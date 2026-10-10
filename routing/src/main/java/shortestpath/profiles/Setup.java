package shortestpath.profiles;

import shortestpath.accounts.Account;

/** A profile's account and plugin settings, ready for a scenario's overrides and then compiling. */
public final class Setup {
    public final Account.Builder account;
    public final PluginSettings settings;

    public Setup(Account.Builder account, PluginSettings settings) {
        this.account = account;
        this.settings = settings;
    }

    /** Builds the account and compiles it; see {@link CompiledAccount#of}. */
    public CompiledAccount compile() {
        return CompiledAccount.of(account.build(), settings);
    }
}
