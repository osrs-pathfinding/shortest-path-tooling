package shortestpath.profiles;

import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import shortestpath.ShortestPathConfig;
import shortestpath.accounts.Account;
import shortestpath.pathfinder.TestPathfinderConfig;

/**
 * A {@link TestPathfinderConfig} that answers quest states and the clock from an {@link Account}.
 * Hand-rolled rather than a client stub because {@code getQuestState} runs in the search loop.
 */
public final class AccountPathfinderConfig extends TestPathfinderConfig {
    private final Account account;

    AccountPathfinderConfig(Client client, ShortestPathConfig settings, Account account,
            boolean bypassVarbitChecks, boolean bypassVarPlayerChecks) {
        super(client, settings, account.defaultQuestState(), bypassVarbitChecks, bypassVarPlayerChecks);
        this.account = account;
    }

    public Account getAccount() {
        return account;
    }

    @Override
    public QuestState getQuestState(Quest quest) {
        return account.questState(quest);
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
