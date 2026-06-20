package me.bounser.nascraft.managers;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.database.commands.resources.Trade;
import me.bounser.nascraft.market.Port;
import me.bounser.nascraft.market.unit.Item;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Plain-text logging of every buy and sell, to the server console and/or a
 * {@code trades.log} file in the plugin folder. This is independent of the
 * database trade log (viewable with /nascraft log) and the optional Discord
 * log: it exists so server operators can see trade activity in their normal
 * logs without either of those.
 *
 * Toggled by the {@code trade-log} section of config.yml; flags are read per
 * call so {@code /nascraft reload} takes effect immediately.
 */
public final class TradeLogger {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static TradeLogger instance;

    private final Object fileLock = new Object();
    private boolean fileErrorReported = false;

    public static TradeLogger getInstance() { return instance == null ? instance = new TradeLogger() : instance; }

    private TradeLogger() { }

    public void log(Trade trade) {

        Config config = Config.getInstance();

        boolean console = config.getTradeLogConsole();
        boolean file = config.getTradeLogFile();

        if (!console && !file) return;

        String line = format(trade);

        if (console) Nascraft.getInstance().getLogger().info(line);

        if (file) appendToFile(line);
    }

    private String format(Trade trade) {

        Item item = trade.getItem();
        Port port = item.getPort();

        String portLabel = port == null ? "unknown" : port.getPlainDisplayName() + " (" + port.getId() + ")";

        return (trade.isBuy() ? "[BUY] " : "[SELL] ")
                + resolveName(trade.getUuid())
                + (trade.isBuy() ? " bought " : " sold ")
                + trade.getAmount() + "x " + item.getName()
                + " for " + trade.getValue() + " " + item.getCurrency().getCurrencyIdentifier()
                + " at " + portLabel;
    }

    private String resolveName(UUID uuid) {
        if (uuid == null) return "unknown";
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        String name = offlinePlayer.getName();
        return name != null ? name : uuid.toString();
    }

    private void appendToFile(String line) {

        File logFile = new File(Nascraft.getInstance().getDataFolder(), "trades.log");

        synchronized (fileLock) {
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(TIMESTAMP.format(java.time.LocalDateTime.now()) + " " + line + System.lineSeparator());
                fileErrorReported = false;
            } catch (IOException e) {
                // Report once, then stay quiet so a bad path can't spam the console on every trade.
                if (!fileErrorReported) {
                    Nascraft.getInstance().getLogger().warning("Could not write to trades.log: " + e.getMessage());
                    fileErrorReported = true;
                }
            }
        }
    }
}
