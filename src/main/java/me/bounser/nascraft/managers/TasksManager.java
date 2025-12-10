package me.bounser.nascraft.managers;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.advancedgui.LayoutModifier;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.discord.alerts.DiscordAlerts;
import me.bounser.nascraft.discord.DiscordBot;
import me.bounser.nascraft.discord.DiscordLog;
import me.bounser.nascraft.market.Market;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.MarketsManager;
import me.bounser.nascraft.market.unit.stats.Instant;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.portfolio.PortfoliosManager;
import me.leoko.advancedgui.manager.GuiWallManager;
import me.leoko.advancedgui.utils.GuiWallInstance;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Random;

public class TasksManager {

    public static TasksManager instance;

    private final int ticksPerSecond = 20;
    private final Random random = new Random();

    private final Plugin AGUI = Bukkit.getPluginManager().getPlugin("AdvancedGUI");

    public static TasksManager getInstance() { return instance == null ? instance = new TasksManager() : instance; }

    private TasksManager(){

        LocalTime timeNow = LocalTime.now();

        LocalTime nextMinute = timeNow.plusMinutes(1).withSecond(0);
        Duration timeRemaining = Duration.between(timeNow, nextMinute);

        // Registering tasks:
        saveDataTask();
        noiseTask((int) timeRemaining.getSeconds());
        discordTask((int) timeRemaining.getSeconds());
        shortTermPricesTask((int) timeRemaining.getSeconds());
        hourlyTask();
        saveInstants();
        stockRestockTask();

        DatabaseManager.get().getDatabase().purgeHistory();
    }

    private void shortTermPricesTask(int delay) {

        Bukkit.getScheduler().runTaskTimerAsynchronously(Nascraft.getInstance(), () -> {

            float allChanges = 0;
            for (Item item : MarketManager.getInstance().getAllParentItems()) {
                if (Config.getInstance().getPriceNoise())
                    allChanges += item.getPrice().getChange();

                item.lowerOperations();

                item.getPrice().addValueToShortTermStorage();
            }

            MarketManager.getInstance().updateMarketChange1h(allChanges/MarketManager.getInstance().getAllParentItems().size());

            if (AGUI != null &&
                AGUI.isEnabled() &&
                GuiWallManager.getInstance().getActiveInstances() != null)

                for (GuiWallInstance instance : GuiWallManager.getInstance().getActiveInstances()) {

                    if (instance.getLayout().getName().equals("Nascraft"))
                        for (Player player : Bukkit.getOnlinePlayers())
                            if (instance.getInteraction(player) != null)
                                LayoutModifier.getInstance().updateMainPage(instance.getInteraction(player).getComponentTree(), true, player);

                }

        }, (long) delay * ticksPerSecond, 60L * ticksPerSecond);
    }

    private void discordTask(int delay) {

        if (Config.getInstance().getDiscordEnabled()) {

            Bukkit.getScheduler().runTaskTimerAsynchronously(Nascraft.getInstance(), () -> {

                if (Config.getInstance().getDiscordMenuEnabled()) {
                    DiscordBot.getInstance().update();
                    DiscordAlerts.getInstance().updateAlerts();
                }

                if (Config.getInstance().getLogChannelEnabled())
                    DiscordLog.getInstance().flushBuffer();

            }, (long) delay * ticksPerSecond, ((long) Config.getInstance().getUpdateTime() *  ticksPerSecond));
        }
    }

    private void noiseTask(int delay) {

        Bukkit.getScheduler().runTaskTimerAsynchronously(Nascraft.getInstance(), () -> {

            for (Item item : MarketManager.getInstance().getAllParentItems()) {
                if (Config.getInstance().getPriceNoise())
                    item.getPrice().applyNoise();

            }
        }, (long) delay * ticksPerSecond, (long) Config.getInstance().getNoiseTime() *  ticksPerSecond);
    }

    private void saveDataTask() {

        Bukkit.getScheduler().runTaskTimerAsynchronously(Nascraft.getInstance(), () -> {

            DatabaseManager.get().getDatabase().saveEverything();

            DatabaseManager.get().getDatabase().saveCPIValue(MarketManager.getInstance().getConsumerPriceIndex());

            PortfoliosManager.getInstance().savePortfoliosWorthOfOnlinePlayers();

            for (Player player : Bukkit.getOnlinePlayers())
                DatabaseManager.get().getDatabase().updateBalance(player.getUniqueId());

        }, 60L * 5 * ticksPerSecond, 60L * 5 * ticksPerSecond); // 5 min
    }

    private void saveInstants() {

        Bukkit.getScheduler().runTaskTimerAsynchronously(Nascraft.getInstance(), () -> {

            for (Item item : MarketManager.getInstance().getAllParentItems()) {

                item.getItemStats().addInstant(new Instant(
                        LocalDateTime.now(),
                        item.getPrice().getValue(),
                        item.getVolume()
                ));

                item.restartVolume();
            }

        }, 2400, 60L * ticksPerSecond);
    }

    private void hourlyTask() {
        LocalTime timeNow = LocalTime.now();

        LocalTime nextHour = timeNow.plusHours(1).withMinute(0).withSecond(0);
        Duration timeRemaining = Duration.between(timeNow, nextHour);

        Bukkit.getScheduler().runTaskTimerAsynchronously(Nascraft.getInstance(), () -> {

            for (Item item : MarketManager.getInstance().getAllItems()) {
                item.getPrice().restartHourLimits();
            }

            MarketManager.getInstance().setOperationsLastHour(0);

            if (Config.getInstance().getAlertsMenuEnabled()) DatabaseManager.get().getDatabase().purgeAlerts();

        }, timeRemaining.getSeconds()*ticksPerSecond, 60 * 60 * ticksPerSecond); // 1 hour
    }

    private void stockRestockTask() {
        if (!Config.getInstance().getStockRestockEnabled()) return;

        // Schedule restock for the legacy single market
        scheduleMarketRestock(null);

        // Schedule restock for each multi-market independently
        for (Market market : MarketsManager.getInstance().getAllMarkets()) {
            scheduleMarketRestock(market);
        }
    }

    /**
     * Schedules a restock for a specific market with random interval.
     * @param market The market to restock, or null for the legacy single market
     */
    private void scheduleMarketRestock(Market market) {
        int minMinutes;
        int maxMinutes;

        if (market != null) {
            minMinutes = market.getEffectiveRestockMinMinutes();
            maxMinutes = market.getEffectiveRestockMaxMinutes();
        } else {
            minMinutes = Config.getInstance().getStockRestockMinMinutes();
            maxMinutes = Config.getInstance().getStockRestockMaxMinutes();
        }

        // Calculate random interval between min and max
        int intervalMinutes = minMinutes + random.nextInt(Math.max(1, maxMinutes - minMinutes + 1));
        long intervalTicks = (long) intervalMinutes * 60 * ticksPerSecond;

        String marketName = market != null ? market.getDisplayName() : "Legacy Market";

        Bukkit.getScheduler().runTaskLaterAsynchronously(Nascraft.getInstance(), () -> {
            // Perform the restock
            if (market != null) {
                // Multi-market restock
                int totalRestocked = 0;
                for (Item item : market.getAllParentItems()) {
                    int itemRestockAmount = market.getEffectiveRestockAmount(item.getIdentifier());
                    item.addStock(itemRestockAmount);
                    totalRestocked += itemRestockAmount;
                }

                // Broadcast restock complete (must run on main thread)
                final int finalTotal = totalRestocked;
                Bukkit.getScheduler().runTask(Nascraft.getInstance(), () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        Lang.get().message(player, Message.STOCK_RESTOCK_COMPLETE,
                                "[AMOUNT]", String.valueOf(finalTotal),
                                "[MARKET]", market.getDisplayName());
                    }
                });
            } else {
                // Legacy single market restock
                int totalRestocked = 0;
                for (Item item : MarketManager.getInstance().getAllParentItems()) {
                    int itemRestockAmount = Config.getInstance().getItemRestockAmount(item.getIdentifier());
                    item.addStock(itemRestockAmount);
                    totalRestocked += itemRestockAmount;
                }

                // Broadcast restock complete (must run on main thread)
                final int finalTotal = totalRestocked;
                Bukkit.getScheduler().runTask(Nascraft.getInstance(), () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        Lang.get().message(player, Message.STOCK_RESTOCK_COMPLETE,
                                "[AMOUNT]", String.valueOf(finalTotal),
                                "[MARKET]", "Market");
                    }
                });
            }

            // Schedule the next restock with a new random interval
            scheduleMarketRestock(market);

        }, intervalTicks);

        Nascraft.getInstance().getLogger().info("[Restock] " + marketName + " scheduled in " + intervalMinutes + " minutes");
    }
}
