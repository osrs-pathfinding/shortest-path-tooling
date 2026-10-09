package shortestpath.scenarios;

import java.util.Objects;
import java.util.Set;
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
    private final boolean allowTransports;
    private final String description;
    private final Set<String> tiers;
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
        allowTransports = builder.allowTransports;
        description = builder.description;
        tiers = Set.copyOf(builder.tiers);
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

    /** Where a scenario without a start begins: the Grand Exchange. */
    public static final int DEFAULT_START = WorldPointUtil.packWorldPoint(3185, 3436, 0);

    /** The tile the route starts from: the start, or {@link #DEFAULT_START} when there is none. */
    public int getRouteStart() {
        return start != WorldPointUtil.UNDEFINED ? start : DEFAULT_START;
    }
    public Profile getProfile() { return profile; }
    public boolean isAllowTransports() { return allowTransports; }
    /** A human-readable route label beyond the name, or {@code null}. */
    public String getDescription() { return description; }
    /** Tier tags, e.g. a canonical route's {@code smoke}/{@code standard}/{@code full}. */
    public Set<String> getTiers() { return tiers; }
    public boolean isExpectedReachable() { return expectedReachable; }
    public OptionalInt getMinimumLength() { return minimumLength; }
    public OptionalInt getExpectedLength() { return expectedLength; }

    /** The profile's account and settings with this scenario's overrides applied. */
    public Setup setup() {
        Setup setup = profile.setup(new ProfileContext(start, allowTransports));
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
        builder.allowTransports = allowTransports;
        builder.description = description;
        builder.tiers = tiers;
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
        private boolean allowTransports = true;
        private String description;
        private Set<String> tiers = Set.of();
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

        /** The start as a packed tile ({@code WorldPointUtil.UNDEFINED} for the default start). */
        public Builder fromTile(int packed) {
            start = packed;
            return this;
        }

        public Builder toTile(int packed) {
            target = packed;
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

        /**
         * Whether the profile may enable transports at all (default true). Only the canonical
         * profiles read it; a canonical route with {@code allowTransports: false} walks.
         */
        public Builder allowTransports(boolean value) {
            allowTransports = value;
            return this;
        }

        /** A human-readable route label shown next to the name, e.g. a canonical route's name. */
        public Builder description(String value) {
            description = value;
            return this;
        }

        public Builder tiers(Set<String> value) {
            tiers = value;
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
