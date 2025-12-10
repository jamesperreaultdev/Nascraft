package me.bounser.nascraft.inventorygui;

import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.config.lang.Lang;
import me.bounser.nascraft.config.lang.Message;
import me.bounser.nascraft.formatter.Formatter;
import me.bounser.nascraft.formatter.RoundUtils;
import me.bounser.nascraft.formatter.Style;
import me.bounser.nascraft.market.Market;
import me.bounser.nascraft.market.MarketsManager;
import me.bounser.nascraft.market.resources.Category;
import me.bounser.nascraft.market.unit.Item;
import net.kyori.adventure.platform.bukkit.BukkitComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import me.bounser.nascraft.Nascraft;

public class MarketMenuManager {

    private static MarketMenuManager instance;

    private HashMap<Player, MenuPage> playerMenus = new HashMap<>();
    private HashMap<Player, String> playerMarkets = new HashMap<>();

    public static MarketMenuManager getInstance() { return instance == null ? instance = new MarketMenuManager() : instance; }

    /**
     * Opens the default market menu (for backwards compatibility)
     */
    public void openMenu(Player player) {
        Market defaultMarket = MarketsManager.getInstance().getDefaultMarket();
        if (defaultMarket != null) {
            openMenu(player, defaultMarket);
        } else {
            new MainMenu(player);
        }
    }

    /**
     * Opens a specific market menu
     */
    public void openMenu(Player player, Market market) {
        // Store the current market for this player
        playerMarkets.put(player, market.getMarketId());
        player.setMetadata("NascraftMarket", new FixedMetadataValue(Nascraft.getInstance(), market.getMarketId()));

        new MainMenu(player, market);
    }

    /**
     * Gets the current market for a player
     */
    public Market getPlayerMarket(Player player) {
        String marketId = playerMarkets.get(player);
        if (marketId != null) {
            return MarketsManager.getInstance().getMarket(marketId);
        }
        // Fallback to default market
        return MarketsManager.getInstance().getDefaultMarket();
    }

    /**
     * Gets the current market ID for a player
     */
    public String getPlayerMarketId(Player player) {
        return playerMarkets.getOrDefault(player, "default");
    }

    /**
     * Clears player market data when they close menu
     */
    public void clearPlayerMarket(Player player) {
        playerMarkets.remove(player);
        if (player.hasMetadata("NascraftMarket")) {
            player.removeMetadata("NascraftMarket", Nascraft.getInstance());
        }
    }

    public void setMenuOfPlayer(Player player, MenuPage menu) {
        playerMenus.put(player, menu);
    }

    public MenuPage getMenuFromPlayer(Player player) {
        return playerMenus.get(player);
    }

    public void removeMenuFromPlayer(Player player) {
        playerMenus.remove(player);
    }

    public ItemStack generateItemStack(Material material, String name, List<String> lore) {

        ItemStack itemStack = new ItemStack(material);

        ItemMeta meta = itemStack.getItemMeta();

        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.setAttributeModifiers(null);

        itemStack.setItemMeta(meta);

        return itemStack;
    }

    public ItemStack generateItemStack(Material material, String name) {

        ItemStack itemStack = new ItemStack(material);

        ItemMeta meta = itemStack.getItemMeta();

        meta.setDisplayName(name);
        meta.setAttributeModifiers(null);

        itemStack.setItemMeta(meta);

        return itemStack;
    }

    public List<String> getLoreFromItem(Item item, String lore) {

        List<String> itemLoreLines = new ArrayList<>();

        float change = RoundUtils.roundToOne(-100 + item.getPrice().getValue() *100/item.getPrice().getValueAnHourAgo());

        String itemLore = lore
                .replace("[PRICE]", Formatter.format(item.getCurrency(), item.getPrice().getValue(), Style.ROUND_BASIC))
                .replace("[SELL-PRICE]", Formatter.format(item.getCurrency(), item.getPrice().getSellPrice(), Style.ROUND_BASIC))
                .replace("[BUY-PRICE]", Formatter.format(item.getCurrency(), item.getPrice().getBuyPrice(), Style.ROUND_BASIC));

        String changeFormatted = Lang.get().message(Message.GUI_POSITIVE_CHANGE);

        if (change == 0) changeFormatted = Lang.get().message(Message.GUI_NO_CHANGE);
        else if (change < 0) changeFormatted = Lang.get().message(Message.GUI_NEGATIVE_CHANGE);

        itemLore = itemLore
                .replace("[CHANGE]", changeFormatted)
                .replace("[PERCENTAGE]", String.valueOf(change));


        for (String line : itemLore.split("\\n")) {
            Component loreSegment = MiniMessage.miniMessage().deserialize(line);
            itemLoreLines.add(BukkitComponentSerializer.legacy().serialize(loreSegment));
        }

        // Add stock display if stock system is enabled
        Item stockItem = item.getParent() != null ? item.getParent() : item;
        if (Config.getInstance().getStockRestockEnabled()) {
            String stockLore = Lang.get().message(Message.GUI_STOCK_DISPLAY)
                    .replace("[STOCK]", String.valueOf(stockItem.getStock()));
            Component stockComponent = MiniMessage.miniMessage().deserialize(stockLore);
            itemLoreLines.add(BukkitComponentSerializer.legacy().serialize(stockComponent));
        }

        return itemLoreLines;
    }

}
