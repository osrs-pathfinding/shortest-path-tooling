package shortestpath.profiles;

import net.runelite.api.Client;

/** An account and plugin settings turned into a refreshed pathfinder config. */
public final class CompiledAccount {
    private final AccountPathfinderConfig config;
    private final Client client;
    private final PluginSettings settings;

    CompiledAccount(AccountPathfinderConfig config, Client client, PluginSettings settings) {
        this.config = config;
        this.client = client;
        this.settings = settings;
    }

    public AccountPathfinderConfig getConfig() { return config; }
    public Client getClient() { return client; }
    public PluginSettings getSettings() { return settings; }
}
