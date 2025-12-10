package me.bounser.nascraft.market;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.market.unit.Item;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Map;

/**
 * Manages all market instances.
 * Each market has its own items with independent prices and stock.
 */
public class MarketsManager {

    private static MarketsManager instance = null;

    private final HashMap<String, Market> markets = new HashMap<>();
    private final HashMap<String, String> npcToMarket = new HashMap<>(); // Maps NPC IDs to market IDs

    private File marketsFile;
    private FileConfiguration marketsConfig;

    public static MarketsManager getInstance() {
        return instance == null ? new MarketsManager() : instance;
    }

    private MarketsManager() {
        instance = this;
        loadMarketsConfig();
        loadMarkets();
    }

    /**
     * Loads the markets.yml configuration file
     */
    private void loadMarketsConfig() {
        marketsFile = new File(Nascraft.getInstance().getDataFolder(), "markets.yml");

        if (!marketsFile.exists()) {
            Nascraft.getInstance().saveResource("markets.yml", false);
        }

        marketsConfig = YamlConfiguration.loadConfiguration(marketsFile);
    }

    /**
     * Loads all markets defined in markets.yml
     */
    private void loadMarkets() {
        ConfigurationSection marketsSection = marketsConfig.getConfigurationSection("markets");

        if (marketsSection == null) {
            Nascraft.getInstance().getLogger().warning("No markets defined in markets.yml! Creating default market.");
            createDefaultMarket();
            return;
        }

        for (String marketId : marketsSection.getKeys(false)) {
            ConfigurationSection marketSection = marketsSection.getConfigurationSection(marketId);
            if (marketSection == null) continue;

            // Display name is optional - defaults to marketId if not set
            String displayName = marketSection.contains("display-name") ? marketSection.getString("display-name") : null;
            boolean useGlobalItems = marketSection.getBoolean("use-global-items", true);
            List<String> marketItems = marketSection.getStringList("items");

            // Load per-market restock settings (optional - null means use global)
            Integer restockAmount = marketSection.contains("restock-amount") ? marketSection.getInt("restock-amount") : null;
            Integer restockMinMinutes = marketSection.contains("restock-min-minutes") ? marketSection.getInt("restock-min-minutes") : null;
            Integer restockMaxMinutes = marketSection.contains("restock-max-minutes") ? marketSection.getInt("restock-max-minutes") : null;

            // Load per-market item overrides (price and stock)
            Map<String, Double> priceOverrides = new HashMap<>();
            Map<String, Integer> stockOverrides = new HashMap<>();

            ConfigurationSection itemOverridesSection = marketSection.getConfigurationSection("item-overrides");
            if (itemOverridesSection != null) {
                for (String itemId : itemOverridesSection.getKeys(false)) {
                    ConfigurationSection itemSection = itemOverridesSection.getConfigurationSection(itemId);
                    if (itemSection != null) {
                        if (itemSection.contains("initial-price")) {
                            priceOverrides.put(itemId, itemSection.getDouble("initial-price"));
                        }
                        if (itemSection.contains("starting-stock")) {
                            stockOverrides.put(itemId, itemSection.getInt("starting-stock"));
                        }
                    }
                }
            }

            Market market = new Market(marketId, displayName, useGlobalItems, marketItems, priceOverrides, stockOverrides,
                    restockAmount, restockMinMinutes, restockMaxMinutes);
            markets.put(marketId, market);

            String effectiveDisplayName = market.getDisplayName();
            Nascraft.getInstance().getLogger().info("Loaded market: " + marketId +
                    (effectiveDisplayName.equals(marketId) ? "" : " (" + effectiveDisplayName + ")") + " with " +
                    (useGlobalItems ? "global items" : marketItems.size() + " custom items") +
                    (restockAmount != null ? ", restock: " + restockAmount : "") +
                    (restockMinMinutes != null || restockMaxMinutes != null ? ", interval: " +
                            (restockMinMinutes != null ? restockMinMinutes : "default") + "-" +
                            (restockMaxMinutes != null ? restockMaxMinutes : "default") + "min" : "") +
                    (priceOverrides.isEmpty() ? "" : ", " + priceOverrides.size() + " price overrides") +
                    (stockOverrides.isEmpty() ? "" : ", " + stockOverrides.size() + " stock overrides"));
        }

        if (markets.isEmpty()) {
            Nascraft.getInstance().getLogger().warning("No markets loaded! Creating default market.");
            createDefaultMarket();
        }
    }

    /**
     * Creates a default market if none are configured
     */
    private void createDefaultMarket() {
        Market defaultMarket = new Market("default", "Market", true, new ArrayList<>(), new HashMap<>(), new HashMap<>(), null, null, null);
        markets.put("default", defaultMarket);

        // Save to config
        marketsConfig.set("markets.default.display-name", "Market");
        marketsConfig.set("markets.default.use-global-items", true);
        saveMarketsConfig();
    }

    /**
     * Saves the markets.yml configuration file
     */
    public void saveMarketsConfig() {
        try {
            marketsConfig.save(marketsFile);
        } catch (IOException e) {
            Nascraft.getInstance().getLogger().severe("Could not save markets.yml: " + e.getMessage());
        }
    }

    /**
     * Get a market by ID
     */
    public Market getMarket(String marketId) {
        return markets.get(marketId);
    }

    /**
     * Get market by NPC ID
     */
    public Market getMarketByNPC(String npcId) {
        String marketId = npcToMarket.get(npcId);
        if (marketId == null) return null;
        return markets.get(marketId);
    }

    /**
     * Get the default market (first one loaded, or "default" if exists)
     */
    public Market getDefaultMarket() {
        if (markets.containsKey("default")) {
            return markets.get("default");
        }
        // Return first market if no default
        if (!markets.isEmpty()) {
            return markets.values().iterator().next();
        }
        return null;
    }

    /**
     * Get all markets
     */
    public Collection<Market> getAllMarkets() {
        return markets.values();
    }

    /**
     * Get all market IDs
     */
    public Set<String> getAllMarketIds() {
        return markets.keySet();
    }

    /**
     * Check if a market exists
     */
    public boolean marketExists(String marketId) {
        return markets.containsKey(marketId);
    }

    /**
     * Reload all markets
     */
    public void reload() {
        markets.clear();
        npcToMarket.clear();
        loadMarketsConfig();
        loadMarkets();
    }

    /**
     * Add an NPC to a market
     */
    public void addNPCToMarket(String npcId, String marketId) {
        npcToMarket.put(npcId, marketId);

        // Update config
        List<String> npcIds = marketsConfig.getStringList("markets." + marketId + ".npc-ids");
        if (!npcIds.contains(npcId)) {
            npcIds.add(npcId);
            marketsConfig.set("markets." + marketId + ".npc-ids", npcIds);
            saveMarketsConfig();
        }
    }

    /**
     * Remove an NPC from markets
     */
    public void removeNPC(String npcId) {
        String marketId = npcToMarket.remove(npcId);
        if (marketId != null) {
            List<String> npcIds = marketsConfig.getStringList("markets." + marketId + ".npc-ids");
            npcIds.remove(npcId);
            marketsConfig.set("markets." + marketId + ".npc-ids", npcIds);
            saveMarketsConfig();
        }
    }

    /**
     * Create a new market
     */
    public Market createMarket(String marketId, String displayName) {
        if (markets.containsKey(marketId)) {
            return markets.get(marketId);
        }

        Market market = new Market(marketId, displayName, true, new ArrayList<>(), new HashMap<>(), new HashMap<>(), null, null, null);
        markets.put(marketId, market);

        // Save to config - only save display-name if different from marketId
        if (displayName != null && !displayName.equals(marketId)) {
            marketsConfig.set("markets." + marketId + ".display-name", displayName);
        }
        marketsConfig.set("markets." + marketId + ".use-global-items", true);
        saveMarketsConfig();

        Nascraft.getInstance().getLogger().info("Created new market: " + marketId);
        return market;
    }

    /**
     * Delete a market
     */
    public boolean deleteMarket(String marketId) {
        if (!markets.containsKey(marketId)) {
            return false;
        }

        // Don't delete if it's the only market
        if (markets.size() <= 1) {
            Nascraft.getInstance().getLogger().warning("Cannot delete the last market!");
            return false;
        }

        markets.remove(marketId);

        // Remove NPC mappings
        npcToMarket.entrySet().removeIf(entry -> entry.getValue().equals(marketId));

        // Remove from config
        marketsConfig.set("markets." + marketId, null);
        saveMarketsConfig();

        Nascraft.getInstance().getLogger().info("Deleted market: " + marketId);
        return true;
    }

    /**
     * Stop all markets
     */
    public void stopAll() {
        for (Market market : markets.values()) {
            market.stop();
        }
    }

    /**
     * Resume all markets
     */
    public void resumeAll() {
        for (Market market : markets.values()) {
            market.resume();
        }
    }

    /**
     * Get an item across all markets (returns first found)
     * Used for backwards compatibility
     */
    public Item getItemAcrossMarkets(String identifier) {
        for (Market market : markets.values()) {
            Item item = market.getItem(identifier);
            if (item != null) return item;
        }
        return null;
    }

    /**
     * Check if NPC is registered to any market
     */
    public boolean isNPCRegistered(String npcId) {
        return npcToMarket.containsKey(npcId);
    }

    /**
     * Get market ID for an NPC
     */
    public String getMarketIdForNPC(String npcId) {
        return npcToMarket.get(npcId);
    }
}
