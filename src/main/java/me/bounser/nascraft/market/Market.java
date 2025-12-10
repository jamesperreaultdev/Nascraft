package me.bounser.nascraft.market;

import de.tr7zw.changeme.nbtapi.NBT;
import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.database.DatabaseManager;
import me.bounser.nascraft.managers.ImagesManager;
import me.bounser.nascraft.managers.currencies.CurrenciesManager;
import me.bounser.nascraft.market.resources.Category;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.config.Config;
import org.bukkit.inventory.ItemStack;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.Map;

/**
 * Represents a single market instance with its own items, prices, and stock.
 * Each market is independent and has its own price history.
 */
public class Market {

    private final String marketId;
    private final String displayName;
    private final boolean useGlobalItems;
    private final List<String> customItemIds;
    private final Map<String, Double> priceOverrides;
    private final Map<String, Integer> stockOverrides;
    private final Integer restockAmount; // null means use global config
    private final Integer restockMinMinutes; // minimum random interval in minutes
    private final Integer restockMaxMinutes; // maximum random interval in minutes

    private final List<Item> items = new ArrayList<>();
    private final HashMap<String, Item> identifiers = new HashMap<>();
    private List<Category> categories = new ArrayList<>();

    private boolean active = true;

    private List<Float> marketChanges1h;
    private List<Float> marketChanges24h;

    private float lastChange;

    private int operationsLastHour = 0;

    private List<String> ignoredKeys = new ArrayList<>();

    public Market(String marketId, String displayName, boolean useGlobalItems, List<String> customItemIds,
                  Map<String, Double> priceOverrides, Map<String, Integer> stockOverrides, Integer restockAmount,
                  Integer restockMinMinutes, Integer restockMaxMinutes) {
        this.marketId = marketId;
        this.displayName = displayName != null ? displayName : marketId;
        this.useGlobalItems = useGlobalItems;
        this.customItemIds = customItemIds != null ? customItemIds : new ArrayList<>();
        this.priceOverrides = priceOverrides != null ? priceOverrides : new HashMap<>();
        this.stockOverrides = stockOverrides != null ? stockOverrides : new HashMap<>();
        this.restockAmount = restockAmount;
        this.restockMinMinutes = restockMinMinutes;
        this.restockMaxMinutes = restockMaxMinutes;
        this.ignoredKeys = Config.getInstance().getIgnoredKeys();
        this.active = !Config.getInstance().isMarketClosed();

        setupItems();
    }

    public void setupItems() {
        Config config = Config.getInstance();

        // Create categories (shared structure from categories.yml)
        for (String categoryName : Config.getInstance().getCategories()) {
            Category category = new Category(categoryName);
            categories.add(category);
        }

        // Determine which items to load
        List<String> itemsToLoad;
        if (useGlobalItems || customItemIds.isEmpty()) {
            itemsToLoad = new ArrayList<>(Config.getInstance().getAllMaterials());
        } else {
            itemsToLoad = customItemIds;
        }

        // Create items for this market
        for (String identifier : itemsToLoad) {

            ItemStack itemStack = config.getItemStackOfItem(identifier);

            if (itemStack == null) {
                Nascraft.getInstance().getLogger().warning("[" + marketId + "] Error with the itemStack item: " + identifier);
                Nascraft.getInstance().getLogger().warning("Make sure the material is correct and exists in your version.");
                continue;
            }

            Category category = getCategoryFromConfig(identifier);

            if (category == null) {
                Nascraft.getInstance().getLogger().warning("[" + marketId + "] No category found for item: " + identifier);
                continue;
            }

            BufferedImage image = ImagesManager.getInstance().getImage(identifier);

            // Get price and stock overrides for this market
            Double priceOverride = priceOverrides.get(identifier);
            Integer stockOverride = stockOverrides.get(identifier);

            Item item = new Item(
                    itemStack,
                    identifier,
                    config.getAlias(identifier),
                    category,
                    image,
                    marketId,
                    priceOverride,
                    stockOverride
            );

            // Load item data from database for this specific market
            DatabaseManager.get().getDatabase().retrieveItem(item, marketId);

            items.add(item);
            identifiers.put(identifier, item);
            category.addItem(item);

            // Add child items
            for (Item child : config.getChilds(identifier, marketId)) {
                item.addChildItem(child);
                items.add(child);
            }
        }

        Nascraft.getInstance().getLogger().info("[" + marketId + "] Loaded " + items.size() + " items in " + categories.size() + " categories.");

        marketChanges1h = new ArrayList<>(Collections.nCopies(60, 0f));
        marketChanges24h = new ArrayList<>(Collections.nCopies(24, 0f));
    }

    /**
     * Gets category from config that matches this market's category list
     */
    private Category getCategoryFromConfig(String identifier) {
        String categoryId = Config.getInstance().getCategoryOfMaterial(identifier);
        if (categoryId == null) return null;

        for (Category category : categories) {
            if (category.getIdentifier().equals(categoryId)) {
                return category;
            }
        }
        return null;
    }

    public void reload() {
        items.clear();
        identifiers.clear();
        categories.clear();
        setupItems();
    }

    // Getters
    public String getMarketId() { return marketId; }

    public String getDisplayName() { return displayName; }

    /**
     * Gets the restock amount for this market, or null if using global config
     */
    public Integer getRestockAmount() { return restockAmount; }

    /**
     * Gets the minimum restock interval in minutes for this market, or null if using global config
     */
    public Integer getRestockMinMinutes() { return restockMinMinutes; }

    /**
     * Gets the maximum restock interval in minutes for this market, or null if using global config
     */
    public Integer getRestockMaxMinutes() { return restockMaxMinutes; }

    /**
     * Gets the effective restock amount for an item in this market.
     * Uses market-specific amount if set, otherwise falls back to item-specific or global config.
     */
    public int getEffectiveRestockAmount(String identifier) {
        if (restockAmount != null) {
            return restockAmount;
        }
        return Config.getInstance().getItemRestockAmount(identifier);
    }

    /**
     * Gets the effective minimum restock interval in minutes.
     * Uses market-specific value if set, otherwise falls back to global config.
     */
    public int getEffectiveRestockMinMinutes() {
        if (restockMinMinutes != null) {
            return restockMinMinutes;
        }
        return Config.getInstance().getStockRestockMinMinutes();
    }

    /**
     * Gets the effective maximum restock interval in minutes.
     * Uses market-specific value if set, otherwise falls back to global config.
     */
    public int getEffectiveRestockMaxMinutes() {
        if (restockMaxMinutes != null) {
            return restockMaxMinutes;
        }
        return Config.getInstance().getStockRestockMaxMinutes();
    }

    /**
     * Gets the initial price override for an item in this market, or null if no override
     */
    public Double getPriceOverride(String identifier) {
        return priceOverrides.get(identifier);
    }

    /**
     * Gets the starting stock override for an item in this market, or null if no override
     */
    public Integer getStockOverride(String identifier) {
        return stockOverrides.get(identifier);
    }

    public Item getItem(ItemStack itemStack) {
        for (Item item : items) if (itemStack.isSimilar(item.getItemStack())) return item;
        return null;
    }

    public Item getItem(String identifier) {
        if (identifiers.containsKey(identifier)) return identifiers.get(identifier);
        return null;
    }

    public List<Category> getCategories() { return categories; }

    public List<Item> getAllItems() { return items; }

    public List<Item> getAllParentItemsInAlphabeticalOrder() {
        List<Item> sorted = new ArrayList<>(getAllParentItems());
        sorted.sort(Comparator.comparing(Item::getName));
        return sorted;
    }

    public List<String> getAllItemsAndChildsIdentifiers() {
        List<String> identifiers = new ArrayList<>();
        for (Item item : getAllItems()) {
            identifiers.add(item.getIdentifier());
        }
        return identifiers;
    }

    public List<Item> getAllParentItems() {
        List<Item> parents = new ArrayList<>();
        for (Item item : items) {
            if (item.isParent()) parents.add(item);
        }
        return parents;
    }

    public void stop() { active = false; }
    public void resume() { active = true; }

    public boolean getActive() { return active; }

    public boolean isAValidItem(ItemStack itemStack) {
        for (Item item : items)
            if (isSimilarEnough(item.getItemStack(), itemStack)) return true;
        return false;
    }

    public boolean isAValidParentItem(ItemStack itemStack) {
        for (Item item : getAllParentItems())
            if (isSimilarEnough(item.getItemStack(), itemStack)) return true;
        return false;
    }

    public boolean isSimilarEnough(ItemStack itemStack1, ItemStack itemStack2) {
        if (itemStack1 == null || itemStack2 == null) return false;
        if (!itemStack1.getType().equals(itemStack2.getType())) return false;

        ItemStack itemStackWithoutFlags1 = itemStack1.clone();
        ItemStack itemStackWithoutFlags2 = itemStack2.clone();

        for (String ignoredKey : ignoredKeys) {
            NBT.modify(itemStackWithoutFlags1, nbt -> {
                nbt.removeKey(ignoredKey);
            });
            NBT.modify(itemStackWithoutFlags2, nbt -> {
                nbt.removeKey(ignoredKey);
            });
        }

        return itemStackWithoutFlags1.isSimilar(itemStackWithoutFlags2);
    }

    public List<Item> getTopGainers(int quantity) {
        List<Item> itemsList = new ArrayList<>(getAllParentItems());
        List<Item> topGainers = new ArrayList<>();

        for (int i = 1; i <= quantity; i++) {
            if (itemsList.isEmpty()) break;

            Item imax = itemsList.get(0);
            for (Item item : itemsList) {
                float variation = item.getPrice().getValueChangeLastHour();
                if (variation != 0) {
                    if (variation > imax.getPrice().getValueChangeLastHour()) {
                        imax = item;
                    }
                }
            }
            itemsList.remove(imax);
            topGainers.add(imax);
        }
        return topGainers;
    }

    public List<Item> getTopDippers(int quantity) {
        List<Item> itemsList = new ArrayList<>(getAllParentItems());
        List<Item> topDippers = new ArrayList<>();

        for (int i = 1; i <= quantity; i++) {
            if (itemsList.isEmpty()) break;

            Item imax = itemsList.get(0);
            for (Item item : itemsList) {
                float variation = item.getPrice().getValueChangeLastHour();
                if (variation != 0) {
                    if (variation < imax.getPrice().getValueChangeLastHour()) {
                        imax = item;
                    }
                }
            }
            itemsList.remove(imax);
            topDippers.add(imax);
        }
        return topDippers;
    }

    public List<Item> getMostMoved(int quantity) {
        List<Item> itemsList = new ArrayList<>(getAllParentItems());
        List<Item> mostMoved = new ArrayList<>();

        for (int i = 1; i <= quantity; i++) {
            if (itemsList.isEmpty()) break;

            Item imax = itemsList.get(0);
            for (Item item : itemsList) {
                float variation = item.getPrice().getValueChangeLastHour();
                if (variation != 0) {
                    if (Math.abs(variation) > Math.abs(imax.getPrice().getValueChangeLastHour())) {
                        imax = item;
                    }
                }
            }
            itemsList.remove(imax);
            mostMoved.add(imax);
        }
        return mostMoved;
    }

    public List<Item> getMostTraded(int quantity) {
        List<Item> itemsList = new ArrayList<>(getAllParentItems());
        List<Item> mostTraded = new ArrayList<>();

        for (int i = 1; i <= quantity; i++) {
            if (itemsList.isEmpty()) break;

            Item imax = itemsList.get(0);
            for (Item item : itemsList) {
                if (item.getOperations() >= 1) {
                    if (item.getOperations() > imax.getOperations()) {
                        imax = item;
                    }
                }
            }
            itemsList.remove(imax);
            mostTraded.add(imax);
        }
        return mostTraded;
    }

    public int getPositionByVolume(Item item) {
        List<Item> itemsList = new ArrayList<>(getAllItems());
        itemsList.sort(Comparator.comparingDouble(Item::getVolume));
        return itemsList.size() - getIndexOf(item, itemsList);
    }

    public int getIndexOf(Item item, List<Item> list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == item) {
                return i;
            }
        }
        return -1;
    }

    public void updateMarketChange1h(float change) {
        lastChange = change;
        marketChanges1h.add(change);
        marketChanges1h.remove(0);
    }

    public List<Float> getBenchmark1h(float base) {
        List<Float> benchmark = new ArrayList<>();
        float value = base;

        for (float change : marketChanges1h) {
            value += value * change / 100;
            benchmark.add(value);
        }

        return benchmark;
    }

    public float getChange1h() {
        float change = 0;

        for (Item item : getAllParentItems())
            change += item.getPrice().getValue() / item.getPrice().getValueAnHourAgo() - 1;

        return change * 100;
    }

    public float getLastChange() { return lastChange; }

    public int[] getBenchmarkX(int xSize, int offset) { return Plot.getXPositions(xSize, offset, false, 60); }

    public int[] getBenchmarkY(int ySize, int offset) {
        return Plot.getYPositions(ySize, offset, false, getBenchmark1h(100));
    }

    public int getOperationsLastHour() { return operationsLastHour; }

    public void addOperation() { operationsLastHour++; }

    public void setOperationsLastHour(int operations) { operationsLastHour = operations; }

    public void removeItem(Item item) { items.remove(item); }

    public void addItem(Item item) { items.add(item); }

    public void removeCategory(Category category) { categories.remove(category); }

    public void addCategory(Category category) { categories.add(category); }

    public void setCategories(List<Category> categories) { this.categories = categories; }

    public Category getCategoryFromIdentifier(String identifier) {
        for (Category category : categories)
            if (category.getIdentifier().equals(identifier)) return category;
        return null;
    }

    public float getConsumerPriceIndex() {
        float index = 0;
        int numOfItems = 0;

        for (Item item : getAllParentItems()) {
            if (!item.getCurrency().equals(CurrenciesManager.getInstance().getDefaultCurrency())) continue;

            if (Config.getInstance().includeInCPI(item)) {
                index += (float) (item.getPrice().getValue() / item.getPrice().getInitialValue());
                numOfItems++;
            }
        }

        if (numOfItems == 0) return 100;
        return (index / numOfItems) * 100;
    }
}
