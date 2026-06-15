package me.bounser.nascraft.managers.currencies;

import me.bounser.nascraft.config.Config;

import java.util.Collections;
import java.util.List;

public class CurrenciesManager {

    public static CurrenciesManager instance;

    private final Currency vaultCurrency;

    public static CurrenciesManager getInstance() { return instance == null ? instance = new CurrenciesManager() : instance; }

    private CurrenciesManager() {

        Config config = Config.getInstance();

        vaultCurrency = new Currency(
                "vault",
                CurrencyType.VAULT,
                null,
                null,
                config.getCurrencyNotEnough(),
                config.getCurrencyFormat(),
                null,
                config.getCurrencyDecimals(),
                config.getCurrencyTopLimit(),
                config.getCurrencyLowLimit());
    }

    public Currency getCurrency(String currencyIdentifier) { return vaultCurrency; }

    public Currency getVaultCurrency() { return vaultCurrency; }

    public List<Currency> getCurrencies() { return Collections.singletonList(vaultCurrency); }

    public Currency getDefaultCurrency() { return vaultCurrency; }
}
