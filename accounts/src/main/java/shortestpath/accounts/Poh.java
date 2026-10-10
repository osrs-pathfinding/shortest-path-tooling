package shortestpath.accounts;

import java.util.List;

/**
 * A player-owned house: where it is (a varbit) and its facilities, which the plugin takes from its
 * settings rather than the client.
 */
public final class Poh {
    /** In the order of the {@code POH_HOUSE_LOCATION} varbit's values, starting at 1. */
    public enum Location { RIMMINGTON, TAVERLEY, POLLNIVNEACH, RELLEKKA, BRIMHAVEN, YANILLE, PRIFDDINAS, HOSIDIUS, ALDARIN }

    public enum JewelleryBox { NONE, FANCY, ORNATE }

    public final Location location;
    public final boolean fairyRing;
    public final boolean spiritTree;
    public final boolean obelisk;
    public final JewelleryBox jewelleryBox;
    public final boolean mountedGlory;
    public final boolean mountedXerics;
    public final boolean mountedDigsite;
    public final boolean mountedMythical;
    /** Nexus portal destinations by plugin display name; {@code null} means every portal. */
    public final List<String> portals;

    public Poh(Location location, boolean fairyRing, boolean spiritTree, boolean obelisk, JewelleryBox jewelleryBox,
            boolean mountedGlory, boolean mountedXerics, boolean mountedDigsite, boolean mountedMythical,
            List<String> portals) {
        this.location = location;
        this.fairyRing = fairyRing;
        this.spiritTree = spiritTree;
        this.obelisk = obelisk;
        this.jewelleryBox = jewelleryBox;
        this.mountedGlory = mountedGlory;
        this.mountedXerics = mountedXerics;
        this.mountedDigsite = mountedDigsite;
        this.mountedMythical = mountedMythical;
        this.portals = portals == null ? null : List.copyOf(portals);
    }
}
