package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.unit.Item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ItemProperties {

    public static void saveItem(Connection connection, Item item) {

        try {
            String sql = "SELECT stock FROM items WHERE identifier=?;";

            PreparedStatement prep = connection.prepareStatement(sql);
            prep.setString(1, item.getIdentifier());
            ResultSet rs = prep.executeQuery();

            if (rs.next()) {
                String sqlReplace = "REPLACE INTO items (lastprice, lowest, highest, stock, taxes, identifier) VALUES (?, ?, ?, ?, ?, ?);";
                PreparedStatement prepReplace = connection.prepareStatement(sqlReplace);

                prepReplace.setDouble(1, item.getPrice().getValue());
                prepReplace.setDouble(2, item.getPrice().getHistoricalLow());
                prepReplace.setDouble(3, item.getPrice().getHistoricalHigh());
                prepReplace.setDouble(4, item.getPrice().getStock());
                prepReplace.setDouble(5, item.getCollectedTaxes());

                prepReplace.setString(6, item.getIdentifier());

                prepReplace.executeUpdate();
            } else {
                String sqlInsert = "INSERT INTO items (lastprice, lowest, highest, stock, taxes, identifier) VALUES (?, ?, ?, ?, ?, ?);";
                PreparedStatement prepInsert = connection.prepareStatement(sqlInsert);

                prepInsert.setDouble(1, item.getPrice().getValue());
                prepInsert.setDouble(2, item.getPrice().getHistoricalLow());
                prepInsert.setDouble(3, item.getPrice().getHistoricalHigh());
                prepInsert.setDouble(4, item.getPrice().getStock());
                prepInsert.setDouble(5, item.getCollectedTaxes());

                prepInsert.setString(6, item.getIdentifier());

                prepInsert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void retrieveItem(Connection connection, Item item) {

        try {
            String sql = "SELECT lowest, highest, stock, taxes FROM items WHERE identifier=?;";

            PreparedStatement prep = connection.prepareStatement(sql);
            prep.setString(1, item.getIdentifier());
            ResultSet rs = prep.executeQuery();

            if (rs.next()) {
                item.getPrice().setStock(rs.getInt("stock"));
                item.getPrice().setHistoricalHigh(rs.getFloat("highest"));
                item.getPrice().setHistoricalLow(rs.getFloat("lowest"));
                item.setCollectedTaxes(rs.getFloat("taxes"));
            } else {
                String sqlinsert = "INSERT INTO items (identifier, lastprice, lowest, highest, stock, taxes) VALUES (?,?,?,?,?,?);";

                PreparedStatement insertPrep = connection.prepareStatement(sqlinsert);
                insertPrep.setString(1, item.getIdentifier());
                insertPrep.setFloat(2, Config.getInstance().getInitialPrice(item.getIdentifier()));
                insertPrep.setFloat(3, Config.getInstance().getInitialPrice(item.getIdentifier()));
                insertPrep.setFloat(4, Config.getInstance().getInitialPrice(item.getIdentifier()));
                insertPrep.setFloat(5, 0);
                insertPrep.setFloat(6, 0);

                item.getPrice().setStock(0);
                item.getPrice().setHistoricalHigh(Config.getInstance().getInitialPrice(item.getIdentifier()));
                item.getPrice().setHistoricalLow(Config.getInstance().getInitialPrice(item.getIdentifier()));
                item.setCollectedTaxes(0);

                insertPrep.executeUpdate();
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static float retrieveLastPrice(Connection connection, Item item) {

        try {
            String selectSQL = "SELECT lastprice FROM items WHERE identifier = ?;";
            PreparedStatement preparedStatement = connection.prepareStatement(selectSQL);

            preparedStatement.setString(1, item.getIdentifier());

            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                return resultSet.getFloat("lastprice");
            } else {
                return Config.getInstance().getInitialPrice(item.getIdentifier());
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void retrieveItems(Connection connection) {

        try {
            String selectSQL = "SELECT stock, identifier FROM items;";

            PreparedStatement preparedStatement = connection.prepareStatement(selectSQL);

            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {

                String identifier = resultSet.getString("identifier");

                Item item = MarketManager.getInstance().getItem(identifier);

                if (item == null) continue;

                if (item.isParent()) {
                    item.getPrice().setStock(resultSet.getFloat("stock"));
                }

            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // Multi-market support methods

    public static void saveItem(Connection connection, Item item, String marketId) {

        try {
            String sql = "SELECT stock FROM market_items WHERE market_id=? AND identifier=?;";

            PreparedStatement prep = connection.prepareStatement(sql);
            prep.setString(1, marketId);
            prep.setString(2, item.getIdentifier());
            ResultSet rs = prep.executeQuery();

            if (rs.next()) {
                String sqlReplace = "REPLACE INTO market_items (market_id, identifier, lastprice, lowest, highest, stock, item_stock, taxes) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
                PreparedStatement prepReplace = connection.prepareStatement(sqlReplace);

                prepReplace.setString(1, marketId);
                prepReplace.setString(2, item.getIdentifier());
                prepReplace.setDouble(3, item.getPrice().getValue());
                prepReplace.setDouble(4, item.getPrice().getHistoricalLow());
                prepReplace.setDouble(5, item.getPrice().getHistoricalHigh());
                prepReplace.setDouble(6, item.getPrice().getStock());
                prepReplace.setInt(7, item.getStock());
                prepReplace.setDouble(8, item.getCollectedTaxes());

                prepReplace.executeUpdate();
            } else {
                String sqlInsert = "INSERT INTO market_items (market_id, identifier, lastprice, lowest, highest, stock, item_stock, taxes) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
                PreparedStatement prepInsert = connection.prepareStatement(sqlInsert);

                prepInsert.setString(1, marketId);
                prepInsert.setString(2, item.getIdentifier());
                prepInsert.setDouble(3, item.getPrice().getValue());
                prepInsert.setDouble(4, item.getPrice().getHistoricalLow());
                prepInsert.setDouble(5, item.getPrice().getHistoricalHigh());
                prepInsert.setDouble(6, item.getPrice().getStock());
                prepInsert.setInt(7, item.getStock());
                prepInsert.setDouble(8, item.getCollectedTaxes());

                prepInsert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void retrieveItem(Connection connection, Item item, String marketId) {

        try {
            String sql = "SELECT lowest, highest, stock, item_stock, taxes FROM market_items WHERE market_id=? AND identifier=?;";

            PreparedStatement prep = connection.prepareStatement(sql);
            prep.setString(1, marketId);
            prep.setString(2, item.getIdentifier());
            ResultSet rs = prep.executeQuery();

            if (rs.next()) {
                item.getPrice().setStock(rs.getInt("stock"));
                item.getPrice().setHistoricalHigh(rs.getFloat("highest"));
                item.getPrice().setHistoricalLow(rs.getFloat("lowest"));
                item.setCollectedTaxes(rs.getFloat("taxes"));
                // Retrieve item stock (inventory stock for the stock system)
                // Always set from database, even if 0 (stock was depleted)
                item.setStock(rs.getInt("item_stock"));
            } else {
                // New item for this market - use the item's current values (which may have market overrides)
                String sqlinsert = "INSERT INTO market_items (market_id, identifier, lastprice, lowest, highest, stock, item_stock, taxes) VALUES (?,?,?,?,?,?,?,?);";

                float initialPrice = (float) item.getPrice().getInitialValue();

                PreparedStatement insertPrep = connection.prepareStatement(sqlinsert);
                insertPrep.setString(1, marketId);
                insertPrep.setString(2, item.getIdentifier());
                insertPrep.setFloat(3, initialPrice);
                insertPrep.setFloat(4, initialPrice);
                insertPrep.setFloat(5, initialPrice);
                insertPrep.setFloat(6, 0);
                insertPrep.setInt(7, item.getStock()); // Use item's current stock (may have market override)
                insertPrep.setFloat(8, 0);

                item.getPrice().setStock(0);
                item.getPrice().setHistoricalHigh(initialPrice);
                item.getPrice().setHistoricalLow(initialPrice);
                item.setCollectedTaxes(0);

                insertPrep.executeUpdate();
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static float retrieveLastPrice(Connection connection, Item item, String marketId) {

        try {
            String selectSQL = "SELECT lastprice FROM market_items WHERE market_id=? AND identifier = ?;";
            PreparedStatement preparedStatement = connection.prepareStatement(selectSQL);

            preparedStatement.setString(1, marketId);
            preparedStatement.setString(2, item.getIdentifier());

            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                return resultSet.getFloat("lastprice");
            } else {
                return Config.getInstance().getInitialPrice(item.getIdentifier());
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
