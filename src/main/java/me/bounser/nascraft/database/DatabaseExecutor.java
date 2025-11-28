package me.bounser.nascraft.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import me.bounser.nascraft.Nascraft;

public class DatabaseExecutor {

    private static DatabaseExecutor instance;

    private final ExecutorService executor;
    private final HikariDataSource dataSource;
    private final ConcurrentHashMap<String, Long> transactionIds;
    private final AtomicLong idCounter;

    private static final int MAX_RETRIES = 3;
    private static final int BASE_DELAY_MS = 50;

    public static DatabaseExecutor getInstance() {
        if (instance == null) {
            instance = new DatabaseExecutor();
        }
        return instance;
    }

    private DatabaseExecutor() {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Nascraft-DB");
            t.setDaemon(true);
            return t;
        });
        this.transactionIds = new ConcurrentHashMap<>();
        this.idCounter = new AtomicLong(0);

        String path = Nascraft.getInstance().getDataFolder().getPath() + "/data/sqlite.db";

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + path);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "30000");

        this.dataSource = new HikariDataSource(config);
    }

    public void execute(Runnable task) {
        executor.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                Nascraft.getInstance().getLogger().warning("DB task failed: " + e.getMessage());
            }
        });
    }

    public void executeWithRetry(Consumer<Connection> task) {
        executor.submit(() -> runWithRetry(task));
    }

    public void executeIdempotent(String transactionKey, Consumer<Connection> task) {
        long currentId = idCounter.incrementAndGet();

        Long existingId = transactionIds.putIfAbsent(transactionKey, currentId);
        if (existingId != null) {
            return;
        }

        executor.submit(() -> {
            try {
                runWithRetry(task);
            } finally {
                transactionIds.remove(transactionKey);
            }
        });
    }

    private void runWithRetry(Consumer<Connection> task) {
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            try (Connection conn = dataSource.getConnection()) {
                task.accept(conn);
                return;
            } catch (SQLException e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("SQLITE_BUSY") || msg.contains("database is locked"))) {
                    attempt++;
                    if (attempt < MAX_RETRIES) {
                        int delay = BASE_DELAY_MS * (1 << attempt);
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                } else {
                    Nascraft.getInstance().getLogger().warning("DB error: " + msg);
                    return;
                }
            }
        }
        Nascraft.getInstance().getLogger().warning("DB operation failed after " + MAX_RETRIES + " retries");
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
