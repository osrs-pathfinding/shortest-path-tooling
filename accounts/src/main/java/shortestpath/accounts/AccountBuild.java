package shortestpath.accounts;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An account as {@code corpus/schemas/account-build-v1} describes it: semantic state (levels,
 * quests, diaries, items, POH, spellbook) plus compatibility {@code routingVariables}. The web
 * planner edits these, the canonical profiles are written as these, and {@link AccountBuilds}
 * compiles one into an {@link Account}. Plain fields, so Jackson binds it directly.
 */
public final class AccountBuild {
    public int schemaVersion;
    public String id;
    public String name;
    public long benchmarkNowMinutes;
    public Map<String, Integer> levels = new LinkedHashMap<>();
    public List<String> completedQuests = new ArrayList<>();
    public Map<String, String> diaries = new LinkedHashMap<>();
    public Map<String, Integer> inventory = new LinkedHashMap<>();
    public Map<String, Integer> equipment = new LinkedHashMap<>();
    public Map<String, Integer> runePouch = new LinkedHashMap<>();
    public Map<String, Integer> bank = new LinkedHashMap<>();
    public boolean fairyRingsUnlocked;
    public List<String> plantedSpiritTrees = new ArrayList<>();
    public Poh poh;
    public RuntimeState runtime;
    public RoutingVariables routingVariables;

    public static final class Poh {
        public String location;
        public String jewelleryBox;
        public Portals portals;
        public boolean fairyRing;
        public boolean spiritTree;
        public boolean obelisk;
        public boolean mountedGlory;
        public boolean mountedXerics;
        public boolean mountedDigsite;
        public boolean mountedMythical;
    }

    public static final class Portals {
        public String mode;
        public List<String> destinations = new ArrayList<>();
    }

    public static final class RuntimeState {
        public boolean arriveInsidePoh;
        public String spellbook;
        public MinigameTeleport minigameTeleport;
    }

    /** {@code minutes} is set only when the state is {@code usedAt}; the schema has no null. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class MinigameTeleport {
        public String state;
        public Long minutes;
    }

    public static final class RoutingVariables {
        public Map<Integer, Integer> varbits = new LinkedHashMap<>();
        public Map<Integer, Integer> varplayers = new LinkedHashMap<>();
    }
}
