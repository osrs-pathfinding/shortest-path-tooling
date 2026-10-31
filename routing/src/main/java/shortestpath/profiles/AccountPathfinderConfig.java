package shortestpath.profiles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import shortestpath.accounts.Boats;
import shortestpath.accounts.ClientState;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PluginResources;
import shortestpath.pathfinder.sailing.SailingFleet;
import shortestpath.pathfinder.sailing.SailingVessel;
import shortestpath.requirement.model.VarRequirement;
import shortestpath.sailing.BoatSpeed;
import shortestpath.sailing.SeaArea;
import shortestpath.transport.Transport;

/**
 * A {@link PathfinderConfig} for an account's {@link ClientState}, on the shared {@link PluginResources}.
 * It answers quest states and the clock from the state, and skips varbit or varplayer transport
 * requirements when the settings bypass them. Quest states are answered here rather than by the
 * client because {@code getQuestState} runs in the search loop.
 */
public final class AccountPathfinderConfig extends PathfinderConfig {
    private final ClientState state;
    private final boolean bypassVarbitChecks;
    private final boolean bypassVarPlayerChecks;

    AccountPathfinderConfig(Client client, PluginSettings settings, ClientState state) {
        super(client, settings, PluginResources.map(), PluginResources.transports(),
            PluginResources.destinations(), PluginResources.filteredDestinations(),
            PluginResources.bankRequirements());
        this.state = state;
        this.bypassVarbitChecks = settings.isBypassVarbitChecks();
        this.bypassVarPlayerChecks = settings.isBypassVarPlayerChecks();
    }

    @Override
    public QuestState getQuestState(Quest quest) {
        return state.questState(quest);
    }

    // The gate chain reads var requirements through the RequirementHooks
    // Collection/Map overloads, not the single-transport convenience path, so
    // every overload must carry the bypass or it silently never applies.
    @Override
    public boolean varbitChecks(Transport transport, long evaluationTimeMinutes) {
        return !bypassVarbitChecks && super.varbitChecks(transport, evaluationTimeMinutes);
    }

    @Override
    public boolean varbitChecks(Collection<VarRequirement> requirements, long evaluationTimeMinutes) {
        return !bypassVarbitChecks && super.varbitChecks(requirements, evaluationTimeMinutes);
    }

    @Override
    public boolean varbitChecks(Collection<VarRequirement> requirements,
            Map<Integer, Integer> values, long evaluationTimeMinutes) {
        return !bypassVarbitChecks && super.varbitChecks(requirements, values, evaluationTimeMinutes);
    }

    @Override
    public boolean varPlayerChecks(Transport transport, long evaluationTimeMinutes) {
        return !bypassVarPlayerChecks && super.varPlayerChecks(transport, evaluationTimeMinutes);
    }

    @Override
    public boolean varPlayerChecks(Collection<VarRequirement> requirements, long evaluationTimeMinutes) {
        return !bypassVarPlayerChecks && super.varPlayerChecks(requirements, evaluationTimeMinutes);
    }

    @Override
    public boolean varPlayerChecks(Collection<VarRequirement> requirements,
            Map<Integer, Integer> values, long evaluationTimeMinutes) {
        return !bypassVarPlayerChecks && super.varPlayerChecks(requirements, values, evaluationTimeMinutes);
    }

    /**
     * The account's declared boats ({@link Boats}): each owned boat, at the declared speed, boardable wherever the
     * account says it is moored, with the parts it says they are fitted with, and the one the player is aboard. Never derived from the Sailing level,
     * though the parts may be declared as the best the account's levels can build.
     */
    @Override
    protected SailingFleet sailingFleet() {
        Boats boats = state.boats();
        // Heading and where the boat sits in its tile are unknown
        SailingFleet.Aboard aboard = boats.aboard() == null ? null : new SailingFleet.Aboard(boats.aboardTile(), -1,
            vessel(boats.aboard(), boats), 0, 0);
        // Benchmark harness switch: -Ddashboard.boats=false runs every account on foot
        if (boats.owned().isEmpty() || !Boolean.parseBoolean(System.getProperty("dashboard.boats", "true"))) {
            return aboard == null ? SailingFleet.NONE : SailingFleet.aboard(aboard);
        }
        List<SailingFleet.Boat> owned = new ArrayList<>();
        for (Boats.Kind kind : boats.owned()) {
            SailingVessel vessel = vessel(kind, boats);
            owned.add(boats.moored() == Boats.Moored.EVERY_MOORING ? SailingFleet.Boat.everywhere(vessel)
                : SailingFleet.Boat.at(vessel, -1, SailingFleet.UNAVAILABLE));
        }
        return new SailingFleet(owned, aboard, Integer.MAX_VALUE);
    }

    // A boat of kind at the boats' declared speed, in the game's 128ths of a tile per tick, with their parts
    private SailingVessel vessel(Boats.Kind kind, Boats boats) {
        return SailingVessel.of(SailingVessel.Kind.valueOf(kind.name()), (int) Math.round(boats.speed() * BoatSpeed.UNITS))
            .withAreas(areas(boats));
    }

    // The restricted sea areas the boats' parts let them enter
    private Set<SeaArea> areas(Boats boats) {
        switch (boats.fitted()) {
            case ALL:
                return SeaArea.ALL;
            case BEST_FOR_LEVELS:
                return buildable(state.level(Skill.SAILING), state.level(Skill.CONSTRUCTION));
            default:
                return SeaArea.NONE;
        }
    }

    // The Sailing and Construction levels each area's part is built at, from the OSRS Wiki's Sailing hazards
    private static final Map<SeaArea, int[]> PART_LEVELS = Map.of(
        SeaArea.TEMPOR_TANTRUM, new int[] {24, 11},
        SeaArea.FETID, new int[] {40, 37},
        SeaArea.CRYSTALLINE, new int[] {66, 62},
        SeaArea.KELP, new int[] {72, 59},
        SeaArea.NORTHERN, new int[] {78, 72});

    // The areas whose parts a player at these levels can build: the best fitting a boat can have
    private static Set<SeaArea> buildable(int sailing, int construction) {
        Set<SeaArea> areas = EnumSet.noneOf(SeaArea.class);
        PART_LEVELS.forEach((area, levels) -> {
            if (sailing >= levels[0] && construction >= levels[1]) areas.add(area);
        });
        return areas;
    }

    @Override
    protected long currentTimeMinutes() {
        return state.nowMinutes() == null ? super.currentTimeMinutes() : state.nowMinutes();
    }

    /** The game time timed requirements are evaluated at. */
    public long evaluationTimeMinutes() {
        return currentTimeMinutes();
    }
}
