package shortestpath.profiles;

import net.runelite.api.Client;
import shortestpath.dashboard.DashboardPathfinderConfig;

/** An account and plugin settings turned into a refreshed pathfinder config. */
public final class CompiledAccount {
    private final AccountPathfinderConfig config;
    private final Client client;
    private final DashboardPathfinderConfig settings;

    CompiledAccount(AccountPathfinderConfig config, Client client, DashboardPathfinderConfig settings) {
        this.config = config;
        this.client = client;
        this.settings = settings;
    }

    public AccountPathfinderConfig getConfig() { return config; }
    public Client getClient() { return client; }
    public DashboardPathfinderConfig getSettings() { return settings; }
}
