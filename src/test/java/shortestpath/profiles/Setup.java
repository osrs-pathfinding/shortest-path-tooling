package shortestpath.profiles;

import java.util.function.Consumer;
import shortestpath.accounts.Account;

/** A profile's account and plugin settings, ready for scenario overrides. */
public final class Setup {
    public final Account.Builder account;
    public final PluginSettings settings;

    public Setup(Account.Builder account, PluginSettings settings) {
        this.account = account;
        this.settings = settings;
    }

    public Setup account(Consumer<Account.Builder> override) {
        override.accept(account);
        return this;
    }

    public Setup settings(Consumer<PluginSettings> override) {
        override.accept(settings);
        return this;
    }

    public CompiledAccount compile() {
        return AccountCompiler.compile(this);
    }
}
