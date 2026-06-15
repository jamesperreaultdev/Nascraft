package me.bounser.nascraft.managers;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.discord.DiscordLog;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.Port;
import me.bounser.nascraft.market.unit.stats.Instant;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.config.Config;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TasksManager {

    public static TasksManager instance;

    private final int ticksPerSecond = 20;

    private final java.util.Map<String, BukkitTask> restockTasks = new java.util.HashMap<>();

    private final Random random = new Random();

    public static TasksManager getInstance() { return instance == null ? instance = new TasksManager() : instance; }

    private TasksManager(){

        LocalTime timeNow = LocalTime.now();

        LocalTime nextMinute = timeNow.plusMinutes(1).withSecond(0);
        Duration timeRemaining = Duration.between(timeNow, nextMinute);

        saveDataTask();
        noiseTask((int) timeRemaining.getSeconds());
        discordTask((int) timeRemaining.getSeconds());
        shortTermPricesTask((int) timeRemaining.getSeconds());
        hourlyTask();
        saveInstants();
        scheduleRestockTasks();

        DatabaseManager.get().getDatabase().purgeHistory();
    }

    private List<Item> allParentItems() {
        List<Item> items = new ArrayList<>();
        for (Port port : MarketManager.getInstance().getPorts())
            items.addAll(port.getParentItems());
        return items;
    }

    private void shortTermPricesTask(int delay) {

        Bukkit.getScheduler().runTaskTimer(Nascraft.getInstance(), () -> {

            for (Item item : allParentItems()) {
                item.lowerOperations();
                item.getPrice().addValueToShortTermStorage();
            }

        }, (long) delay * ticksPerSecond, 60L * ticksPerSecond);
    }

    private void discordTask(int delay) {

        if (Config.getInstance().getDiscordEnabled()) {

            Bukkit.getScheduler().runTaskTimerAsynchronously(Nascraft.getInstance(), () -> {

                if (Config.getInstance().getLogChannelEnabled())
                    DiscordLog.getInstance().flushBuffer();

            }, (long) delay * ticksPerSecond, 60L * ticksPerSecond);
        }
    }

    private void noiseTask(int delay) {

        Bukkit.getScheduler().runTaskTimer(Nascraft.getInstance(), () -> {

            if (!Config.getInstance().getPriceNoise()) return;

            for (Item item : allParentItems())
                item.getPrice().applyNoise();

        }, (long) delay * ticksPerSecond, (long) Config.getInstance().getNoiseTime() * ticksPerSecond);
    }

    private void saveDataTask() {

        Bukkit.getScheduler().runTaskTimerAsynchronously(Nascraft.getInstance(), () ->
                DatabaseManager.get().getDatabase().saveEverything(),
                60L * 5 * ticksPerSecond, 60L * 5 * ticksPerSecond); // 5 min
    }

    private void saveInstants() {

        Bukkit.getScheduler().runTaskTimer(Nascraft.getInstance(), () -> {

            for (Item item : allParentItems()) {

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

        Bukkit.getScheduler().runTaskTimer(Nascraft.getInstance(), () -> {

            for (Port port : MarketManager.getInstance().getPorts())
                for (Item item : port.getAllItems())
                    item.getPrice().restartHourLimits();

            MarketManager.getInstance().setOperationsLastHour(0);

        }, timeRemaining.getSeconds()*ticksPerSecond, 60 * 60 * ticksPerSecond); // 1 hour
    }

    /**
     * Each port restocks on its own randomized schedule, picked uniformly
     * from [min-minutes, max-minutes] after each restock.
     */
    public void scheduleRestockTasks() {
        for (Port port : MarketManager.getInstance().getPorts())
            scheduleNextRestock(port);
    }

    public void cancelRestockTasks() {
        for (BukkitTask task : restockTasks.values())
            if (task != null && !task.isCancelled()) task.cancel();
        restockTasks.clear();
    }

    private void scheduleNextRestock(Port port) {

        int min = Math.max(1, port.getRestockMinMinutes());
        int max = Math.max(min, port.getRestockMaxMinutes());

        int minutes = min + random.nextInt(max - min + 1);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(Nascraft.getInstance(), () -> {

            // Re-resolve: the port may have been replaced by a reload.
            Port currentPort = MarketManager.getInstance().getPort(port.getId());
            if (currentPort == null) return;

            currentPort.restock();

            if (Config.getInstance().getRestockAnnounceEnabled())
                announceRestock(currentPort);

            scheduleNextRestock(currentPort);

        }, (long) minutes * 60 * ticksPerSecond);

        restockTasks.put(port.getId(), task);
    }

    private void announceRestock(Port port) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (Config.getInstance().getRestockAnnounceGlobal() || port.isInside(player.getLocation()))
                Lang.get().message(player, Message.PORT_RESTOCKED, "[PORT]", port.getDisplayName());
        }
    }
}
