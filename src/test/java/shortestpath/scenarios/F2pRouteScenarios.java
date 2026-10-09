package shortestpath.scenarios;

import static shortestpath.profiles.Profiles.UNIT_TEST;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;

/**
 * Free-to-play routes. Suite {@code f2p_routes}; exact lengths in
 * {@code scenarios/expected-lengths/f2p_routes.json}.
 */
final class F2pRouteScenarios {
    private F2pRouteScenarios() { }

    static void define(Suite suite) {
        // --- Walking routes between F2P landmarks ---
        suite.scenario("Lumbridge → Draynor Village", "walk")
            .from(3222, 3218, 0).to(3092, 3243, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("Lumbridge → Varrock", "walk")
            .from(3222, 3218, 0).to(3183, 3440, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("Varrock → Falador", "walk")
            .from(3183, 3440, 0).to(2964, 3378, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("Falador → Port Sarim", "walk")
            .from(2964, 3378, 0).to(3027, 3222, 0)
            .profile(UNIT_TEST);

        suite.scenario("Lumbridge → Al Kharid", "walk")
            .from(3222, 3218, 0).to(3269, 3167, 0)
            .profile(UNIT_TEST);

        suite.scenario("Falador → Rimmington", "walk")
            .from(2964, 3378, 0).to(2957, 3214, 0)
            .profile(UNIT_TEST);

        suite.scenario("Edgeville → Varrock", "walk")
            .from(3087, 3496, 0).to(3183, 3440, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("Falador → Goblin Village", "walk")
            .from(2964, 3378, 0).to(2956, 3506, 0)
            .profile(UNIT_TEST);

        suite.scenario("Edgeville → Barbarian Village", "walk")
            .from(3087, 3496, 0).to(3082, 3420, 0)
            .profile(UNIT_TEST);

        suite.scenario("Varrock → Wizards Tower", "walk")
            .from(3183, 3440, 0).to(3104, 3162, 0)
            .profile(UNIT_TEST);

        suite.scenario("Port Sarim → Mudskipper Point", "walk")
            .from(3027, 3222, 0).to(2989, 3111, 0)
            .profile(UNIT_TEST);

        suite.scenario("Lumbridge → Crafting Guild", "walk")
            .from(3222, 3218, 0).to(2934, 3290, 0)
            .profile(UNIT_TEST);

        suite.scenario("Falador → Ice Mountain", "walk")
            .from(2964, 3378, 0).to(3007, 3474, 0)
            .profile(UNIT_TEST);

        // --- Teleportation spells (standard spells; note: type-level F2P filtering may be sub-optimal) ---
        suite.scenario("Edgeville → Lumbridge (Home Teleport)", "teleport")
            .from(3087, 3496, 0).to(3222, 3218, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseTeleportationSpells(true));

        suite.scenario("Lumbridge → Varrock (Varrock Teleport)", "teleport")
            .from(3222, 3218, 0).to(3213, 3424, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.AIRRUNE, 3)
                .inventory(ItemID.FIRERUNE, 1)
                .inventory(ItemID.LAWRUNE, 1)
                .level(Skill.MAGIC, 25))
            .settings(s -> s.setUseTeleportationSpells(true));

        suite.scenario("Varrock → Falador (Falador Teleport)", "teleport")
            .from(3213, 3424, 0).to(2964, 3378, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.WATERRUNE, 1)
                .inventory(ItemID.AIRRUNE, 3)
                .inventory(ItemID.LAWRUNE, 1)
                .level(Skill.MAGIC, 37))
            .settings(s -> s.setUseTeleportationSpells(true));

        // --- Canoes (River Lum) ---
        suite.scenario("Lumbridge → Edgeville (canoe)", "canoe")
            .from(3222, 3218, 0).to(3087, 3496, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.BRONZE_AXE, 1)
                .level(Skill.WOODCUTTING, 42))
            .settings(s -> s.setUseCanoes(true));

        suite.scenario("Lumbridge → Barbarian Village (canoe)", "canoe")
            .from(3222, 3218, 0).to(3082, 3420, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.BRONZE_AXE, 1)
                .level(Skill.WOODCUTTING, 12))
            .settings(s -> s.setUseCanoes(true));

        suite.scenario("Edgeville → Ferox Enclave (canoe)", "canoe")
            .from(3087, 3496, 0).to(3128, 3631, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.BRONZE_AXE, 1)
                .level(Skill.WOODCUTTING, 57))
            .settings(s -> s.setUseCanoes(true));

        // --- Ships ---
        suite.scenario("Port Sarim → Musa Point", "ship")
            .from(3027, 3222, 0).to(2956, 3146, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.COINS, 10000))
            .settings(s -> s.setUseShips(true));

        // --- More walking routes ---
        suite.scenario("Port Sarim → Draynor Village", "walk")
            .from(3027, 3222, 0).to(3092, 3243, 0)
            .profile(UNIT_TEST);

        suite.scenario("Goblin Village → Barbarian Village", "walk")
            .from(2956, 3506, 0).to(3082, 3420, 0)
            .profile(UNIT_TEST);

        // --- Failure routes (members-only areas; expected to produce no F2P path) ---
        suite.scenario("Falador → Castle Wars", "fail")
            .from(2964, 3378, 0).to(2442, 3093, 0)
            .profile(UNIT_TEST);

        suite.scenario("Varrock → Ardougne", "fail")
            .from(3183, 3440, 0).to(2662, 3305, 0)
            .profile(UNIT_TEST);

        suite.scenario("Port Sarim → Karamja Interior", "fail")
            .from(3027, 3222, 0).to(2823, 3150, 0)
            .profile(UNIT_TEST);

        // --- Wilderness (F2P accessible up to level 47-48 fence) ---
        suite.scenario("Edgeville → Ferox Enclave (walk)", "wilderness")
            .from(3087, 3496, 0).to(3128, 3631, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setAvoidWilderness(false));

        suite.scenario("Edgeville → Hobgoblin Mine", "wilderness")
            .from(3087, 3496, 0).to(3300, 3820, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setAvoidWilderness(false));
    }
}
