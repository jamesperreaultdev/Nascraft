package me.bounser.nascraft.commands.market;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.commands.Command;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.inventorygui.*;
import me.bounser.nascraft.market.Market;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.MarketsManager;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.market.resources.Category;
import me.bounser.nascraft.market.unit.Item;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MarketCommand extends Command {

    public MarketCommand() {
        super(
                "market",
                new String[]{Config.getInstance().getCommandAlias("market")},
                "Direct access to the market",
                "nascraft.market"
        );
    }

    @Override
    public void execute(CommandSender sender, String[] args) {

        if(sender instanceof Player) {

            Player player = (Player) sender;

            if (!player.hasPermission("nascraft.market") && Config.getInstance().getMarketPermissionRequirement()) {
                Lang.get().message(player, Message.NO_PERMISSION);
                return;
            }

            // /market [marketId] - opens specific market or default
            if (args.length == 0 && player.hasPermission("nascraft.market.gui")) {
                MarketMenuManager.getInstance().openMenu(player);
                return;
            }

            // Check if first arg is a market ID
            if (args.length >= 1 && player.hasPermission("nascraft.market.gui")) {
                Market market = MarketsManager.getInstance().getMarket(args[0]);
                if (market != null) {
                    MarketMenuManager.getInstance().openMenu(player, market);
                    return;
                }
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("category") && player.hasPermission("nascraft.market.gui")) {

                Market market = MarketMenuManager.getInstance().getPlayerMarket(player);
                if (market == null) market = MarketsManager.getInstance().getDefaultMarket();

                Category category = market != null ? market.getCategoryFromIdentifier(args[1]) : MarketManager.getInstance().getCategoryFromIdentifier(args[1]);

                if (category == null) {
                    Lang.get().message(player, Message.MARKET_CMD_INVALID_CATEGORY);
                    return;
                }

                MarketMenuManager.getInstance().setMenuOfPlayer(player, new CategoryMenu(player, category));
                return;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("item") && player.hasPermission("nascraft.market.gui")) {

                Market market = MarketMenuManager.getInstance().getPlayerMarket(player);
                if (market == null) market = MarketsManager.getInstance().getDefaultMarket();

                Item item = market != null ? market.getItem(args[1].toLowerCase()) : MarketManager.getInstance().getItem(args[1].toLowerCase());

                if (item == null) {
                    Lang.get().message(player, Message.MARKET_CMD_INVALID_ITEM);
                    return;
                }

                MarketMenuManager.getInstance().setMenuOfPlayer(player, new BuySellMenu(player, item));
                return;
            }

            if (args.length != 3) {
                Lang.get().message(player, Message.MARKET_CMD_INVALID_USE);
                return;
            }

            int quantity;

            try {
                quantity = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                Lang.get().message(player, Message.MARKET_CMD_INVALID_QUANTITY);
                return;
            }

            if (quantity <= 0) {
                Lang.get().message(player, Message.MARKET_CMD_INVALID_QUANTITY);
                return;
            }

            if (quantity > 64) {
                Lang.get().message(player, Message.MARKET_CMD_MAX_QUANTITY_REACHED);
                return;
            }

            Item item = MarketManager.getInstance().getItem(args[1]);

            if (item == null) {
                Lang.get().message(player, Message.MARKET_CMD_INVALID_IDENTIFIER);
                return;
            }

            switch (args[0].toLowerCase()){
                case "buy":
                    item.buy(quantity, player.getUniqueId(), true);
                    break;
                case "sell":
                    item.sell(quantity, player.getUniqueId(), true);
                    break;
                default:
                    Lang.get().message(player, Message.MARKET_CMD_INVALID_OPTION);
            }

        } else {

            // Console command: /market <player> - open default market for player
            if (args.length == 1) {

                Player player = Bukkit.getPlayer(args[0]);

                if (player == null) {
                    Nascraft.getInstance().getLogger().info(ChatColor.RED + "Invalid player");
                    return;
                }

                MarketMenuManager.getInstance().openMenu(player);
                return;
            }

            // Console command: /market open <player> <marketId> - open specific market for player
            if (args.length == 3 && args[0].equalsIgnoreCase("open")) {

                Player player = Bukkit.getPlayer(args[1]);

                if (player == null) {
                    Nascraft.getInstance().getLogger().info(ChatColor.RED + "Invalid player: " + args[1]);
                    return;
                }

                Market market = MarketsManager.getInstance().getMarket(args[2]);

                if (market == null) {
                    Nascraft.getInstance().getLogger().info(ChatColor.RED + "Invalid market: " + args[2]);
                    Nascraft.getInstance().getLogger().info("Available markets: " + MarketsManager.getInstance().getAllMarketIds());
                    return;
                }

                MarketMenuManager.getInstance().openMenu(player, market);
                Nascraft.getInstance().getLogger().info("Opened market '" + market.getDisplayName() + "' for " + player.getName());
                return;
            }

            if (args.length == 3 && args[0].toLowerCase().equals("category")) {

                Player player = Bukkit.getPlayer(args[2]);

                if (player == null) {
                    Nascraft.getInstance().getLogger().info(ChatColor.RED + "Invalid player");
                    return;
                }

                Category category = MarketManager.getInstance().getCategoryFromIdentifier(args[1]);

                if (category == null) {
                    Nascraft.getInstance().getLogger().info(ChatColor.RED + "Invalid category");
                    return;
                }

                MarketMenuManager.getInstance().setMenuOfPlayer(player, new CategoryMenu(player, category));
                return;
            }

            if (args.length != 4) {
                Nascraft.getInstance().getLogger().info(ChatColor.RED  + "Invalid use of command.");
                Nascraft.getInstance().getLogger().info("(CONSOLE) /market <Player> - Open default market");
                Nascraft.getInstance().getLogger().info("(CONSOLE) /market open <Player> <MarketId> - Open specific market");
                Nascraft.getInstance().getLogger().info("(CONSOLE) /market category <category-identifier> <Player>");
                Nascraft.getInstance().getLogger().info("(CONSOLE) /market <Buy/Sell> <Material> <Quantity> <Player>");
                return;
            }

            Player player = Bukkit.getPlayer(args[3]);

            if (player == null) {
                Nascraft.getInstance().getLogger().info(ChatColor.RED + "Invalid player");
                return;
            }

            Item item = MarketManager.getInstance().getItem(args[1]);
            switch (args[0]){
                case "buy":
                    item.buy(Integer.parseInt(args[2]), player.getUniqueId(), true);
                    break;
                case "sell":
                    item.sell(Integer.parseInt(args[2]), player.getUniqueId(), true);
                    break;
                default:
                    sender.sendMessage(ChatColor.RED + "Wrong option: buy / sell");
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {

        switch (args.length) {
            case 1:
                List<String> options = new ArrayList<>(Arrays.asList("buy", "sell", "category", "item", "open"));
                // Add market IDs to suggestions
                options.addAll(MarketsManager.getInstance().getAllMarketIds());
                return StringUtil.copyPartialMatches(args[0], options, new ArrayList<>());
            case 2:
                if (args[0].equalsIgnoreCase("open")) {
                    // Tab complete player names for /market open <player>
                    List<String> playerNames = new ArrayList<>();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        playerNames.add(player.getName());
                    }
                    return StringUtil.copyPartialMatches(args[1], playerNames, new ArrayList<>());
                }
                return StringUtil.copyPartialMatches(args[1], MarketManager.getInstance().getAllItemsAndChildsIdentifiers(), new ArrayList<>());
            case 3:
                if (args[0].equalsIgnoreCase("open")) {
                    // Tab complete market IDs for /market open <player> <marketId>
                    return StringUtil.copyPartialMatches(args[2], new ArrayList<>(MarketsManager.getInstance().getAllMarketIds()), new ArrayList<>());
                }
                return Collections.singletonList("quantity");
        }

        return null;
    }
}
