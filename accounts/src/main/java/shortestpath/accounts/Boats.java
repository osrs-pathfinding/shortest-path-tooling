package shortestpath.accounts;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The boats an account owns, where they can be boarded and what they are fitted with, declared rather than derived:
 * owning a boat is not a Sailing level. The router sails them only where {@link #moored} allows, and into the
 * restricted sea areas only as {@link #fitted} says.
 */
public final class Boats {
    public enum Kind { RAFT, SKIFF, SLOOP }

    /**
     * The hulls, by the Sailing level each needs (the cache's sailing hull table), and the base speed each gives in
     * tiles per tick.
     */
    public enum Hull {
        WOODEN(1, 1.5), OAK(20, 1.5), TEAK(31, 2.0), MAHOGANY(48, 2.0), CAMPHOR(67, 2.5), IRONWOOD(81, 2.5),
        ROSEWOOD(93, 3.0);

        private final int sailingLevel;
        private final double speed;

        Hull(int sailingLevel, double speed) {
            this.sailingLevel = sailingLevel;
            this.speed = speed;
        }

        public int sailingLevel() { return sailingLevel; }
        /** Base speed in tiles per tick. */
        public double speed() { return speed; }
    }

    public enum Moored {
        /** Where the boats are is not known, so none is boarded on foot. */
        UNKNOWN,
        /**
         * Each boat is taken to be at whichever mooring a route boards at: an assumption for benchmark accounts,
         * whose boat positions are not modelled.
         */
        EVERY_MOORING
    }

    /** The parts the boats have for the restricted sea areas (Tempor Tantrum, fetid, crystal-flecked, kelp, icy). */
    public enum Fitted {
        /** None: they keep to open water. */
        NONE,
        /**
         * The best the account's Sailing and Construction levels can build: an assumption for benchmark accounts,
         * whose boats' parts are not modelled.
         */
        BEST_FOR_LEVELS,
        /** Every part, so only a boat's hull limits where it goes: as the plugin takes the boat a player is on. */
        ALL
    }

    /** No boats. */
    public static final Boats NONE = new Boats(EnumSet.noneOf(Kind.class), Moored.UNKNOWN, Fitted.NONE, 0, null, -1);

    /** The boats' speed when none is declared, in tiles per tick: the plugin's estimate of a base speed. */
    public static final double ESTIMATED_SPEED = 2.0;

    private final Set<Kind> owned;
    private final Moored moored;
    private final Fitted fitted;
    private final double speed;
    private final Kind aboard;
    private final int aboardTile;

    private Boats(Set<Kind> owned, Moored moored, Fitted fitted, double speed, Kind aboard, int aboardTile) {
        this.owned = Collections.unmodifiableSet(owned.isEmpty() ? EnumSet.noneOf(Kind.class) : EnumSet.copyOf(owned));
        this.moored = moored;
        this.fitted = fitted;
        this.speed = speed;
        this.aboard = aboard;
        this.aboardTile = aboardTile;
    }

    /** {@code kinds}, each at every mooring, at the estimated speed, with no parts for the restricted areas. */
    public static Boats atEveryMooring(Kind... kinds) {
        return new Boats(Set.of(kinds), Moored.EVERY_MOORING, Fitted.NONE, ESTIMATED_SPEED, null, -1);
    }

    /**
     * Aboard a {@code kind} at sea on {@code tile} (packed), sailing at {@code speed} tiles per tick, as the plugin
     * sees a player at the helm: the only boat, and no other boarded on the way. Like the plugin, it is taken to have
     * every part.
     */
    public static Boats aboard(Kind kind, double speed, int tile) {
        return new Boats(EnumSet.noneOf(Kind.class), Moored.UNKNOWN, Fitted.ALL, speed, kind, tile);
    }

    /**
     * These boats with the player aboard their {@code kind} at sea on {@code tile} (packed), as the plugin sees a player
     * at the helm of one of their own boats: the others stay where they are moored.
     */
    public Boats boardedAt(Kind kind, int tile) {
        Set<Kind> others = EnumSet.noneOf(Kind.class);
        others.addAll(owned);
        others.remove(kind);
        return new Boats(others, moored, fitted, speed, kind, tile);
    }

    /** These boats with {@code hull}, sailing at its base speed. */
    public Boats hull(Hull value) {
        return new Boats(owned, moored, fitted, value.speed(), aboard, aboardTile);
    }

    /** These boats with {@code fitted} parts. */
    public Boats fitted(Fitted value) {
        return new Boats(owned, moored, value, speed, aboard, aboardTile);
    }

    public Set<Kind> owned() { return owned; }
    public Moored moored() { return moored; }
    /** The parts the boats have for the restricted sea areas. */
    public Fitted fitted() { return fitted; }
    /** Base speed in tiles per tick. */
    public double speed() { return speed; }
    /** The boat the player is aboard, or {@code null} on foot. */
    public Kind aboard() { return aboard; }
    /** Where the boat aboard is (packed); meaningless on foot. */
    public int aboardTile() { return aboardTile; }
}
