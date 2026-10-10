package shortestpath.routeapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
import shortestpath.TeleportationItem;
import shortestpath.accounts.Account;
import shortestpath.profiles.PluginSettings;

public class RoutePoliciesTest {
    private static final Account.Poh POH = AccountBuilds.toAccount(Fixtures.profile("mid")).poh();

    private static PluginSettings settings(RouteApi.RoutePolicy policy) {
        return RoutePolicies.toSettings(policy, POH);
    }

    @Test
    public void minimalPoliciesKeepTheirPreviousBehaviour() {
        PluginSettings settings = settings(Fixtures.policy());

        assertEquals(TeleportationItem.INVENTORY_AND_BANK, settings.useTeleportationItems());
        assertTrue(settings.includeBankPath());
        assertEquals(Integer.MAX_VALUE, settings.currencyThreshold());
        assertEquals(0, settings.costBoats());
        assertEquals(0, settings.costBankVisit());
        assertEquals(0, settings.costConsumableTeleportationItems());
        assertFalse(settings.unlockCanoeAxe());
        assertFalse(settings.respawnPrifddinas());
        assertFalse(settings.isBypassVarbitChecks());
        assertFalse(settings.isBypassVarPlayerChecks());
    }

    @Test
    public void teleportItemModeFollowsBankingResourcesAndItemSource() {
        RouteApi.RoutePolicy policy = Fixtures.policy();
        policy.banking = "never";
        assertEquals(TeleportationItem.INVENTORY, settings(policy).useTeleportationItems());
        assertFalse(settings(policy).includeBankPath());

        policy.resources = "permanent-only";
        assertEquals(TeleportationItem.INVENTORY_NON_CONSUMABLE, settings(policy).useTeleportationItems());

        policy.banking = "avoid";
        assertEquals(TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE, settings(policy).useTeleportationItems());
        assertEquals(RoutePolicies.AVOID_THRESHOLD, settings(policy).costBankVisit());

        policy.teleportItems = "any";
        assertEquals(TeleportationItem.ALL_NON_CONSUMABLE, settings(policy).useTeleportationItems());
        policy.resources = "preserve-consumables";
        assertEquals(TeleportationItem.ALL, settings(policy).useTeleportationItems());
        assertEquals(RoutePolicies.AVOID_THRESHOLD, settings(policy).costConsumableTeleportationItems());

        policy.avoidedTransportTypes = List.of("TELEPORTATION_ITEM");
        assertEquals(TeleportationItem.NONE, settings(policy).useTeleportationItems());
    }

    @Test
    public void thresholdsUnlocksAndCurrencyReachThePluginSettings() {
        RouteApi.RoutePolicy policy = Fixtures.policy();
        policy.transportThresholds.put("FAIRY_RING", 12);
        policy.transportThresholds.put("TELEPORTATION_BOX", 3);
        policy.currencyThreshold = 5000;
        policy.declaredUnlocks = List.of("CANOE_AXE", "PRIFDDINAS_RESPAWN");
        policy.avoidedTransportTypes = List.of("QUETZAL", "CANOE");

        PluginSettings settings = settings(policy);
        assertEquals(12, settings.costFairyRings());
        assertEquals(3, settings.costTeleportationBoxes());
        assertEquals(0, settings.costSpiritTrees());
        assertEquals(5000, settings.currencyThreshold());
        assertTrue(settings.unlockCanoeAxe());
        assertTrue(settings.respawnPrifddinas());
        assertFalse(settings.unlockXericsHonour());
        assertFalse(settings.useQuetzals());
        assertFalse(settings.useCanoes());
        assertTrue(settings.useBoats());
    }
}
