package me.bounser.nascraft.database.commands;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.database.commands.resources.NormalisedDate;
import me.bounser.nascraft.managers.DebtManager;
import me.bounser.nascraft.portfolio.PortfoliosManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PlayerStats {

    public static void saveOrUpdatePlayerStats(Connection connection, UUID uuid) {
        try {
            int day = NormalisedDate.getDays();

            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            double balance = Nascraft.getEconomy().getBalance(player);
            double portfolio = PortfoliosManager.getInstance().getPortfolio(uuid).getValueOfDefaultCurrency();
            double debt = DebtManager.getInstance().getDebtOfPlayer(uuid);

            String sql1 = "SELECT day FROM player_stats WHERE day=? AND uuid=?;";
            PreparedStatement prep1 = connection.prepareStatement(sql1);
            prep1.setInt(1, day);
            prep1.setString(2, uuid.toString());
            ResultSet resultSet = prep1.executeQuery();

            if (resultSet.next()) {
                String sql2 = "UPDATE player_stats SET balance=?, portfolio=?, debt=? WHERE day=? AND uuid=?;";
                PreparedStatement prep2 = connection.prepareStatement(sql2);
                prep2.setDouble(1, balance);
                prep2.setDouble(2, portfolio);
                prep2.setDouble(3, debt);
                prep2.setInt(4, day);
                prep2.setString(5, uuid.toString());
                prep2.executeUpdate();
            } else {
                String sql2 = "INSERT INTO player_stats (day, uuid, balance, portfolio, debt) VALUES (?, ?, ?, ?, ?);";
                PreparedStatement prep2 = connection.prepareStatement(sql2);
                prep2.setInt(1, day);
                prep2.setString(2, uuid.toString());
                prep2.setDouble(3, balance);
                prep2.setDouble(4, portfolio);
                prep2.setDouble(5, debt);
                prep2.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
