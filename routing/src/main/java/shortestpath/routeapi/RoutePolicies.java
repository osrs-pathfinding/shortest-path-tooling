package shortestpath.routeapi;

import java.util.Set;
import shortestpath.TeleportationItem;
import shortestpath.accounts.Poh;
import shortestpath.profiles.PluginSettings;

/**
 * Compiles a {@code route-policy-v1} document, with the account's POH, into the plugin settings
 * a route is planned with. Settings the policy does not cover keep the plugin's defaults, and
 * transport requirements are never bypassed.
 */
public final class RoutePolicies {
    /** The threshold, in ticks, behind the policy's "avoid" and "preserve" choices. */
    public static final int AVOID_THRESHOLD = 1000;

    /** The plugin's default cutoff, in seconds, for one route calculation. */
    private static final int CALCULATION_CUTOFF = 25;

    private RoutePolicies() { }

    public static PluginSettings toSettings(RouteApi.RoutePolicy policy, Poh poh) {
        Set<String> avoided = Set.copyOf(policy.avoidedTransportTypes);
        Set<String> unlocks = Set.copyOf(policy.declaredUnlocks);
        boolean banks = !"never".equals(policy.banking);

        PluginSettings settings = new PluginSettings();
        settings.setBypassVarbitChecks(false);
        settings.setBypassVarPlayerChecks(false);
        settings.setCalculationCutoff(CALCULATION_CUTOFF);
        settings.setAvoidWilderness(policy.avoidWilderness);
        settings.setIncludeBankPath(banks);
        settings.setCostBankVisit("avoid".equals(policy.banking) ? AVOID_THRESHOLD : 0);
        settings.setCurrencyThreshold(policy.currencyThreshold == null ? Integer.MAX_VALUE : policy.currencyThreshold);
        // Seasonal transports also need a seasonal world, which a public account never has.
        settings.setUseSeasonalTransports(false);

        settings.setUseAgilityShortcuts(!avoided.contains("AGILITY_SHORTCUT"));
        settings.setUseGrappleShortcuts(!avoided.contains("GRAPPLE_SHORTCUT"));
        settings.setUseBoats(!avoided.contains("BOAT"));
        settings.setUseCanoes(!avoided.contains("CANOE"));
        settings.setUseCharterShips(!avoided.contains("CHARTER_SHIP"));
        settings.setUseShips(!avoided.contains("SHIP"));
        settings.setUseFairyRings(!avoided.contains("FAIRY_RING"));
        settings.setUseGnomeGliders(!avoided.contains("GNOME_GLIDER"));
        settings.setUseHotAirBalloons(!avoided.contains("HOT_AIR_BALLOON"));
        settings.setUseMagicCarpets(!avoided.contains("MAGIC_CARPET"));
        settings.setUseMagicMushtrees(!avoided.contains("MAGIC_MUSHTREE"));
        settings.setUseMinecarts(!avoided.contains("MINECART"));
        settings.setUseQuetzals(!avoided.contains("QUETZAL"));
        settings.setUseSpiritTrees(!avoided.contains("SPIRIT_TREE"));
        settings.setUseTeleportationLevers(!avoided.contains("TELEPORTATION_LEVER"));
        settings.setUseTeleportationPortals(!avoided.contains("TELEPORTATION_PORTAL"));
        settings.setUseTeleportationSpells(!avoided.contains("TELEPORTATION_SPELL"));
        settings.setUseTeleportationSpellsHome(!avoided.contains("TELEPORTATION_SPELL_HOME"));
        settings.setUseTeleportationMinigames(!avoided.contains("TELEPORTATION_MINIGAME"));
        settings.setUseWildernessObelisks(!avoided.contains("WILDERNESS_OBELISK"));
        settings.setUseTeleportationItems(avoided.contains("TELEPORTATION_ITEM")
            ? TeleportationItem.NONE : teleportationItems(policy, banks));

        settings.setCostAgilityShortcuts(threshold(policy, "AGILITY_SHORTCUT"));
        settings.setCostGrappleShortcuts(threshold(policy, "GRAPPLE_SHORTCUT"));
        settings.setCostBoats(threshold(policy, "BOAT"));
        settings.setCostCanoes(threshold(policy, "CANOE"));
        settings.setCostCharterShips(threshold(policy, "CHARTER_SHIP"));
        settings.setCostShips(threshold(policy, "SHIP"));
        settings.setCostFairyRings(threshold(policy, "FAIRY_RING"));
        settings.setCostGnomeGliders(threshold(policy, "GNOME_GLIDER"));
        settings.setCostHotAirBalloons(threshold(policy, "HOT_AIR_BALLOON"));
        settings.setCostMagicCarpets(threshold(policy, "MAGIC_CARPET"));
        settings.setCostMagicMushtrees(threshold(policy, "MAGIC_MUSHTREE"));
        settings.setCostMinecarts(threshold(policy, "MINECART"));
        settings.setCostQuetzals(threshold(policy, "QUETZAL"));
        settings.setCostSpiritTrees(threshold(policy, "SPIRIT_TREE"));
        settings.setCostNonConsumableTeleportationItems(threshold(policy, "TELEPORTATION_ITEM"));
        settings.setCostTeleportationBoxes(threshold(policy, "TELEPORTATION_BOX"));
        settings.setCostTeleportationLevers(threshold(policy, "TELEPORTATION_LEVER"));
        settings.setCostTeleportationPortals(threshold(policy, "TELEPORTATION_PORTAL"));
        settings.setCostTeleportationSpells(threshold(policy, "TELEPORTATION_SPELL"));
        settings.setCostTeleportationSpellsHome(threshold(policy, "TELEPORTATION_SPELL_HOME"));
        settings.setCostTeleportationMinigames(threshold(policy, "TELEPORTATION_MINIGAME"));
        settings.setCostWildernessObelisks(threshold(policy, "WILDERNESS_OBELISK"));
        // Consumable teleport items pay only this threshold, and quetzal whistles pay it on top of QUETZAL's.
        settings.setCostConsumableTeleportationItems(
            "preserve-consumables".equals(policy.resources) ? AVOID_THRESHOLD : 0);

        settings.setRespawnPrifddinas(unlocks.contains("PRIFDDINAS_RESPAWN"));
        settings.setUnlockCanoeAxe(unlocks.contains("CANOE_AXE"));
        settings.setUnlockXericsHonour(unlocks.contains("XERICS_HONOUR"));
        settings.setUnlockDragontoothPassage(unlocks.contains("DRAGONTOOTH_PASSAGE"));

        settings.applyPoh(poh, true);
        return settings;
    }

    private static TeleportationItem teleportationItems(RouteApi.RoutePolicy policy, boolean banks) {
        boolean permanent = "permanent-only".equals(policy.resources);
        if ("any".equals(policy.teleportItems)) {
            return permanent ? TeleportationItem.ALL_NON_CONSUMABLE : TeleportationItem.ALL;
        }
        // The bank modes force bank paths on, so banking "never" must use the carried-only modes.
        if (!banks) {
            return permanent ? TeleportationItem.INVENTORY_NON_CONSUMABLE : TeleportationItem.INVENTORY;
        }
        return permanent ? TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE : TeleportationItem.INVENTORY_AND_BANK;
    }

    private static int threshold(RouteApi.RoutePolicy policy, String type) {
        return policy.transportThresholds.getOrDefault(type, 0);
    }
}
