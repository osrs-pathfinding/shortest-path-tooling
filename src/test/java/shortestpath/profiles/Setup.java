package shortestpath.profiles;

import net.runelite.api.Client;
import shortestpath.accounts.Account;
import shortestpath.accounts.AccountClient;

/** A profile's account and plugin settings, ready for a scenario's overrides and then compiling. */
public final class Setup {
    public final Account.Builder account;
    public final PluginSettings settings;

    public Setup(Account.Builder account, PluginSettings settings) {
        this.account = account;
        this.settings = settings;
    }

    /**
     * Builds the account and refreshes a pathfinder config for it on the calling thread, which the
     * plugin treats as the client thread; run the pathfinder on the same thread.
     */
    public CompiledAccount compile() {
        Account built = account.build();
        Client client = AccountClient.of(built);
        AccountPathfinderConfig config = new AccountPathfinderConfig(client, settings, built,
            settings.isBypassVarbitChecks(), settings.isBypassVarPlayerChecks());
        config.bank = AccountClient.container(built.bank());
        if (built.plantedSpiritTrees() != null) {
            config.availableSpiritTrees = built.plantedSpiritTrees();
        }
        config.refresh();
        return new CompiledAccount(config, client, settings);
    }
}
