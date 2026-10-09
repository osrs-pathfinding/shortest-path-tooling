package shortestpath.benchmark.canonical;

import shortestpath.WorldPointUtil;
import shortestpath.profiles.CompiledAccount;
import shortestpath.profiles.ProfileContext;
import shortestpath.profiles.Profiles;
import shortestpath.profiles.Setup;

/** Compiles a canonical profile for the benchmark and the route query. */
final class CanonicalAccountCompiler {
    private CanonicalAccountCompiler() { }

    /** Compiles canonical profile {@code name}, evaluating timed requirements at {@code nowMinutes}. */
    static CompiledAccount compile(String name, boolean allowTransports, long nowMinutes) {
        if (!Profiles.canonicalNames().contains(name)) {
            throw new IllegalArgumentException("unknown profile \"" + name + "\"; expected "
                + String.join(", ", Profiles.canonicalNames()));
        }
        Setup setup = Profiles.get(name).setup(new ProfileContext(WorldPointUtil.UNDEFINED, allowTransports));
        setup.account.nowMinutes(nowMinutes);
        return setup.compile();
    }
}
