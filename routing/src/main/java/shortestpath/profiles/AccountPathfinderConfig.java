package shortestpath.profiles;

import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import shortestpath.accounts.Account;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PluginResources;
import shortestpath.transport.Transport;

/**
 * A {@link PathfinderConfig} for an {@link Account}, on the shared {@link PluginResources}. It
 * answers quest states and the clock from the account, and skips varbit or varplayer transport
 * requirements when the settings bypass them. Quest states are answered here rather than by the
 * client because {@code getQuestState} runs in the search loop.
 */
public final class AccountPathfinderConfig extends PathfinderConfig {
    private final Account account;
    private final boolean bypassVarbitChecks;
    private final boolean bypassVarPlayerChecks;

    AccountPathfinderConfig(Client client, PluginSettings settings, Account account) {
        super(client, settings, PluginResources.map(), PluginResources.transports(),
            PluginResources.destinations(), PluginResources.filteredDestinations(),
            PluginResources.bankRequirements());
        this.account = account;
        this.bypassVarbitChecks = settings.isBypassVarbitChecks();
        this.bypassVarPlayerChecks = settings.isBypassVarPlayerChecks();
    }

    @Override
    public QuestState getQuestState(Quest quest) {
        return account.questState(quest);
    }

    @Override
    public boolean varbitChecks(Transport transport, long evaluationTimeMinutes) {
        return !bypassVarbitChecks && super.varbitChecks(transport, evaluationTimeMinutes);
    }

    @Override
    public boolean varPlayerChecks(Transport transport, long evaluationTimeMinutes) {
        return !bypassVarPlayerChecks && super.varPlayerChecks(transport, evaluationTimeMinutes);
    }

    @Override
    protected long currentTimeMinutes() {
        return account.nowMinutes() == null ? super.currentTimeMinutes() : account.nowMinutes();
    }

    /** The game time timed requirements are evaluated at. */
    public long evaluationTimeMinutes() {
        return currentTimeMinutes();
    }
}
