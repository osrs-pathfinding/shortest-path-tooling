package shortestpath.scenarios;

import static shortestpath.profiles.Profiles.UNIT_TEST;
import static shortestpath.scenarios.Overrides.bankTeleports;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import shortestpath.TeleportationItem;

/**
 * Walking and transport routes with exact lengths. Suite {@code routes}; exact lengths in
 * {@code scenarios/expected-lengths/routes.json}.
 */
final class RouteScenarios {
    private RouteScenarios() { }

    static void define(Suite suite) {
        suite.scenario("Lumbridge → Draynor Village", "walk")
            .from(3222, 3218, 0).to(3105, 3251, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("Varrock → Barbarian Village", "walk")
            .from(3213, 3428, 0).to(3082, 3420, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("Falador → Rimmington", "walk")
            .from(2964, 3378, 0).to(2957, 3214, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("Lumbridge → Varrock", "teleport")
            .from(3222, 3218, 0).to(3213, 3428, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.FIRERUNE, 1)
                .inventory(ItemID.AIRRUNE, 3)
                .inventory(ItemID.LAWRUNE, 1)
                .level(Skill.MAGIC, 25))
            .settings(s -> {
                s.setUseTeleportationSpells(true);
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
            });

        suite.scenario("Lumbridge → Ardougne", "teleport")
            .from(3222, 3218, 0).to(2662, 3305, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.WATERRUNE, 2)
                .inventory(ItemID.LAWRUNE, 2)
                .level(Skill.MAGIC, 51))
            .settings(s -> {
                s.setUseTeleportationSpells(true);
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
            });

        suite.scenario("Edgeville → Legends Guild", "fairy-ring")
            .from(3087, 3496, 0).to(2729, 3348, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.DRAMEN_STAFF, 1))
            .settings(s -> s.setUseFairyRings(true));

        suite.scenario("GE → Canifis", "fairy-ring")
            .from(3165, 3487, 0).to(3507, 3496, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.DRAMEN_STAFF, 1))
            .settings(s -> s.setUseFairyRings(true));

        suite.scenario("Varrock → Gnome Stronghold", "spirit-tree")
            .from(3213, 3428, 0).to(2461, 3382, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseSpiritTrees(true));

        suite.scenario("Gnome Stronghold → Digsite", "gnome-glider")
            .from(2461, 3382, 0).to(3370, 3425, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseGnomeGliders(true));

        suite.scenario("Port Sarim → Musa Point", "ship")
            .from(3038, 3192, 0).to(2956, 3146, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.COINS, 10000))
            .settings(s -> s.setUseShips(true));

        suite.scenario("Rellekka → Waterbirth Island", "boat")
            .from(2660, 3657, 0).to(2544, 3759, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.COINS, 10000)
                .inventory(ItemID.ECTOTOKEN, 25))
            .settings(s -> s.setUseBoats(true));

        suite.scenario("Port Phasmatys → Mos Le'Harmless", "ship")
            .from(3702, 3503, 0).to(3684, 2953, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseShips(true));

        suite.scenario("Brimhaven → Port Khazard", "charter-ship")
            .from(2760, 3178, 0).to(2661, 3162, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.COINS, 100000))
            .settings(s -> s.setUseCharterShips(true));

        suite.scenario("Lumbridge → Edgeville", "canoe")
            .from(3222, 3218, 0).to(3087, 3496, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.BRONZE_AXE, 1))
            .settings(s -> {
                s.setUseCanoes(true);
                s.setUseTeleportationSpells(false);
                s.setUseTeleportationSpellsHome(false);
            });

        suite.scenario("Shantay Pass → Pollnivneach", "magic-carpet")
            .from(3304, 3124, 0).to(3360, 2970, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.COINS, 200))
            .settings(s -> s.setUseMagicCarpets(true));

        suite.scenario("GE → Keldagrim", "minecart")
            .from(3165, 3487, 0).to(2909, 10174, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.COINS, 1000))
            .settings(s -> s.setUseMinecarts(true));

        suite.scenario("Varrock → Civitas illa Fortis", "quetzal")
            .from(3213, 3428, 0).to(1700, 3141, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseQuetzals(true));

        suite.scenario("Edgeville → Mage Arena", "wilderness")
            .from(3087, 3496, 0).to(3105, 3951, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varplayer(VarPlayerID.MAGEARENA, 1)
                .inventory(ItemID.KNIFE, 1))
            .settings(s -> {
                s.setAvoidWilderness(false);
                s.setBypassVarPlayerChecks(false);
            });

        suite.scenario("Edgeville → Mage Arena (miniquest not started)", "wilderness")
            .from(3087, 3496, 0).to(3105, 3951, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varplayer(VarPlayerID.MAGEARENA, 0)
                .inventory(ItemID.KNIFE, 1))
            .settings(s -> {
                s.setAvoidWilderness(false);
                s.setBypassVarPlayerChecks(false);
            })
            .expectUnreachable();

        suite.scenario("Deep Wilderness → GE", "wilderness")
            .from(3340, 3828, 0).to(3158, 3509, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.AMULET_OF_GLORY_6, 1))
            .settings(s -> {
                s.setAvoidWilderness(false);
                s.setUseAgilityShortcuts(true);
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
            });

        suite.scenario("Falador → White Knight 2F", "multi-plane")
            .from(2964, 3378, 0).to(2961, 3339, 2)
            .profile(UNIT_TEST);

        suite.scenario("Castle Wars → GE", "cross-map")
            .from(2442, 3096, 0).to(3162, 3489, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.DRAMEN_STAFF, 1))
            .settings(s -> s.setUseFairyRings(true));

        suite.scenario("Port Sarim → Land's End", "cross-map")
            .from(3038, 3192, 0).to(1496, 3403, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.DRAMEN_STAFF, 1))
            .settings(s -> {
                s.setUseFairyRings(true);
                s.setUseSpiritTrees(true);
            });

        suite.scenario("GE → Zul-Andra", "cross-map")
            .from(3165, 3487, 0).to(2213, 3099, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.DRAMEN_STAFF, 1))
            .settings(s -> s.setUseFairyRings(true));

        suite.scenario("Al Kharid → Hosidius", "cross-map")
            .from(3298, 3290, 0).to(1750, 3590, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.DRAMEN_STAFF, 1))
            .settings(s -> {
                s.setUseFairyRings(true);
                s.setUseSpiritTrees(true);
            });

        suite.scenario("Ardougne → Deep Wilderness", "teleport-lever")
            .from(2562, 3311, 0).to(3154, 3924, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseTeleportationLevers(true));

        suite.scenario("Lumbridge → Varrock (bank teleport)", "teleport")
            .from(3222, 3218, 0).to(3213, 3428, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .bank(ItemID.FIRERUNE, 1)
                .bank(ItemID.AIRRUNE, 3)
                .bank(ItemID.LAWRUNE, 1)
                .level(Skill.MAGIC, 25))
            .settings(bankTeleports())
            .settings(s -> s.setUseTeleportationSpells(true));

        suite.scenario("Lumbridge → Ardougne (bank teleport)", "teleport")
            .from(3222, 3218, 0).to(2662, 3305, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .bank(ItemID.WATERRUNE, 2)
                .bank(ItemID.LAWRUNE, 2)
                .level(Skill.MAGIC, 51))
            .settings(bankTeleports())
            .settings(s -> s.setUseTeleportationSpells(true));

        suite.scenario("GE → Yu'Biusk (bank fairy-ring)", "fairy-ring")
            .from(3165, 3487, 0).to(3572, 4372, 0)
            .profile(UNIT_TEST)
            .account(a -> a.bank(ItemID.DRAMEN_STAFF, 1))
            .settings(bankTeleports())
            .settings(s -> s.setUseFairyRings(true));

        suite.scenario("Auburnvale → Ferox Enclave", "cross-map")
            .from(1411, 3361, 0).to(3134, 3629, 0)
            .profile(UNIT_TEST)
            .account(a -> a.bank(ItemID.DRAMEN_STAFF, 1))
            .settings(bankTeleports())
            .settings(s -> {
                s.setUseFairyRings(true);
                s.setUseSpiritTrees(true);
            });
    }
}
