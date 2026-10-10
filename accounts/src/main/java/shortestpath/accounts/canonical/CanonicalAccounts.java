package shortestpath.accounts.canonical;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import shortestpath.accounts.Account;
import shortestpath.accounts.AccountBuild;
import shortestpath.accounts.AccountBuilds;

/**
 * The canonical profiles ({@code early}, {@code mid}, {@code end}, {@code maxed}) as account
 * builds, the same documents the corpus publishes, and as {@link Account}s compiled from them by
 * {@link AccountBuilds}, as the route service compiles the web planner's builds.
 */
public final class CanonicalAccounts {
    public static final List<String> NAMES = List.of("early", "mid", "end", "maxed");

    private static final Map<String, Supplier<ProfileSpec>> PROFILES = Map.of(
        "early", CanonicalProfiles::early,
        "mid", CanonicalProfiles::mid,
        "end", CanonicalProfiles::end,
        "maxed", CanonicalProfiles::maxed);

    private CanonicalAccounts() { }

    /** The game time every canonical profile is evaluated at, in minutes. */
    public static long benchmarkNowMinutes() {
        return RoutingVariables.BENCHMARK_NOW_MINUTES;
    }

    /** A fresh copy of the profile's account build. */
    public static AccountBuild build(String name) {
        Supplier<ProfileSpec> profile = PROFILES.get(name);
        if (profile == null) {
            throw new IllegalArgumentException("unknown canonical profile: " + name);
        }
        return ProfileCompiler.build(profile.get());
    }

    public static Account account(String name) {
        return AccountBuilds.toAccount(build(name));
    }
}
