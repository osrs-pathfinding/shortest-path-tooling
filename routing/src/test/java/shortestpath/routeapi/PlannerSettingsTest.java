package shortestpath.routeapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import shortestpath.TeleportationItem;
import shortestpath.pathfinder.PathfinderBackend;
import shortestpath.profiles.PluginSettings;

public class PlannerSettingsTest {
    @Test
    public void everyRoutingConfigItemIsASettingAndDisplayAndHouseItemsAreNot() {
        Set<String> keys = PlannerSettings.catalog().stream().map(setting -> setting.key).collect(Collectors.toSet());
        assertTrue(keys.containsAll(Set.of("useFairyRings", "costBoats", "useTeleportationItems", "currencyThreshold",
            "pathfinderBackend", "exactHeuristicWeight", "unlockCanoeAxe", "costBankVisit")));
        assertFalse(keys.contains("drawMap"));
        assertFalse(keys.contains("usePohFairyRing"));
        assertFalse(keys.contains("pohJewelleryBoxTier"));
        assertFalse(keys.contains("useSeasonalTransports"));
    }

    @Test
    public void jsonValuesOverrideThePlannerDefaults() {
        PluginSettings defaults = PlannerSettings.fromJson(null);
        assertEquals(PathfinderBackend.EXACT, defaults.pathfinderBackend());
        assertFalse(defaults.isBypassVarbitChecks());
        assertEquals(TeleportationItem.INVENTORY_AND_BANK, defaults.useTeleportationItems());

        PluginSettings settings = PlannerSettings.fromJson(Map.of("useFairyRings", false, "costBoats", 12,
            "useTeleportationItems", "ALL"));
        assertFalse(settings.useFairyRings());
        assertEquals(12, settings.costBoats());
        assertEquals(TeleportationItem.ALL, settings.useTeleportationItems());
    }

    @Test
    public void unknownKeysAndWrongValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PlannerSettings.fromJson(Map.of("drawMap", true)));
        assertThrows(IllegalArgumentException.class, () -> PlannerSettings.fromJson(Map.of("costBoats", "ten")));
        assertThrows(IllegalArgumentException.class,
            () -> PlannerSettings.fromJson(Map.of("useTeleportationItems", "EVERYTHING")));
    }
}
