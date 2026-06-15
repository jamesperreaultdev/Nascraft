package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.database.commands.resources.NormalisedDate;
import me.bounser.nascraft.database.commands.resources.Trade;
import me.bounser.nascraft.formatter.RoundUtils;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.Port;
import me.bounser.nascraft.market.unit.Item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TradesLog {

    public static void saveTrade(Connection connection, Trade trade) throws SQLException {

        String sql = "INSERT INTO trade_log (port_id, uuid, day, date, identifier, amount, value, buy, discord) VALUES (?,?,?,?,?,?,?,?,?);";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, trade.getItem().getPort().getId());
            statement.setString(2, trade.getUuid().toString());
            statement.setInt(3, NormalisedDate.getDays());
            statement.setString(4, NormalisedDate.formatDateTime(LocalDateTime.now()));
            statement.setString(5, trade.getItem().getIdentifier());
            statement.setInt(6, trade.getAmount());
            statement.setFloat(7, RoundUtils.round(trade.getValue()));
            statement.setBoolean(8, trade.isBuy());
            statement.setBoolean(9, trade.throughDiscord());
            statement.executeUpdate();
        }
    }

    public static List<Trade> retrieveTrades(Connection connection, UUID uuid, int offset, int limit) throws SQLException {

        List<Trade> trades = new ArrayList<>();

        if (uuid == null) return trades;

        String sql = "SELECT port_id, uuid, date, identifier, amount, value, buy, discord FROM trade_log WHERE uuid = ? ORDER BY id DESC LIMIT ? OFFSET ?;";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, limit);
            statement.setInt(3, offset);

            ResultSet rs = statement.executeQuery();

            while (rs.next()) {
                Trade trade = buildTrade(rs);
                if (trade != null) trades.add(trade);
            }
        }

        return trades;
    }

    public static List<Trade> retrieveLastTrades(Connection connection, int offset, int limit) throws SQLException {

        List<Trade> trades = new ArrayList<>();

        String sql = "SELECT port_id, uuid, date, identifier, amount, value, buy, discord FROM trade_log ORDER BY id DESC LIMIT ? OFFSET ?;";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            statement.setInt(2, offset);

            ResultSet rs = statement.executeQuery();

            while (rs.next()) {
                Trade trade = buildTrade(rs);
                if (trade != null) trades.add(trade);
            }
        }

        return trades;
    }

    /**
     * Rebuilds a trade from a trade_log row. Returns null when the port or
     * the item no longer exists (the config may have changed since the row
     * was written), in which case the row is skipped.
     */
    private static Trade buildTrade(ResultSet rs) throws SQLException {

        Port port = MarketManager.getInstance().getPort(rs.getString("port_id"));
        if (port == null) return null;

        Item item = port.getItem(rs.getString("identifier"));
        if (item == null) return null;

        return new Trade(
                item,
                NormalisedDate.parseDateTime(rs.getString("date")),
                rs.getFloat("value"),
                rs.getInt("amount"),
                rs.getBoolean("buy"),
                rs.getBoolean("discord"),
                UUID.fromString(rs.getString("uuid"))
        );
    }

    public static void purgeHistory(Connection connection) throws SQLException {

        try (PreparedStatement prep = connection.prepareStatement("DELETE FROM prices_day WHERE day < ?;")) {
            prep.setInt(1, NormalisedDate.getDays() - 2);
            prep.executeUpdate();
        }

        int offset = Config.getInstance().getDatabasePurgeDays();
        if (offset == -1) return;

        try (PreparedStatement prep = connection.prepareStatement("DELETE FROM trade_log WHERE day < ?;")) {
            prep.setInt(1, NormalisedDate.getDays() - offset);
            prep.executeUpdate();
        }
    }

}
