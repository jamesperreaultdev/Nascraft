package me.bounser.nascraft.discord;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.discord.linking.LinkManager;
import me.bounser.nascraft.discord.linking.LinkingMethod;
import me.bounser.nascraft.formatter.Formatter;
import me.bounser.nascraft.formatter.Style;
import me.bounser.nascraft.managers.MoneyManager;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.Port;
import me.bounser.nascraft.market.unit.Item;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Reduced Discord integration: bot startup, account linking,
 * informational port/market lookups and (through DiscordLog) trade logging.
 *
 * Remote trading from Discord was removed on purpose: markets are
 * location-locked to their port.
 */
public class DiscordBot extends ListenerAdapter {

    private static DiscordBot instance;

    private final JDA jda;

    public DiscordBot() {

        jda = JDABuilder.createLight(Config.getInstance().getToken(), Collections.emptyList())
                .addEventListeners(this)
                .setActivity(Activity.watching("the port markets"))
                .setStatus(OnlineStatus.ONLINE)
                .build();

        // Publish the instance only after JDA was built: a bad token must
        // not leave a half-constructed singleton behind.
        instance = this;

        List<CommandData> commandList = new ArrayList<>();

        commandList.add(Commands.slash("link", "Link your Minecraft account to your Discord account."));
        commandList.add(Commands.slash("unlink", "Unlink your Minecraft account."));
        commandList.add(Commands.slash("balance", "Check the balance of your linked Minecraft account."));
        commandList.add(Commands.slash("ports", "List all trading ports."));
        commandList.add(Commands.slash("port", "Show the local market of a port.")
                .addOption(OptionType.STRING, "id", "Id of the port.", true, true));

        jda.updateCommands().addCommands(commandList).queue();
    }

    public static DiscordBot getInstance() { return instance; }

    public JDA getJDA() { return jda; }

    // -------------------------------------------------------------------
    // Slash commands
    // -------------------------------------------------------------------

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {

        switch (event.getName()) {

            case "link" -> handleLink(event);

            case "unlink" -> handleUnlink(event);

            case "balance" -> handleBalance(event);

            case "ports" -> handlePorts(event);

            case "port" -> handlePort(event);
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {

        if (!event.getName().equals("port") || !event.getFocusedOption().getName().equals("id")) return;

        String typed = event.getFocusedOption().getValue().toLowerCase();

        List<Command.Choice> choices = new ArrayList<>();

        for (Port port : MarketManager.getInstance().getPorts()) {
            if (choices.size() >= 25) break;
            if (port.getId().toLowerCase().contains(typed) || port.getPlainDisplayName().toLowerCase().contains(typed))
                choices.add(new Command.Choice(port.getPlainDisplayName() + " (" + port.getId() + ")", port.getId()));
        }

        event.replyChoices(choices).queue();
    }

    private void handleLink(SlashCommandInteractionEvent event) {

        if (LinkManager.getInstance().getLinkingMethod() == LinkingMethod.DISCORDSRV) {
            event.reply("Account linking is handled by **DiscordSRV** on this server. Use DiscordSRV's own linking process.").setEphemeral(true).queue();
            return;
        }

        if (LinkManager.getInstance().getUUID(event.getUser().getId()) != null) {
            event.reply("Your Discord account is already linked to a Minecraft account. Use `/unlink` first if you want to link a different one.").setEphemeral(true).queue();
            return;
        }

        int code = LinkManager.getInstance().startLinkingProcess(event.getUser().getId());

        event.reply("To link your Minecraft account, run this command **in-game**:\n`/link " + code + "`").setEphemeral(true).queue();
    }

    private void handleUnlink(SlashCommandInteractionEvent event) {

        if (LinkManager.getInstance().getUUID(event.getUser().getId()) == null) {
            event.reply("Your Discord account is not linked to any Minecraft account.").setEphemeral(true).queue();
            return;
        }

        if (LinkManager.getInstance().unlink(event.getUser().getId())) {
            event.reply("Your Minecraft account has been unlinked.").setEphemeral(true).queue();
        } else {
            event.reply("Your account could not be unlinked.").setEphemeral(true).queue();
        }
    }

    private void handleBalance(SlashCommandInteractionEvent event) {

        UUID uuid = LinkManager.getInstance().getUUID(event.getUser().getId());

        if (uuid == null) {
            event.reply("You are not linked to any Minecraft account. Use `/link` to link one.").setEphemeral(true).queue();
            return;
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

        double balance = MoneyManager.getInstance().getBalance(player);

        String name = player.getName();
        if (name == null) name = DatabaseManager.get().getDatabase().getNameByUUID(uuid);
        if (name == null) name = uuid.toString();

        event.reply("Balance of **" + name + "**: " + String.format("%,.2f", balance)).setEphemeral(true).queue();
    }

    private void handlePorts(SlashCommandInteractionEvent event) {

        EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.setTitle("Trading ports");
        embedBuilder.setColor(new Color(100, 50, 150));

        if (MarketManager.getInstance().getPorts().isEmpty()) {
            embedBuilder.setDescription("No ports are set up.");
        } else {
            for (Port port : MarketManager.getInstance().getPorts()) {
                if (embedBuilder.getFields().size() >= 25) break;
                embedBuilder.addField(
                        port.getPlainDisplayName(),
                        "Id: `" + port.getId() + "`\nGoods: " + port.getParentItems().size(),
                        true);
            }
            embedBuilder.setFooter("Use /port <id> to see the local market of a port.");
        }

        event.replyEmbeds(embedBuilder.build()).setEphemeral(true).queue();
    }

    private void handlePort(SlashCommandInteractionEvent event) {

        String id = event.getOption("id").getAsString();

        Port port = MarketManager.getInstance().getPort(id);

        if (port == null) {
            event.reply("Unknown port: `" + id + "`. Use `/ports` to list all ports.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.setTitle(port.getPlainDisplayName() + " — local market");
        embedBuilder.setColor(new Color(100, 50, 150));
        embedBuilder.setFooter("Prices are local to this port. Trade in-game at the port.");

        List<Item> goods = port.getParentItemsInAlphabeticalOrder();

        if (goods.isEmpty()) {
            embedBuilder.setDescription("This port trades no goods.");
            event.replyEmbeds(embedBuilder.build()).setEphemeral(true).queue();
            return;
        }

        StringBuilder table = new StringBuilder();
        table.append(String.format("%-20s %10s %10s %7s %7s%n", "Good", "Buy", "Sell", "Stock", "1h"));

        for (Item item : goods) {
            table.append(String.format("%-20s %10s %10s %7d %6.1f%%%n",
                    truncate(item.getName(), 20),
                    Formatter.plainFormat(item.getCurrency(), item.getPrice().getBuyPrice(), Style.ROUND_BASIC),
                    Formatter.plainFormat(item.getCurrency(), item.getPrice().getSellPrice(), Style.ROUND_BASIC),
                    item.getStock(),
                    item.getPrice().getValueChangeLastHour()));
        }

        String content = table.toString();

        // Embed descriptions cap at 4096 characters; keep a margin for the code fences.
        if (content.length() > 4000) content = content.substring(0, 4000) + "\n(...)";

        embedBuilder.setDescription("```\n" + content + "```");

        event.replyEmbeds(embedBuilder.build()).setEphemeral(true).queue();
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "…";
    }

    // -------------------------------------------------------------------
    // Link log (used by LinkManager)
    // -------------------------------------------------------------------

    public void sendLinkLog(String userId, UUID uuid, String nick, boolean linked) {

        if (!Config.getInstance().getLogChannelEnabled()) return;

        if (!jda.getStatus().isInit()) {
            Nascraft.getInstance().getLogger().warning("JDA not ready. Link log skipped.");
            return;
        }

        String action = linked ? "linked to" : "unlinked from";
        String message = ":link: **" + (nick == null ? uuid.toString() : nick) + "** (`" + uuid + "`) " + action + " discord user `" + userId + "`.";

        jda.getGuilds().forEach(guild -> {
            TextChannel textChannel = guild.getTextChannelById(Config.getInstance().getLogChannel());

            if (textChannel == null) {
                Nascraft.getInstance().getLogger().info("Log channel could not be found. (link log)");
                return;
            }

            textChannel.sendMessage(message).queue();
        });
    }
}
