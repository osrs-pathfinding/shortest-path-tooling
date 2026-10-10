package shortestpath.profiles;

/** What a profile may depend on about the scenario it starts. */
public final class ProfileContext {
    /** The packed start tile, or {@code WorldPointUtil.UNDEFINED}. */
    public final int start;
    /** Whether the route may use transports at all. */
    public final boolean allowTransports;

    public ProfileContext(int start, boolean allowTransports) {
        this.start = start;
        this.allowTransports = allowTransports;
    }
}
