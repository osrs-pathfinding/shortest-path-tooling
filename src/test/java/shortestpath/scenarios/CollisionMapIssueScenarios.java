package shortestpath.scenarios;

import static shortestpath.profiles.Profiles.UNIT_TEST;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import shortestpath.TeleportationItem;

/**
 * Regression routes for upstream collision-map issues; the category names the issue. Suite {@code collision-map-issues}; exact lengths in
 * {@code scenarios/expected-lengths/collision-map-issues.json}.
 */
final class CollisionMapIssueScenarios {
    private CollisionMapIssueScenarios() { }

    static void define(Suite suite) {
        suite.scenario("Wizards' Guild -> Edgeville (avoid wilderness)", "wilderness")
            .from(2485, 3080, 0).to(3087, 3492, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.BURNING_AMULET_5, 1)
                .level(Skill.MAGIC, 99))
            .settings(s -> {
                s.setAvoidWilderness(true);
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
            });

        suite.scenario("Duel Arena (#339) drop-off cross wall north", "collision-issue-339")
            .from(3391, 3251, 0).to(3393, 3253, 0)
            .profile(UNIT_TEST)
            .settings(s -> {
                s.setUseAgilityShortcuts(false);
                s.setUseTeleportationItems(TeleportationItem.NONE);
            });

        suite.scenario("Death Plateau (#270) descent top of climb rocks -> Burthorpe", "collision-issue-270")
            .from(2856, 3613, 0).to(2899, 3543, 0)
            .profile(UNIT_TEST)
            .settings(s -> {
                s.setUseAgilityShortcuts(true);
                s.setUseTeleportationItems(TeleportationItem.NONE);
            });

        suite.scenario("Death Plateau (#270) ascent Burthorpe -> top of climb rocks", "collision-issue-270")
            .from(2899, 3543, 0).to(2856, 3613, 0)
            .profile(UNIT_TEST)
            .account(a -> a.equipment(ItemID.DEATH_CLIMBINGBOOTS, 1))
            .settings(s -> {
                s.setUseAgilityShortcuts(true);
                s.setUseTeleportationItems(TeleportationItem.NONE);
            });

        suite.scenario("Death Plateau (#270) climb-up rocks south->north", "collision-issue-270")
            .from(2856, 3611, 0).to(2856, 3613, 0)
            .profile(UNIT_TEST)
            .account(a -> a.equipment(ItemID.DEATH_CLIMBINGBOOTS, 1))
            .settings(s -> {
                s.setUseAgilityShortcuts(true);
                s.setUseTeleportationItems(TeleportationItem.NONE);
            });

        suite.scenario("Death Plateau (#270) climb-down rocks north->south [control]", "collision-issue-270")
            .from(2856, 3613, 0).to(2856, 3611, 0)
            .profile(UNIT_TEST)
            .settings(s -> {
                s.setUseAgilityShortcuts(true);
                s.setUseTeleportationItems(TeleportationItem.NONE);
            });

        suite.scenario("Auburnvale (#193) bridge cross south -> north", "collision-issue-193")
            .from(1435, 3335, 0).to(1437, 3358, 0)
            .profile(UNIT_TEST)
            .settings(s -> {
                s.setUseAgilityShortcuts(false);
                s.setUseTeleportationItems(TeleportationItem.NONE);
            });

        suite.scenario("Auburnvale (#193) bridge cross north -> south", "collision-issue-193")
            .from(1437, 3358, 0).to(1435, 3335, 0)
            .profile(UNIT_TEST)
            .settings(s -> {
                s.setUseAgilityShortcuts(false);
                s.setUseTeleportationItems(TeleportationItem.NONE);
            });

        suite.scenario("Ancient Cavern (#374) pier -> inside cavern via whirlpool", "collision-issue-374")
            .from(2510, 3511, 0).to(1768, 5366, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.FIREMAKING, 35))
            .settings(s -> {
                s.setUseAgilityShortcuts(false);
                s.setUseTeleportationItems(TeleportationItem.NONE);
            });

        suite.scenario("Ancient Cavern (#374) approach pier from north", "collision-issue-374")
            .from(2509, 3520, 0).to(2511, 3511, 0)
            .profile(UNIT_TEST)
            .settings(s -> {
                s.setUseAgilityShortcuts(false);
                s.setUseTeleportationItems(TeleportationItem.NONE);
            });

        suite.scenario("Fossil Island Hunter trail (#233) along trail", "collision-issue-233")
            .from(3700, 3870, 0).to(3705, 3878, 0)
            .profile(UNIT_TEST)
            .settings(s -> {
                s.setUseAgilityShortcuts(true);
                s.setUseTeleportationItems(TeleportationItem.NONE);
            });

        suite.scenario("Fossil Island Hunter trail (#233) cross trail", "collision-issue-233")
            .from(3700, 3880, 0).to(3700, 3868, 0)
            .profile(UNIT_TEST)
            .settings(s -> {
                s.setUseAgilityShortcuts(true);
                s.setUseTeleportationItems(TeleportationItem.NONE);
            });

        suite.scenario("Varrock palace trellis (control)", "collision-control")
            .from(3220, 3471, 0).to(3232, 3475, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("GE centre walk (control)", "collision-control")
            .from(3160, 3486, 0).to(3170, 3492, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("Ourania Cave entrance (#621) rim -> Chaos altar dig spot", "collision-issue-621")
            .from(2454, 3234, 0).to(2454, 3230, 0)
            .profile(UNIT_TEST)
            .settings(s -> {
                s.setUseAgilityShortcuts(false);
                s.setUseTeleportationItems(TeleportationItem.NONE);
            });
    }
}
