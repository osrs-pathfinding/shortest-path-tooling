package shortestpath.profiles;

import net.runelite.api.Client;
import shortestpath.accounts.Account;
import shortestpath.accounts.AccountCompiler;
import shortestpath.accounts.ClientState;
import shortestpath.accounts.HeadlessClient;

/** An account and plugin settings compiled into a refreshed pathfinder config. */
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
     * Compiles the account and refreshes a pathfinder config for it on the calling thread, which the
     * plugin treats as the client thread; run the pathfinder on the same thread.
     */
    public static CompiledAccount of(Account account, PluginSettings settings) {
        ClientState state = AccountCompiler.compile(account);
        Client client = HeadlessClient.of(state);
        AccountPathfinderConfig config = new AccountPathfinderConfig(client, settings, state);
        config.bank = HeadlessClient.container(state.bank());
        if (state.plantedSpiritTrees() != null) {
            config.availableSpiritTrees = state.plantedSpiritTrees();
        }
        config.refresh();
        return new CompiledAccount(config, client, settings);
    }

    public AccountPathfinderConfig getConfig() { return config; }
    public Client getClient() { return client; }
    public PluginSettings getSettings() { return settings; }
}
