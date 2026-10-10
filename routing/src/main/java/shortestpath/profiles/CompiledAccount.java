package shortestpath.profiles;

import net.runelite.api.Client;
import shortestpath.accounts.Account;
import shortestpath.accounts.AccountClient;

/** An account and plugin settings turned into a refreshed pathfinder config. */
public final class CompiledAccount {
    private final AccountPathfinderConfig config;
    private final Client client;
    private final PluginSettings settings;

    private CompiledAccount(AccountPathfinderConfig config, Client client, PluginSettings settings) {
        this.config = config;
        this.client = client;
        this.settings = settings;
    }

    /**
     * Refreshes a pathfinder config for the account on the calling thread, which the plugin treats
     * as the client thread; run the pathfinder on the same thread.
     */
    public static CompiledAccount of(Account account, PluginSettings settings) {
        Client client = AccountClient.of(account);
        AccountPathfinderConfig config = new AccountPathfinderConfig(client, settings, account);
        config.bank = AccountClient.container(account.bank());
        if (account.plantedSpiritTrees() != null) {
            config.availableSpiritTrees = account.plantedSpiritTrees();
        }
        config.refresh();
        return new CompiledAccount(config, client, settings);
    }

    public AccountPathfinderConfig getConfig() { return config; }
    public Client getClient() { return client; }
    public PluginSettings getSettings() { return settings; }
}
