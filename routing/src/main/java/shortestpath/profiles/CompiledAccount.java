package shortestpath.profiles;

import net.runelite.api.Client;
import shortestpath.accounts.Account;
import shortestpath.accounts.AccountCompiler;
import shortestpath.accounts.ClientState;
import shortestpath.accounts.HeadlessClient;
import shortestpath.accounts.Unlock;
import shortestpath.items.ItemStateService;

/** An account and plugin settings compiled into a refreshed pathfinder config. */
public final class CompiledAccount {
    private final AccountPathfinderConfig config;
    private final Client client;
    private final PluginSettings settings;
    private final ItemStateService itemState;

    private CompiledAccount(AccountPathfinderConfig config, Client client, PluginSettings settings,
        ItemStateService itemState) {
        this.config = config;
        this.client = client;
        this.settings = settings;
        this.itemState = itemState;
    }

    /**
     * Compiles the account and refreshes a pathfinder config for it on the calling thread, which the
     * plugin treats as the client thread; run the pathfinder on the same thread.
     */
    public static CompiledAccount of(Account account, PluginSettings settings) {
        applyUnlockSettings(account, settings);
        ClientState state = AccountCompiler.compile(account);
        Client client = HeadlessClient.of(state);
        AccountPathfinderConfig config = new AccountPathfinderConfig(client, settings, state);
        ItemStateService itemState = new ItemStateService(null, null);
        config.setItemStateService(itemState);
        itemState.noteBankContainer(HeadlessClient.container(state.bank()));
        if (state.plantedSpiritTrees() != null) {
            config.getSpiritTrees().setAvailableSpiritTreesForTest(state.plantedSpiritTrees());
        }
        config.refresh();
        return new CompiledAccount(config, client, settings, itemState);
    }

    private static void applyUnlockSettings(Account account, PluginSettings settings) {
        for (Unlock unlock : account.unlocks()) {
            if (unlock.kind != Unlock.Kind.SETTING) {
                continue;
            }
            switch (unlock.setting) {
                case "respawnPrifddinas": settings.setRespawnPrifddinas(true); break;
                case "unlockCanoeAxe": settings.setUnlockCanoeAxe(true); break;
                case "unlockXericsHonour": settings.setUnlockXericsHonour(true); break;
                case "unlockDragontoothPassage": settings.setUnlockDragontoothPassage(true); break;
                case "unlockMorUlRek": settings.setUnlockMorUlRek(true); break;
                case "unlockBalloonLogBasket": settings.setUnlockBalloonLogBasket(true); break;
                default: throw new IllegalStateException("no plugin setting for unlock " + unlock);
            }
        }
    }

    public AccountPathfinderConfig getConfig() { return config; }
    public Client getClient() { return client; }
    public PluginSettings getSettings() { return settings; }
    public ItemStateService getItemState() { return itemState; }
}
