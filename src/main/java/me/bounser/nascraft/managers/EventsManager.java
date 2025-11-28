package me.bounser.nascraft.managers;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import me.bounser.nascraft.database.DatabaseExecutor;
import me.bounser.nascraft.database.commands.Balances;
import me.bounser.nascraft.database.commands.PlayerStats;
import me.bounser.nascraft.database.commands.UserNames;
import me.bounser.nascraft.portfolio.PortfoliosManager;

public class EventsManager implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        String txKey = "join-" + uuid;
        DatabaseExecutor.getInstance().executeIdempotent(txKey, conn -> {
            UserNames.saveOrUpdateNick(conn, uuid, name);
            PortfoliosManager.getInstance().savePortfolioOfPlayer(player);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        String txKey = "quit-" + uuid;
        DatabaseExecutor.getInstance().executeIdempotent(txKey, conn -> {
            PortfoliosManager.getInstance().savePortfolioOfPlayer(player);
            Balances.updateBalance(conn, uuid);
            PlayerStats.saveOrUpdatePlayerStats(conn, uuid);
        });
    }
}
