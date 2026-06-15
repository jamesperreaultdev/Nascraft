package me.bounser.nascraft.commands.admin.nascraft;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.database.commands.resources.Trade;
import me.bounser.nascraft.formatter.Formatter;
import me.bounser.nascraft.formatter.Style;
import net.kyori.adventure.platform.bukkit.BukkitComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Paged trade-log GUI for admins: global trades or the trades of a single
 * player. 45 trades per page, navigation arrows at slots 0 and 8.
 */
public class NascraftLogListener implements Listener {

    private static final int TRADES_PER_PAGE = 45;

    private static final int PREVIOUS_SLOT = 0;
    private static final int NEXT_SLOT = 8;

    @EventHandler
    public void onClick(InventoryClickEvent event) {

        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();

        if (!player.hasMetadata("NascraftLogInventory")) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();

        if (slot != PREVIOUS_SLOT && slot != NEXT_SLOT) return;

        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || !clicked.getType().equals(Material.ARROW)) return;

        List<MetadataValue> modeValues = player.getMetadata("NascraftLogInventory");
        List<MetadataValue> pageValues = player.getMetadata("NascraftLogInventoryPage");

        if (modeValues.isEmpty() || pageValues.isEmpty()) return;

        String mode = modeValues.get(0).asString();
        int page = pageValues.get(0).asInt();

        int newPage = slot == PREVIOUS_SLOT ? page - 1 : page + 1;

        if (newPage < 0) return;

        UUID uuid = mode.startsWith("uuid-") ? parseUUID(mode.substring(5)) : null;

        if (mode.startsWith("uuid-") && uuid == null) return;

        updateTradePage(event.getView().getTopInventory(), newPage, uuid);

        player.setMetadata("NascraftLogInventoryPage", new FixedMetadataValue(Nascraft.getInstance(), newPage));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {

        if (!(event.getWhoClicked() instanceof Player)) return;

        if (event.getWhoClicked().hasMetadata("NascraftLogInventory")) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {

        if (!(event.getPlayer() instanceof Player)) return;

        if (event.getPlayer().hasMetadata("NascraftLogInventory")) {
            event.getPlayer().removeMetadata("NascraftLogInventory", Nascraft.getInstance());
            event.getPlayer().removeMetadata("NascraftLogInventoryPage", Nascraft.getInstance());
        }
    }

    /** Opens the trade log for the viewer. uuid == null shows global trades. */
    public static void openTradeLog(Player viewer, UUID uuid) {

        Inventory logsGUI = Bukkit.createInventory(null, 54, "Trade log");

        ItemStack header;

        if (uuid == null) {
            header = new ItemStack(Material.CHEST);

            ItemMeta meta = header.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + "Global trades");
                header.setItemMeta(meta);
            }
        } else {
            header = new ItemStack(Material.PLAYER_HEAD);

            ItemMeta meta = header.getItemMeta();
            if (meta != null) {
                String name = DatabaseManager.get().getDatabase().getNameByUUID(uuid);
                meta.setDisplayName(ChatColor.BLUE + "Trades of: " + name + " (" + uuid + ")");
                header.setItemMeta(meta);
            }
        }

        logsGUI.setItem(4, header);

        for (int i : Arrays.asList(1, 2, 3, 5, 6, 7))
            logsGUI.setItem(i, new ItemStack(Material.BLACK_STAINED_GLASS_PANE));

        updateTradePage(logsGUI, 0, uuid);

        viewer.openInventory(logsGUI);

        // Set AFTER openInventory: opening fires InventoryCloseEvent for any
        // previous inventory, which would strip this metadata again.
        viewer.setMetadata("NascraftLogInventory",
                new FixedMetadataValue(Nascraft.getInstance(), uuid == null ? "global" : "uuid-" + uuid));
        viewer.setMetadata("NascraftLogInventoryPage", new FixedMetadataValue(Nascraft.getInstance(), 0));
    }

    private static void updateTradePage(Inventory logsGUI, int page, UUID uuid) {

        List<Trade> trades;

        // Fetch one more than a page holds to know whether a next page exists.
        if (uuid == null) {
            trades = DatabaseManager.get().getDatabase().retrieveTrades(page * TRADES_PER_PAGE, TRADES_PER_PAGE + 1);
        } else {
            trades = DatabaseManager.get().getDatabase().retrieveTrades(uuid, page * TRADES_PER_PAGE, TRADES_PER_PAGE + 1);
        }

        if (trades == null) trades = new ArrayList<>();

        if (page > 0) {
            logsGUI.setItem(PREVIOUS_SLOT, navArrow("Previous Page"));
        } else {
            logsGUI.setItem(PREVIOUS_SLOT, new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
        }

        if (trades.size() > TRADES_PER_PAGE) {
            logsGUI.setItem(NEXT_SLOT, navArrow("Next Page"));
        } else {
            logsGUI.setItem(NEXT_SLOT, new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
        }

        for (int i = 0; i < TRADES_PER_PAGE; i++) {

            int slot = i + 9;

            if (i >= trades.size()) {
                logsGUI.setItem(slot, null);
                continue;
            }

            Trade trade = trades.get(i);

            if (trade == null || trade.getItem() == null) {
                logsGUI.setItem(slot, null);
                continue;
            }

            logsGUI.setItem(slot, buildTradeDisplay(trade));
        }
    }

    private static ItemStack navArrow(String name) {

        ItemStack itemStack = new ItemStack(Material.ARROW);

        ItemMeta meta = itemStack.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.BLUE + name);
            itemStack.setItemMeta(meta);
        }

        return itemStack;
    }

    private static ItemStack buildTradeDisplay(Trade trade) {

        ItemStack itemStack = trade.getItem().getItemStack(Math.max(1, Math.min(trade.getAmount(), 64)));

        ItemMeta meta = itemStack.getItemMeta();

        if (meta == null) return itemStack;

        if (trade.isBuy()) {
            meta.setDisplayName(ChatColor.GREEN + "BUY: " + trade.getItem().getFormattedName());
        } else {
            meta.setDisplayName(ChatColor.RED + "SELL: " + trade.getItem().getFormattedName());
        }

        List<String> lore = new ArrayList<>();

        lore.add(ChatColor.BLUE + "Player: " + DatabaseManager.get().getDatabase().getNameByUUID(trade.getUuid())
                + " (" + trade.getUuid() + ")");

        if (trade.getItem().getPort() != null)
            lore.add(ChatColor.BLUE + "Port: " + trade.getItem().getPort().getPlainDisplayName()
                    + " (" + trade.getItem().getPort().getId() + ")");

        lore.add("");

        if (trade.getAmount() > 64) {
            lore.add(ChatColor.BLUE + "Quantity: " + trade.getAmount());
            lore.add("");
        }

        String price = ChatColor.BLUE + (trade.isBuy() ? "Price paid: " : "Price received: ");

        if (trade.getAmount() == 1) {
            price += legacy(Formatter.format(trade.getItem().getCurrency(), trade.getValue(), Style.ROUND_BASIC));
        } else {
            price += ChatColor.GREEN + legacy(Formatter.format(trade.getItem().getCurrency(), trade.getValue(), Style.ROUND_BASIC))
                    + ChatColor.BLUE + " -> "
                    + legacy(Formatter.format(trade.getItem().getCurrency(), trade.getValue() / trade.getAmount(), Style.ROUND_BASIC))
                    + ChatColor.BLUE + " each";
        }

        lore.add(price);
        lore.add("");
        lore.add(ChatColor.BLUE + getFormattedTime(trade.getDate()));

        meta.setLore(lore);

        itemStack.setItemMeta(meta);

        return itemStack;
    }

    private static String legacy(String miniMessage) {
        return BukkitComponentSerializer.legacy().serialize(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    private static UUID parseUUID(String uuidString) {
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String getFormattedTime(LocalDateTime date) {

        LocalDateTime now = LocalDateTime.now();

        Duration duration = Duration.between(date, now);

        long days = duration.toDays();
        duration = duration.minusDays(days);

        long hours = duration.toHours();
        duration = duration.minusHours(hours);

        long minutes = duration.toMinutes();
        duration = duration.minusMinutes(minutes);

        long seconds = duration.getSeconds();

        StringBuilder result = new StringBuilder();

        if (days > 0) result.append(days).append(" day").append(days != 1 ? "s" : "").append(", ");
        if (hours > 0) result.append(hours).append(" hour").append(hours != 1 ? "s" : "").append(", ");
        if (minutes > 0) result.append(minutes).append(" min").append(minutes != 1 ? "s" : "").append(", ");
        if (seconds > 0) result.append(seconds).append(" second").append(seconds != 1 ? "s" : "").append(", ");

        if (result.length() > 0) {
            result.setLength(result.length() - 2);
            result.append(" ago");
        } else {
            result.append("just now");
        }

        return result.toString();
    }
}
