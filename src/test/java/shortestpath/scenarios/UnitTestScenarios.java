package shortestpath.scenarios;

import static shortestpath.profiles.Profiles.UNIT_TEST;
import static shortestpath.scenarios.Scenario.scenario;

import java.util.List;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.TeleportationItem;

/**
 * Routes from the plugin's PathfinderTest, on its UNIT_TEST baseline. Suite {@code unit-tests}; exact lengths in
 * {@code scenarios/expected-lengths/unit-tests.json}.
 */
final class UnitTestScenarios {
    private UnitTestScenarios() { }

    static List<Scenario.Builder> all() {
        return List.of(
            scenario("Banked mith grapple should not leak to non-bank grapple branch", "agility-grapple")
                .from(3025, 3365, 0).to(3026, 3393, 0)
                .profile(UNIT_TEST)
                .account(a -> a
                    .inventory(ItemID.XBOWS_CROSSBOW_ADAMANTITE, 1)
                    .bank(ItemID.XBOWS_GRAPPLE_TIP_BOLT_MITHRIL_ROPE, 1))
                .settings(s -> {
                    s.setUseGrappleShortcuts(true);
                    s.setUseAgilityShortcuts(true);
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK);
                    s.setIncludeBankPath(true);
                }),
            scenario("Catherby charter tile reuse -> bank -> Musa Point with banked coins", "charter")
                .from(2792, 3414, 0).to(2954, 3158, 0)
                .profile(UNIT_TEST)
                .account(a -> a.bank(ItemID.COINS, 10000))
                .settings(s -> {
                    s.setUseCharterShips(true);
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK);
                    s.setIncludeBankPath(true);
                }),
            scenario("Catherby bank branch should not leak coins to charter branch", "charter")
                .from(2807, 3435, 0).to(2954, 3158, 0)
                .profile(UNIT_TEST)
                .account(a -> a.bank(ItemID.COINS, 10000))
                .settings(s -> {
                    s.setUseCharterShips(true);
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK);
                    s.setIncludeBankPath(true);
                }),
            scenario("Castle Wars -> AKQ with staff in inventory", "fairy-ring")
                .from(2442, 3083, 0).to(2324, 3619, 0)
                .profile(UNIT_TEST)
                .account(a -> a
                    .varbit(VarbitID.FAIRY2_QUEENCURE_QUEST, 100)
                    .varbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 0)
                    .inventory(ItemID.DRAMEN_STAFF, 1))
                .settings(s -> {
                    s.setUseFairyRings(true);
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK);
                    s.setCostConsumableTeleportationItems(50);
                }),
            scenario("Castle Wars -> AKQ with staff in bank", "fairy-ring")
                .from(2442, 3083, 0).to(2324, 3619, 0)
                .profile(UNIT_TEST)
                .account(a -> a
                    .varbit(VarbitID.FAIRY2_QUEENCURE_QUEST, 100)
                    .varbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 0)
                    .bank(ItemID.DRAMEN_STAFF, 1)
                    .bank(ItemID.ARDY_CAPE_MEDIUM, 1)
                    .bank(ItemID.NECKLACE_OF_PASSAGE_5, 1))
                .settings(s -> {
                    s.setUseFairyRings(true);
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK);
                    s.setIncludeBankPath(true);
                    s.setCostConsumableTeleportationItems(50);
                }),
            scenario("Castle Wars -> AKQ with cloak inventory/staff bank", "fairy-ring")
                .from(2442, 3083, 0).to(2319, 3619, 0)
                .profile(UNIT_TEST)
                .account(a -> a
                    .varbit(VarbitID.FAIRY2_QUEENCURE_QUEST, 100)
                    .varbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 0)
                    .inventory(ItemID.ARDY_CAPE_ELITE, 1)
                    .bank(ItemID.DRAMEN_STAFF, 1)
                    .bank(ItemID.NECKLACE_OF_PASSAGE_5, 1))
                .settings(s -> {
                    s.setUseFairyRings(true);
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK);
                    s.setIncludeBankPath(true);
                    s.setCostConsumableTeleportationItems(50);
                }),
            scenario("Fairy ring remains gated before bank visit", "fairy-ring")
                .from(2442, 3096, 0).to(3162, 3489, 0)
                .profile(UNIT_TEST)
                .account(a -> a
                    .varbit(VarbitID.FAIRY2_QUEENCURE_QUEST, 100)
                    .varbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 0)
                    .inventory(ItemID.RING_OF_WEALTH_1, 1)
                    .bank(ItemID.DRAMEN_STAFF, 1))
                .settings(s -> {
                    s.setUseFairyRings(true);
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK);
                    s.setIncludeBankPath(true);
                }),
            scenario("Castle Wars -> Grand Exchange bank with on-hand ring", "fairy-ring")
                .from(2442, 3096, 0).to(3162, 3489, 0)
                .profile(UNIT_TEST)
                .account(a -> a
                    .varbit(VarbitID.FAIRY2_QUEENCURE_QUEST, 100)
                    .varbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 0)
                    .inventory(ItemID.RING_OF_WEALTH_1, 1)
                    .bank(ItemID.DRAMEN_STAFF, 1))
                .settings(s -> {
                    s.setUseFairyRings(true);
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK);
                    s.setIncludeBankPath(true);
                }),
            scenario("Al Kharid mine -> AKQ with staff only in bank", "fairy-ring")
                .from(3298, 3290, 0).to(2319, 3619, 0)
                .profile(UNIT_TEST)
                .account(a -> a
                    .varbit(VarbitID.FAIRY2_QUEENCURE_QUEST, 100)
                    .varbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 0)
                    .bank(ItemID.DRAMEN_STAFF, 1))
                .settings(s -> {
                    s.setUseFairyRings(true);
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK);
                    s.setIncludeBankPath(true);
                }),
            scenario("Great Conch -> McGrubor's Wood with banked staff and bracelet", "fairy-ring")
                .from(3180, 2419, 0).to(2652, 3485, 0)
                .profile(UNIT_TEST)
                .account(a -> a
                    .varbit(VarbitID.FAIRY2_QUEENCURE_QUEST, 100)
                    .varbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 0)
                    .bank(ItemID.DRAMEN_STAFF, 1)
                    .bank(ItemID.JEWL_BRACELET_OF_COMBAT_4, 1))
                .settings(s -> {
                    s.setUseFairyRings(true);
                    s.setUseAgilityShortcuts(true);
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK);
                    s.setIncludeBankPath(true);
                }),
            scenario("Great Conch tile reuse -> McGrubor's Wood with banked Dramen staff", "fairy-ring")
                .from(3181, 2437, 0).to(2652, 3485, 0)
                .profile(UNIT_TEST)
                .account(a -> a
                    .varbit(VarbitID.FAIRY2_QUEENCURE_QUEST, 100)
                    .varbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 0)
                    .bank(ItemID.DRAMEN_STAFF, 1)
                    .bank(ItemID.JEWL_BRACELET_OF_COMBAT_4, 1))
                .settings(s -> {
                    s.setUseFairyRings(true);
                    s.setUseAgilityShortcuts(true);
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK);
                    s.setIncludeBankPath(true);
                }),
            scenario("Great Conch -> McGrubor's Wood with inventory Dramen staff", "fairy-ring")
                .from(3180, 2419, 0).to(2652, 3485, 0)
                .profile(UNIT_TEST)
                .account(a -> a
                    .varbit(VarbitID.FAIRY2_QUEENCURE_QUEST, 100)
                    .varbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 0)
                    .inventory(ItemID.DRAMEN_STAFF, 1))
                .settings(s -> {
                    s.setUseFairyRings(true);
                    s.setUseAgilityShortcuts(true);
                    s.setUseTeleportationItems(TeleportationItem.NONE);
                }),
            scenario("Banked Dramen staff should not leak to non-bank fairy-ring branch", "fairy-ring")
                .from(3134, 3503, 0).to(2652, 3485, 0)
                .profile(UNIT_TEST)
                .account(a -> a
                    .varbit(VarbitID.FAIRY2_QUEENCURE_QUEST, 100)
                    .varbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 0)
                    .bank(ItemID.DRAMEN_STAFF, 1))
                .settings(s -> {
                    s.setUseFairyRings(true);
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK);
                    s.setIncludeBankPath(true);
                }),
            scenario("Varrock centre -> Cowbell amulet destination with amulet in bank", "teleportation-item")
                .from(3213, 3424, 0).to(3259, 3277, 0)
                .profile(UNIT_TEST)
                .account(a -> a.bank(ItemID.COWBELL_AMULET, 1))
                .settings(s -> {
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK);
                    s.setIncludeBankPath(true);
                }),
            scenario("Lovakengj reverse minecart without coins", "minecart")
                .from(1415, 3577, 0).to(1670, 3833, 0)
                .profile(UNIT_TEST)
                .account(a -> a.varbit(VarbitID.LOVAQUEST, 0))
                .settings(s -> {
                    s.setUseMinecarts(true);
                    s.setUseTeleportationItems(TeleportationItem.NONE);
                    s.setBypassVarbitChecks(false);
                })
                .minimumLength(3),
            scenario("Varrock teleport too low magic level", "teleportation-spell")
                .from(3216, 3424, 0).to(3213, 3424, 0)
                .profile(UNIT_TEST)
                .account(a -> a.level(Skill.MAGIC, 1))
                .settings(s -> {
                    s.setUseTeleportationSpells(true);
                    s.setUseTeleportationItems(TeleportationItem.NONE);
                }),
            scenario("Varrock teleport with runes and magic level", "teleportation-spell")
                .from(3223, 3424, 0).to(3213, 3424, 0)
                .profile(UNIT_TEST)
                .account(a -> a
                    .inventory(ItemID.LAWRUNE, 1)
                    .inventory(ItemID.AIRRUNE, 3)
                    .inventory(ItemID.FIRERUNE, 1)
                    .level(Skill.MAGIC, 99))
                .settings(s -> {
                    s.setUseTeleportationSpells(true);
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                }),
            scenario("Draynor Manor stepping stones vs combat bracelet", "agility-shortcut")
                .from(3149, 3363, 0).to(3154, 3363, 0)
                .profile(UNIT_TEST)
                .settings(s -> {
                    s.setUseAgilityShortcuts(true);
                    s.setUseTeleportationItems(TeleportationItem.ALL);
                }),
            scenario("Deep wilderness -> Grand Exchange with no teleports", "wilderness")
                .from(3340, 3828, 0).to(3158, 3509, 0)
                .profile(UNIT_TEST)
                .account(a -> a.level(Skill.MAGIC, 99))
                .settings(s -> {
                    s.setUseAgilityShortcuts(true);
                    s.setUseTeleportationItems(TeleportationItem.NONE);
                }),
            scenario("Deep wilderness -> Grand Exchange with glory", "wilderness")
                .from(3340, 3828, 0).to(3158, 3509, 0)
                .profile(UNIT_TEST)
                .account(a -> a
                    .inventory(ItemID.AMULET_OF_GLORY_6, 1)
                    .level(Skill.MAGIC, 99))
                .settings(s -> {
                    s.setUseAgilityShortcuts(true);
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                }),
            scenario("Deep wilderness -> Grand Exchange with GE Varrock Teleport", "wilderness")
                .from(3340, 3828, 0).to(3158, 3509, 0)
                .profile(UNIT_TEST)
                .account(a -> a
                    .inventory(ItemID.LAWRUNE, 1)
                    .inventory(ItemID.AIRRUNE, 3)
                    .inventory(ItemID.FIRERUNE, 1)
                    .level(Skill.MAGIC, 99))
                .settings(s -> {
                    s.setUseAgilityShortcuts(true);
                    s.setUseTeleportationSpells(true);
                    s.setUseTeleportationItems(TeleportationItem.NONE);
                }),
            scenario("Deep wilderness -> Grand Exchange with glory and GE runes", "wilderness")
                .from(3340, 3828, 0).to(3158, 3509, 0)
                .profile(UNIT_TEST)
                .account(a -> a
                    .inventory(ItemID.AMULET_OF_GLORY_6, 1)
                    .inventory(ItemID.LAWRUNE, 1)
                    .inventory(ItemID.AIRRUNE, 3)
                    .inventory(ItemID.FIRERUNE, 1)
                    .level(Skill.MAGIC, 99))
                .settings(s -> {
                    s.setUseAgilityShortcuts(true);
                    s.setUseTeleportationSpells(true);
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                }),
            scenario("Wizards' Guild -> Edgeville with burning amulet and avoid wilderness", "wilderness")
                .from(2485, 3080, 0).to(3087, 3492, 0)
                .profile(UNIT_TEST)
                .account(a -> a
                    .inventory(ItemID.BURNING_AMULET_5, 1)
                    .level(Skill.MAGIC, 99))
                .settings(s -> {
                    s.setAvoidWilderness(true);
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                }),
            scenario("Wizards' Guild -> Edgeville with no items and wilderness allowed", "wilderness")
                .from(2485, 3080, 0).to(3087, 3492, 0)
                .profile(UNIT_TEST)
                .account(a -> a.level(Skill.MAGIC, 99))
                .settings(s -> {
                    s.setAvoidWilderness(false);
                    s.setUseTeleportationLevers(true);
                    s.setUseTeleportationItems(TeleportationItem.NONE);
                }),
            scenario("Wizards' Guild -> Edgeville with burning amulet and wilderness allowed", "wilderness")
                .from(2485, 3080, 0).to(3087, 3492, 0)
                .profile(UNIT_TEST)
                .account(a -> a
                    .inventory(ItemID.BURNING_AMULET_5, 1)
                    .level(Skill.MAGIC, 99))
                .settings(s -> {
                    s.setAvoidWilderness(false);
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                }),
            scenario("Keldagrim east -> west via other plane", "keldagrim")
                .from(2894, 10199, 0).to(2864, 10199, 0)
                .profile(UNIT_TEST)
                .settings(s -> s.setUseTeleportationItems(TeleportationItem.NONE)),
            scenario("Keldagrim west -> east via other plane", "keldagrim")
                .from(2864, 10199, 0).to(2894, 10199, 0)
                .profile(UNIT_TEST)
                .settings(s -> s.setUseTeleportationItems(TeleportationItem.NONE)),
            scenario("Grand Tree quest gates gnome glider (finished)", "quest-gating")
                .from(3284, 3213, 0).to(2971, 2968, 0)
                .profile(UNIT_TEST)
                .account(a -> a.quest(Quest.THE_GRAND_TREE, QuestState.FINISHED))
                .settings(s -> s.setUseGnomeGliders(true)),
            scenario("Grand Tree quest gates gnome glider (not started)", "quest-gating")
                .from(3284, 3213, 0).to(2971, 2968, 0)
                .profile(UNIT_TEST)
                .account(a -> a.quest(Quest.THE_GRAND_TREE, QuestState.NOT_STARTED))
                .settings(s -> s.setUseGnomeGliders(true))
                .minimumLength(213)
        );
    }
}
