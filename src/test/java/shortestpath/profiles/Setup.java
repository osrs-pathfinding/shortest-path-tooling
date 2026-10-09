package shortestpath.profiles;

import java.util.function.Consumer;
import shortestpath.accounts.Account;
import shortestpath.dashboard.DashboardPathfinderConfig;

/** A profile's account and plugin settings, ready for scenario overrides. */
public final class Setup {
    public final Account.Builder account;
    public final DashboardPathfinderConfig settings;

    public Setup(Account.Builder account, DashboardPathfinderConfig settings) {
        this.account = account;
        this.settings = settings;
    }

    public Setup account(Consumer<Account.Builder> override) {
        override.accept(account);
        return this;
    }

    public Setup settings(Consumer<DashboardPathfinderConfig> override) {
        override.accept(settings);
        return this;
    }

    public CompiledAccount compile() {
        return AccountCompiler.compile(this);
    }
}
