package me.bounser.nascraft.commands.market;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.commands.Command;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.inventorygui.MarketMenuManager;
import me.bounser.nascraft.market.MarketManager;
import me.bounser.nascraft.market.Port;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /market opens the menu of the port the player is standing in.
 * /market &lt;portId&gt; opens a port remotely (requires 'nascraft.ports.bypass').
 */
public class MarketCommand extends Command {

    public MarketCommand() {
        super(
                "market",
                new String[]{Config.getInstance().getCommandAlias("market")},
                "Open the local port market",
                "nascraft.market"
        );
    }

    @Override
    public void execute(CommandSender sender, String[] args) {

        if (!(sender instanceof Player)) {
            Nascraft.getInstance().getLogger().info("Command not available through console.");
            return;
        }

        Player player = (Player) sender;

        if (Config.getInstance().getMarketPermissionRequirement() && !player.hasPermission("nascraft.market")) {
            Lang.get().message(player, Message.NO_PERMISSION);
            return;
        }

        Port port;

        if (args.length >= 1) {

            if (!player.hasPermission("nascraft.ports.bypass")) {
                Lang.get().message(player, Message.NO_PERMISSION);
                return;
            }

            port = MarketManager.getInstance().getPort(args[0]);

            if (port == null) {
                Lang.get().message(player, Message.PORT_NOT_FOUND, "[PORT]", args[0]);
                return;
            }

        } else {

            port = MarketManager.getInstance().getPortAt(player.getLocation());

            if (port == null) {
                Lang.get().message(player, Message.NOT_IN_PORT);
                return;
            }
        }

        MarketMenuManager.getInstance().openPortMenu(player, port);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {

        if (args.length == 1 && sender.hasPermission("nascraft.ports.bypass"))
            return StringUtil.copyPartialMatches(args[0],
                    new ArrayList<>(MarketManager.getInstance().getPortIds()), new ArrayList<>());

        return Collections.emptyList();
    }
}
