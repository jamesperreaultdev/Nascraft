package me.bounser.nascraft.discord;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.database.commands.resources.Trade;
import me.bounser.nascraft.discord.linking.LinkManager;
import me.bounser.nascraft.formatter.Formatter;
import me.bounser.nascraft.formatter.Style;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Buffered trade log: trade lines are accumulated and flushed to the
 * configured Discord channel by TasksManager once a minute (or earlier
 * if the buffer grows large). Safe to call before JDA is ready.
 */
public class DiscordLog {

    private static DiscordLog instance;

    public static DiscordLog getInstance() {
        if (instance == null) {
            synchronized (DiscordLog.class) {
                if (instance == null) {
                    instance = new DiscordLog();
                }
            }
        }
        return instance;
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final AtomicReference<StringBuffer> buffer = new AtomicReference<>(new StringBuffer());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private DiscordLog() { }

    public void sendTradeLog(Trade trade) {

        executorService.submit(() -> {
            try {
                String message = formatTrade(trade);

                StringBuffer currentBuffer = buffer.get();
                synchronized (currentBuffer) {
                    currentBuffer.append("\n").append(message);
                    if (currentBuffer.length() > 1500) {
                        flushBuffer();
                    }
                }
            } catch (Exception e) {
                Nascraft.getInstance().getLogger().warning("Error in sendTradeLog: " + e.getMessage());
            }
        });
    }

    private String formatTrade(Trade trade) {

        String action = trade.isBuy() ? "bought" : "sold";

        String nickname = null;

        if (trade.getUuid() != null) {
            Player player = Bukkit.getPlayer(trade.getUuid());
            nickname = (player != null) ? player.getName() : DatabaseManager.get().getDatabase().getNameByUUID(trade.getUuid());
            if (nickname == null) nickname = trade.getUuid().toString();
        }
        if (nickname == null) nickname = "Unknown";

        String line = "`" + trade.getDate().format(TIME_FORMAT) + "` **" + nickname + "** " + action + " "
                + trade.getAmount() + " x " + trade.getItem().getName()
                + " at **" + trade.getItem().getPort().getPlainDisplayName() + "**"
                + " for " + Formatter.plainFormat(trade.getItem().getCurrency(), trade.getValue(), Style.ROUND_BASIC);

        if (trade.getUuid() != null) {
            String userId = LinkManager.getInstance().getUserDiscordID(trade.getUuid());
            if (userId != null) line += " (discord: `" + userId + "`)";
        }

        return line;
    }

    public void flushBuffer() {

        StringBuffer currentBuffer = buffer.getAndSet(new StringBuffer());

        if (currentBuffer.length() == 0) return;

        executorService.submit(() -> {
            try {
                DiscordBot bot = DiscordBot.getInstance();

                if (bot == null || bot.getJDA() == null || !bot.getJDA().getStatus().isInit()) {
                    Nascraft.getInstance().getLogger().warning("JDA not ready. Trade log skipped.");
                    return;
                }

                JDA jda = bot.getJDA();

                jda.getGuilds().forEach(guild -> {
                    TextChannel textChannel = guild.getTextChannelById(Config.getInstance().getLogChannel());
                    if (textChannel != null) {
                        textChannel.sendMessage(currentBuffer.toString()).queue();
                    } else {
                        Nascraft.getInstance().getLogger().warning("Log channel not found for guild: " + guild.getName());
                    }
                });
            } catch (Exception e) {
                Nascraft.getInstance().getLogger().severe("Error flushing trade log buffer: " + e.getMessage());
            }
        });
    }
}
