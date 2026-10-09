package shortestpath.scenarios;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Consumer;
import shortestpath.WorldPointUtil;
import shortestpath.accounts.Account;
import shortestpath.dashboard.DashboardPathfinderConfig;
import shortestpath.profiles.CompiledAccount;
import shortestpath.profiles.Profile;
import shortestpath.profiles.ProfileContext;
import shortestpath.profiles.Setup;

/**
 * One route to plan: where from and to, the {@link Profile} it starts from, the account and
 * plugin-setting overrides on top of it, and what counts as correct.
 *
 * <pre>
 * suite.scenario("Digsite gate (#139) kudos 153+ crosses gate", "routing-issue-139")
 *     .from(3293, 3428, 0).to(3350, 3415, 0)
 *     .profile(UNIT_TEST)
 *     .account(a -&gt; a.varbit(VarbitID.VM_KUDOS, 153))
 *     .settings(s -&gt; s.setBypassVarbitChecks(false))
 *     .minimumLength(40);
 * </pre>
 *
 * {@link Overrides} names the overrides suites repeat. The exact expected length is data, kept per suite in
 * {@code scenarios/expected-lengths/<suite>.json} and rewritten by {@code captureExpectedLengths}.
 */
public final class Scenario {
    private final String name;
    private final String category;
    private final int start;
    private final int target;
    private final Profile profile;
    private final Consumer<Account.Builder> account;
    private final Consumer<DashboardPathfinderConfig> settings;
    private final boolean expectedReachable;
    private final OptionalInt minimumLength;
    private final OptionalInt expectedLength;

    private Scenario(Builder builder, OptionalInt expectedLength) {
        name = builder.name;
        category = builder.category;
        start = builder.start;
        target = builder.target;
        profile = Objects.requireNonNull(builder.profile, () -> "scenario " + name + " has no profile");
        account = builder.account;
        settings = builder.settings;
        expectedReachable = builder.expectedReachable;
        minimumLength = builder.minimumLength;
        this.expectedLength = expectedLength;
        if (target == WorldPointUtil.UNDEFINED) {
            throw new IllegalArgumentException("scenario " + name + " has no target");
        }
    }

    public static Builder scenario(String name, String category) {
        return new Builder(name, category);
    }

    public String getName() { return name; }
    public String getCategory() { return category; }
    /** The packed start tile, or {@code WorldPointUtil.UNDEFINED} for the dashboard's default start. */
    public int getStartPoint() { return start; }
    public int getEndPoint() { return target; }
    public Profile getProfile() { return profile; }
    public boolean isExpectedReachable() { return expectedReachable; }
    public OptionalInt getMinimumLength() { return minimumLength; }
    public OptionalInt getExpectedLength() { return expectedLength; }

    /** The profile's account and settings with this scenario's overrides applied. */
    public Setup setup() {
        Setup setup = profile.setup(new ProfileContext(start, true));
        account.accept(setup.account);
        settings.accept(setup.settings);
        return setup;
    }

    /** Compiles on the calling thread; run the pathfinder on the same thread. */
    public CompiledAccount compile() {
        return setup().compile();
    }

    Scenario withExpectedLength(OptionalInt length) {
        return new Scenario(toBuilder(), length);
    }

    private Builder toBuilder() {
        Builder builder = new Builder(name, category);
        builder.start = start;
        builder.target = target;
        builder.profile = profile;
        builder.account = account;
        builder.settings = settings;
        builder.expectedReachable = expectedReachable;
        builder.minimumLength = minimumLength;
        return builder;
    }

    public static final class Builder {
        private final String name;
        private final String category;
        private int start = WorldPointUtil.UNDEFINED;
        private int target = WorldPointUtil.UNDEFINED;
        private Profile profile;
        private Consumer<Account.Builder> account = account -> { };
        private Consumer<DashboardPathfinderConfig> settings = settings -> { };
        private boolean expectedReachable = true;
        private OptionalInt minimumLength = OptionalInt.empty();

        private Builder(String name, String category) {
            this.name = Objects.requireNonNull(name);
            this.category = Objects.requireNonNull(category);
        }

        public Builder from(int x, int y, int plane) {
            start = WorldPointUtil.packWorldPoint(x, y, plane);
            return this;
        }

        public Builder to(int x, int y, int plane) {
            target = WorldPointUtil.packWorldPoint(x, y, plane);
            return this;
        }

        public Builder profile(Profile value) {
            profile = value;
            return this;
        }

        /** Overrides the profile's account; applied after the profile and after earlier calls. */
        public Builder account(Consumer<Account.Builder> override) {
            account = account.andThen(override);
            return this;
        }

        /** Overrides the profile's plugin settings; applied after the profile and after earlier calls. */
        public Builder settings(Consumer<DashboardPathfinderConfig> override) {
            settings = settings.andThen(override);
            return this;
        }

        /** The route must not be found: an intentional-failure scenario. */
        public Builder expectUnreachable() {
            expectedReachable = false;
            return this;
        }

        public Builder expectReachable(boolean value) {
            expectedReachable = value;
            return this;
        }

        /** Checked when the suite has no exact expected length for this scenario. */
        public Builder minimumLength(int length) {
            minimumLength = OptionalInt.of(length);
            return this;
        }

        public Scenario build() {
            return new Scenario(this, OptionalInt.empty());
        }
    }
}
