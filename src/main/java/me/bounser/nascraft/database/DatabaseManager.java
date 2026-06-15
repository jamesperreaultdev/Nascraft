package me.bounser.nascraft.database;

import me.bounser.nascraft.database.sqlite.SQLite;

public class DatabaseManager {

    private final Database database;

    private static DatabaseManager instance;

    public static DatabaseManager get() { return instance == null ? instance = new DatabaseManager() : instance; }

    /** Like get but never constructs/connects: for shutdown paths. */
    public static DatabaseManager getIfPresent() { return instance; }

    public DatabaseManager() {
        database = SQLite.getInstance();
        database.connect();
    }

    public Database getDatabase() {
        return database;
    }
}
