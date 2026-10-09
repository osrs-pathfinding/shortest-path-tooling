package shortestpath.profiles;

import net.runelite.api.Client;
import shortestpath.accounts.Account;
import shortestpath.accounts.AccountClient;

/** Turns an {@link Account} and plugin settings into the config the pathfinders read. */
public final class AccountCompiler {
    private AccountCompiler() { }

    /**
     * Refreshes on the calling thread, which the plugin treats as the client thread; run the
     * pathfinder on the same thread.
     */
    public static CompiledAccount compile(Account account, PluginSettings settings) {
        Client client = AccountClient.of(account);
        AccountPathfinderConfig config = new AccountPathfinderConfig(client, settings, account,
            settings.isBypassVarbitChecks(), settings.isBypassVarPlayerChecks());
        config.bank = AccountClient.container(account.bank());
        if (account.plantedSpiritTrees() != null) {
            config.availableSpiritTrees = account.plantedSpiritTrees();
        }
        config.refresh();
        return new CompiledAccount(config, client, settings);
    }

    public static CompiledAccount compile(Setup setup) {
        return compile(setup.account.build(), setup.settings);
    }
}
