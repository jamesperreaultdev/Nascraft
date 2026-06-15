package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.database.commands.resources.NormalisedDate;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.market.unit.stats.Instant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;

/**
 * Price history, scoped per port. Three resolutions:
 * prices_day keeps fine-grained recent points (purged after ~2 days),
 * prices_month keeps ~4-hour aggregates (purged after ~31 days),
 * prices_history keeps one point per day forever.
 */
public class HistorialData {

    private static Item resolveParent(Item item) {
        return item.getParent() != null ? item.getParent() : item;
    }

    public static void saveDayPrice(Connection connection, Item item, Instant instant) throws SQLException {

        item = resolveParent(item);

        String insert = "INSERT INTO prices_day (port_id, day, date, identifier, price, volume) VALUES (?,?,?,?,?,?);";

        try (PreparedStatement insertStatement = connection.prepareStatement(insert)) {
            insertStatement.setString(1, item.getPort().getId());
            insertStatement.setInt(2, NormalisedDate.getDays());
            insertStatement.setString(3, instant.getLocalDateTime().toString());
            insertStatement.setString(4, item.getIdentifier());
            insertStatement.setDouble(5, instant.getPrice());
            insertStatement.setInt(6, instant.getVolume());
            insertStatement.executeUpdate();
        }

        try (PreparedStatement deleteStatement = connection.prepareStatement("DELETE FROM prices_day WHERE day < ?;")) {
            deleteStatement.setInt(1, NormalisedDate.getDays() - 2);
            deleteStatement.executeUpdate();
        }
    }

    public static void saveMonthPrice(Connection connection, Item item, Instant instant) throws SQLException {

        item = resolveParent(item);

        String select = "SELECT date FROM prices_month WHERE port_id=? AND identifier=? ORDER BY id DESC LIMIT 1;";

        try (PreparedStatement preparedStatement = connection.prepareStatement(select)) {

            preparedStatement.setString(1, item.getPort().getId());
            preparedStatement.setString(2, item.getIdentifier());

            ResultSet resultSet = preparedStatement.executeQuery();

            if (!resultSet.next()) {

                insertMonthPrice(connection, item, NormalisedDate.getDays(), instant.getLocalDateTime().toString(), instant.getPrice(), instant.getVolume());

            } else if (LocalDateTime.parse(resultSet.getString("date")).isBefore(LocalDateTime.now().minusHours(4))) {

                String selectDay = "SELECT date, price, volume FROM prices_day WHERE port_id=? AND identifier=? ORDER BY id DESC LIMIT 48;";

                try (PreparedStatement preparedStatementDay = connection.prepareStatement(selectDay)) {

                    preparedStatementDay.setString(1, item.getPort().getId());
                    preparedStatementDay.setString(2, item.getIdentifier());

                    ResultSet resultSetDay = preparedStatementDay.executeQuery();

                    if (!resultSetDay.next()) {

                        insertMonthPrice(connection, item, NormalisedDate.getDays(), instant.getLocalDateTime().toString(), instant.getPrice(), instant.getVolume());

                    } else {

                        double averagePrice = 0;
                        int totalVolume = 0;

                        int i = 0;

                        while (resultSetDay.next()) {
                            if (LocalDateTime.parse(resultSetDay.getString("date")).isAfter(LocalDateTime.now().minusHours(4))) {
                                i++;
                                averagePrice += resultSetDay.getDouble("price");
                                totalVolume += resultSetDay.getInt("volume");
                            }
                        }

                        if (averagePrice == 0) return;

                        insertMonthPrice(connection, item, NormalisedDate.getDays(), LocalDateTime.now().minusHours(2).toString(), averagePrice / i, totalVolume);
                    }
                }

                try (PreparedStatement deleteStatement = connection.prepareStatement("DELETE FROM prices_month WHERE day < ?;")) {
                    deleteStatement.setInt(1, NormalisedDate.getDays() - 31);
                    deleteStatement.executeUpdate();
                }
            }
        }
    }

    private static void insertMonthPrice(Connection connection, Item item, int day, String date, double price, int volume) throws SQLException {

        String insert = "INSERT INTO prices_month (port_id, day, date, identifier, price, volume) VALUES (?,?,?,?,?,?);";

        try (PreparedStatement insertStatement = connection.prepareStatement(insert)) {
            insertStatement.setString(1, item.getPort().getId());
            insertStatement.setInt(2, day);
            insertStatement.setString(3, date);
            insertStatement.setString(4, item.getIdentifier());
            insertStatement.setDouble(5, price);
            insertStatement.setInt(6, volume);
            insertStatement.executeUpdate();
        }
    }

    public static void saveHistoryPrices(Connection connection, Item item, Instant instant) throws SQLException {

        item = resolveParent(item);

        String select = "SELECT date FROM prices_history WHERE port_id=? AND day=? AND identifier=?;";

        try (PreparedStatement preparedStatement = connection.prepareStatement(select)) {

            preparedStatement.setString(1, item.getPort().getId());
            preparedStatement.setInt(2, NormalisedDate.getDays());
            preparedStatement.setString(3, item.getIdentifier());

            ResultSet resultSet = preparedStatement.executeQuery();

            if (!resultSet.next()) {

                String selectMonth = "SELECT date, price, volume FROM prices_month WHERE port_id=? AND identifier=? ORDER BY id DESC LIMIT 6;";

                try (PreparedStatement preparedStatementMonth = connection.prepareStatement(selectMonth)) {

                    preparedStatementMonth.setString(1, item.getPort().getId());
                    preparedStatementMonth.setString(2, item.getIdentifier());

                    ResultSet resultSetMonth = preparedStatementMonth.executeQuery();

                    if (!resultSetMonth.next()) {

                        insertHistoryPrice(connection, item, NormalisedDate.getDays(), instant.getLocalDateTime().toString(), instant.getPrice(), instant.getVolume());

                    } else {

                        double averagePrice = 0;
                        int totalVolume = 0;

                        int i = 0;

                        while (resultSetMonth.next()) {
                            if (LocalDateTime.parse(resultSetMonth.getString("date")).isAfter(LocalDateTime.now().minusHours(24))) {
                                i++;
                                averagePrice += resultSetMonth.getDouble("price");
                                totalVolume += resultSetMonth.getInt("volume");
                            }
                        }

                        if (i == 0) {
                            insertHistoryPrice(connection, item, NormalisedDate.getDays(), instant.getLocalDateTime().toString(), instant.getPrice(), instant.getVolume());
                        } else {
                            insertHistoryPrice(connection, item, NormalisedDate.getDays(), LocalDateTime.now().minusHours(12).toString(), averagePrice / i, totalVolume);
                        }
                    }
                }
            }
        }
    }

    private static void insertHistoryPrice(Connection connection, Item item, int day, String date, double price, int volume) throws SQLException {

        String insert = "INSERT INTO prices_history (port_id, day, date, identifier, price, volume) VALUES (?,?,?,?,?,?);";

        try (PreparedStatement insertStatement = connection.prepareStatement(insert)) {
            insertStatement.setString(1, item.getPort().getId());
            insertStatement.setInt(2, day);
            insertStatement.setString(3, date);
            insertStatement.setString(4, item.getIdentifier());
            insertStatement.setDouble(5, price);
            insertStatement.setInt(6, volume);
            insertStatement.executeUpdate();
        }
    }

    public static List<Instant> getDayPrices(Connection connection, Item item) throws SQLException {

        item = resolveParent(item);

        List<Instant> prices = new LinkedList<>();

        String select = "SELECT date FROM prices_day WHERE port_id=? AND identifier=? ORDER BY id DESC LIMIT 1;";

        try (PreparedStatement preparedStatement = connection.prepareStatement(select)) {

            preparedStatement.setString(1, item.getPort().getId());
            preparedStatement.setString(2, item.getIdentifier());

            ResultSet resultSet = preparedStatement.executeQuery();

            if (!resultSet.next()) {

                prices.add(new Instant(LocalDateTime.now().minusHours(24), 0, 0));
                prices.add(new Instant(LocalDateTime.now().minusMinutes(5), 0, 0));

                prices.add(new Instant(LocalDateTime.now(), item.getPrice().getValue(), item.getVolume()));

            } else {

                String selectAll = "SELECT date, price, volume FROM prices_day WHERE port_id=? AND identifier=? ORDER BY id DESC LIMIT 288;";

                try (PreparedStatement preparedStatementAll = connection.prepareStatement(selectAll)) {

                    preparedStatementAll.setString(1, item.getPort().getId());
                    preparedStatementAll.setString(2, item.getIdentifier());

                    ResultSet resultSetAll = preparedStatementAll.executeQuery();

                    while (resultSetAll.next()) {

                        LocalDateTime time = LocalDateTime.parse(resultSetAll.getString("date"));

                        double price = resultSetAll.getDouble("price");

                        if (time.isAfter(LocalDateTime.now().minusHours(24)) && price != 0) {
                            prices.add(new Instant(
                                    time,
                                    price,
                                    resultSetAll.getInt("volume")
                            ));
                        }
                    }
                }

                prices.add(0, new Instant(LocalDateTime.now().minusHours(24), 0, 0));

                prices.add(new Instant(LocalDateTime.now(), item.getPrice().getValue(), item.getVolume()));
            }
        }

        return prices;
    }

    public static List<Instant> getMonthPrices(Connection connection, Item item) throws SQLException {

        item = resolveParent(item);

        List<Instant> prices = new LinkedList<>();

        String select = "SELECT date FROM prices_month WHERE port_id=? AND identifier=? ORDER BY id DESC LIMIT 1;";

        try (PreparedStatement preparedStatement = connection.prepareStatement(select)) {

            preparedStatement.setString(1, item.getPort().getId());
            preparedStatement.setString(2, item.getIdentifier());

            ResultSet resultSet = preparedStatement.executeQuery();

            if (!resultSet.next()) {

                prices.add(new Instant(LocalDateTime.now().minusDays(30), 0, 0));
                prices.add(new Instant(LocalDateTime.now().minusMinutes(5), 0, 0));

                prices.add(new Instant(LocalDateTime.now(), item.getPrice().getValue(), item.getVolume()));

            } else {

                String selectAll = "SELECT date, price, volume FROM prices_month WHERE port_id=? AND identifier=? ORDER BY id DESC LIMIT 400;";

                try (PreparedStatement preparedStatementAll = connection.prepareStatement(selectAll)) {

                    preparedStatementAll.setString(1, item.getPort().getId());
                    preparedStatementAll.setString(2, item.getIdentifier());

                    ResultSet resultSetAll = preparedStatementAll.executeQuery();

                    while (resultSetAll.next()) {

                        LocalDateTime time = LocalDateTime.parse(resultSetAll.getString("date"));

                        if (time.isAfter(LocalDateTime.now().minusDays(30))) {
                            prices.add(new Instant(
                                    time,
                                    resultSetAll.getDouble("price"),
                                    resultSetAll.getInt("volume")
                            ));
                        }
                    }
                }

                prices.add(0, new Instant(LocalDateTime.now().minusDays(30), 0, 0));

                prices.add(new Instant(LocalDateTime.now(), item.getPrice().getValue(), item.getVolume()));
            }
        }

        return prices;
    }

    public static List<Instant> getYearPrices(Connection connection, Item item) throws SQLException {

        item = resolveParent(item);

        List<Instant> prices = new LinkedList<>();

        String select = "SELECT day FROM prices_history WHERE port_id=? AND identifier=? ORDER BY day DESC LIMIT 1;";

        try (PreparedStatement preparedStatement = connection.prepareStatement(select)) {

            preparedStatement.setString(1, item.getPort().getId());
            preparedStatement.setString(2, item.getIdentifier());

            ResultSet resultSet = preparedStatement.executeQuery();

            if (!resultSet.next()) {

                prices.add(new Instant(LocalDateTime.now().minusDays(365), 0, 0));
                prices.add(new Instant(LocalDateTime.now().minusMinutes(5), 0, 0));

                prices.add(new Instant(LocalDateTime.now(), item.getPrice().getValue(), item.getVolume()));

            } else {

                String selectAll = "SELECT day, price, volume FROM prices_history WHERE port_id=? AND identifier=? ORDER BY day DESC LIMIT 385;";

                try (PreparedStatement preparedStatementAll = connection.prepareStatement(selectAll)) {

                    preparedStatementAll.setString(1, item.getPort().getId());
                    preparedStatementAll.setString(2, item.getIdentifier());

                    ResultSet resultSetAll = preparedStatementAll.executeQuery();

                    while (resultSetAll.next()) {

                        LocalDateTime time = LocalDateTime.of(2023, 1, 1, 1, 1).plusDays(resultSetAll.getInt("day"));

                        if (time.isAfter(LocalDateTime.now().minusDays(365))) {
                            prices.add(new Instant(
                                    time,
                                    resultSetAll.getDouble("price"),
                                    resultSetAll.getInt("volume")
                            ));
                        }
                    }
                }

                prices.add(new Instant(LocalDateTime.now().minusDays(365), 0, 0));
                prices.add(new Instant(LocalDateTime.now(), item.getPrice().getValue(), item.getVolume()));
            }
        }

        return prices;
    }

    public static List<Instant> getAllPrices(Connection connection, Item item) throws SQLException {

        item = resolveParent(item);

        List<Instant> prices = new LinkedList<>();

        String select = "SELECT day FROM prices_history WHERE port_id=? AND identifier=? ORDER BY day DESC LIMIT 1;";

        try (PreparedStatement preparedStatement = connection.prepareStatement(select)) {

            preparedStatement.setString(1, item.getPort().getId());
            preparedStatement.setString(2, item.getIdentifier());

            ResultSet resultSet = preparedStatement.executeQuery();

            if (!resultSet.next()) {

                prices.add(new Instant(LocalDateTime.now().minusDays(30), 0, 0));
                prices.add(new Instant(LocalDateTime.now().minusMinutes(5), 0, 0));

                prices.add(new Instant(LocalDateTime.now(), item.getPrice().getValue(), item.getVolume()));

            } else {

                String selectAll = "SELECT day, price, volume FROM prices_history WHERE port_id=? AND identifier=? ORDER BY day DESC;";

                try (PreparedStatement preparedStatementAll = connection.prepareStatement(selectAll)) {

                    preparedStatementAll.setString(1, item.getPort().getId());
                    preparedStatementAll.setString(2, item.getIdentifier());

                    ResultSet resultSetAll = preparedStatementAll.executeQuery();

                    while (resultSetAll.next()) {

                        LocalDateTime time = LocalDateTime.of(2023, 1, 1, 1, 1);

                        prices.add(new Instant(
                                time.plusDays(resultSetAll.getInt("day")),
                                resultSetAll.getDouble("price"),
                                resultSetAll.getInt("volume")
                        ));
                    }
                }

                prices.add(new Instant(LocalDateTime.now(), item.getPrice().getValue(), item.getVolume()));
            }
        }

        return prices;
    }

}
