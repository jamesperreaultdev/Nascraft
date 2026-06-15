package me.bounser.nascraft.managers;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.managers.currencies.Currency;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

public class MoneyManager {

    private static MoneyManager instance;

    private final Economy economy;

    public static MoneyManager getInstance() { return instance == null ? instance = new MoneyManager() : instance; }

    private MoneyManager() {
        economy = Nascraft.getEconomy();

        if (economy == null)
            throw new IllegalStateException("Vault economy is not available. Nascraft requires Vault and an economy plugin.");
    }

    /** @return true only if the money was actually taken. */
    public boolean withdraw(OfflinePlayer player, Currency currency, double amount) {
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    /** @return true only if the money was actually deposited. */
    public boolean deposit(OfflinePlayer player, Currency currency, double amount) {
        EconomyResponse response = economy.depositPlayer(player, amount);

        if (!response.transactionSuccess())
            Nascraft.getInstance().getLogger().warning("Vault deposit of " + amount + " to " + player.getName() + " failed: " + response.errorMessage);

        return response.transactionSuccess();
    }

    public boolean hasEnoughMoney(OfflinePlayer player, double quantity) {
        return economy.getBalance(player) >= quantity;
    }

    public double getBalance(OfflinePlayer player) {
        return economy.getBalance(player);
    }
}
