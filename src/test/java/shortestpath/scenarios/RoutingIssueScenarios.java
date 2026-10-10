package shortestpath.scenarios;

import static shortestpath.profiles.Profiles.SEASONAL;
import static shortestpath.profiles.Profiles.UNIT_TEST;
import static shortestpath.scenarios.Overrides.bankTeleports;
import static shortestpath.scenarios.Overrides.eliteDiaries;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.TeleportationItem;
import shortestpath.accounts.Spellbook;
import shortestpath.accounts.Unlock;

/**
 * Regression routes for upstream routing issues; the category names the issue. Suite {@code routing-issues}; exact lengths in
 * {@code scenarios/expected-lengths/routing-issues.json}.
 */
final class RoutingIssueScenarios {
    private RoutingIssueScenarios() { }

    static void define(Suite suite) {
        suite.scenario("GE centre walk (routing control)", "routing-control")
            .from(3160, 3486, 0).to(3170, 3492, 0)
            .profile(UNIT_TEST);

        suite.scenario("Digsite gate (#139) kudos 153+ crosses gate", "routing-issue-139")
            .from(3293, 3428, 0).to(3350, 3415, 0)
            .profile(UNIT_TEST)
            .account(a -> a.unlock(Unlock.MUSEUM_KUDOS_153))
            .settings(s -> s.setBypassVarbitChecks(false))
            .minimumLength(40);

        suite.scenario("Digsite gate (#139) no kudos walks around fence", "routing-issue-139")
            .from(3293, 3428, 0).to(3350, 3415, 0)
            .profile(UNIT_TEST)
            .account(a -> a.lock(Unlock.MUSEUM_KUDOS_153))
            .settings(s -> s.setBypassVarbitChecks(false))
            .minimumLength(100);

        suite.scenario("Piscatoris colony (#148) enters through fence hole", "routing-issue-148")
            .from(2344, 3645, 0).to(2344, 3670, 0)
            .profile(UNIT_TEST)
            .minimumLength(15);

        suite.scenario("Civitas to Auburnvale (#171) walks around Mons Gratia", "routing-issue-171")
            .from(1697, 3140, 0).to(1411, 3361, 0)
            .profile(UNIT_TEST)
            .minimumLength(300);

        suite.scenario("SW Ape Atoll (#261) unreachable until collision fix", "routing-issue-261")
            .from(2760, 2780, 0).to(2650, 2740, 0)
            .profile(UNIT_TEST)
            .minimumLength(279);

        suite.scenario("RFD dining room (#288) cannot enter until door mapped", "routing-issue-288")
            .from(3213, 3221, 0).to(1866, 5323, 0)
            .profile(UNIT_TEST)
            .minimumLength(2);

        suite.scenario("Taverley dungeon (#291) dusty key opens gate to dragons", "routing-issue-291")
            .from(2925, 9803, 0).to(2872, 9795, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.DUSTY_KEY, 1))
            .settings(s -> s.setUseAgilityShortcuts(false))
            .minimumLength(70);

        suite.scenario("Taverley dungeon (#291) agility pipe reaches dragons", "routing-issue-291")
            .from(2925, 9803, 0).to(2872, 9795, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.AGILITY, 70))
            .settings(s -> s.setUseAgilityShortcuts(true))
            .minimumLength(140);

        suite.scenario("Taverley dungeon (#291) no key no shortcut unreachable", "routing-issue-291")
            .from(2925, 9803, 0).to(2872, 9795, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseAgilityShortcuts(false))
            .expectUnreachable();

        suite.scenario("Great Conch (#319) fairy ring CJQ reaches island", "routing-issue-319")
            .from(2996, 3108, 0).to(3180, 2420, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.DRAMEN_STAFF, 1))
            .settings(s -> s.setUseFairyRings(true))
            .minimumLength(60);

        suite.scenario("Great Conch (#319) unreachable without fairy ring", "routing-issue-319")
            .from(2996, 3108, 0).to(3180, 2420, 0)
            .profile(UNIT_TEST)
            .expectUnreachable();

        suite.scenario("Keldagrim minecart (#324) GE trapdoor to station", "routing-issue-324")
            .from(3141, 3504, 0).to(2909, 10174, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseMinecarts(true))
            .minimumLength(2);

        suite.scenario("Entrana dungeon (#350) no wilderness exit until magic door mapped", "routing-issue-350")
            .from(2830, 9765, 0).to(3100, 3950, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseTeleportationSpellsHome(false))
            .expectUnreachable();

        suite.scenario("Heroes guild (#362) ladder reaches fountain basement", "routing-issue-362")
            .from(2906, 3480, 0).to(2900, 9874, 0)
            .profile(UNIT_TEST)
            .minimumLength(20);

        suite.scenario("Ibans temple (#522) unreachable until region mapped", "routing-issue-522")
            .from(2433, 3312, 0).to(2147, 4648, 0)
            .profile(UNIT_TEST)
            .expectUnreachable();

        suite.scenario("Kharazi jungle (#504) machete and axe chops through bush", "routing-issue-504")
            .from(2795, 2945, 0).to(2800, 2900, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varplayer(VarPlayerID.LEGENDSQUEST, 50)
                .inventory(ItemID.MACHETTE, 1)
                .inventory(ItemID.RUNE_AXE, 1))
            .settings(s -> s.setBypassVarPlayerChecks(false))
            .minimumLength(30);

        suite.scenario("Kharazi jungle (#504) no tools unreachable", "routing-issue-504")
            .from(2795, 2945, 0).to(2800, 2900, 0)
            .profile(UNIT_TEST)
            .account(a -> a.varplayer(VarPlayerID.LEGENDSQUEST, 50))
            .settings(s -> s.setBypassVarPlayerChecks(false))
            .expectUnreachable();

        suite.scenario("Camulet (#314) teleports to Enakhras temple", "routing-issue-314")
            .from(3000, 3300, 0).to(3105, 9315, 2)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.CAMULET, 1))
            .settings(s -> s.setUseTeleportationItems(TeleportationItem.INVENTORY));

        suite.scenario("Mythical cape (#160) teleports to Myths Guild", "routing-issue-160")
            .from(2600, 3200, 0).to(2457, 2850, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.MYTHICAL_CAPE, 1))
            .settings(s -> s.setUseTeleportationItems(TeleportationItem.INVENTORY))
            .minimumLength(2);

        suite.scenario("Quetzal whistle (#537) works after Twilights Promise", "routing-issue-537")
            .from(3213, 3428, 0).to(1411, 3361, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.HG_QUETZALWHISTLE_BASIC, 1)
                .quest(Quest.TWILIGHTS_PROMISE, QuestState.FINISHED))
            .settings(s -> s.setUseQuetzals(true));

        suite.scenario("Quetzal whistle (#537) locked before Twilights Promise", "routing-issue-537")
            .from(3213, 3428, 0).to(1411, 3361, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.HG_QUETZALWHISTLE_BASIC, 1)
                .quest(Quest.TWILIGHTS_PROMISE, QuestState.NOT_STARTED))
            .settings(s -> s.setUseQuetzals(true))
            .minimumLength(450);

        suite.scenario("Ape Atoll glider (#396) works after Monkey Madness II", "routing-issue-396")
            .from(2462, 3505, 0).to(2711, 2803, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .quest(Quest.THE_GRAND_TREE, QuestState.FINISHED)
                .quest(Quest.MONKEY_MADNESS_II, QuestState.FINISHED))
            .settings(s -> s.setUseGnomeGliders(true));

        suite.scenario("Ape Atoll glider (#396) needs Monkey Madness II", "routing-issue-396")
            .from(2462, 3505, 0).to(2711, 2803, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .quest(Quest.THE_GRAND_TREE, QuestState.FINISHED)
                .quest(Quest.MONKEY_MADNESS_II, QuestState.NOT_STARTED))
            .settings(s -> s.setUseGnomeGliders(true))
            .expectUnreachable();

        suite.scenario("Wilderness slayer cave (#79) descends south entrance", "routing-issue-79")
            .from(3260, 3663, 0).to(3385, 10052, 0)
            .profile(UNIT_TEST);

        suite.scenario("Wilderness slayer cave (#79) exits to surface", "routing-issue-79")
            .from(3385, 10052, 0).to(3260, 3663, 0)
            .profile(UNIT_TEST);

        suite.scenario("Wilderness slayer cave (#79) descends north entrance", "routing-issue-79")
            .from(3294, 3749, 0).to(3406, 10145, 0)
            .profile(UNIT_TEST);

        suite.scenario("Chaos temple (#79) crosses lava to courtyard", "routing-issue-79")
            .from(3238, 3600, 0).to(3238, 3640, 0)
            .profile(UNIT_TEST);

        suite.scenario("Teleport cost (#119) consumable tele preferred", "routing-issue-119")
            .from(3065, 3357, 0).to(2945, 3368, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.RING_OF_WEALTH_5, 1))
            .settings(s -> s.setUseTeleportationItems(TeleportationItem.INVENTORY));

        suite.scenario("Teleport cost (#119) high threshold suppresses tele", "routing-issue-119")
            .from(3065, 3357, 0).to(2945, 3368, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.RING_OF_WEALTH_5, 1))
            .settings(s -> {
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setCostConsumableTeleportationItems(5000);
            })
            .minimumLength(100);

        suite.scenario("Blood altar rocks (#126) correct way climbs to mine", "routing-issue-126")
            .from(1735, 3854, 0).to(1760, 3855, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.AGILITY, 73))
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("Blood altar rocks (#126) mine side cannot descend", "routing-issue-126")
            .from(1760, 3855, 0).to(1735, 3854, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.AGILITY, 73))
            .settings(s -> s.setUseAgilityShortcuts(true))
            .minimumLength(40);

        suite.scenario("Blood altar rocks (#126) no agility takes detour", "routing-issue-126")
            .from(1735, 3854, 0).to(1760, 3855, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.AGILITY, 0))
            .settings(s -> s.setUseAgilityShortcuts(true))
            .minimumLength(40);

        suite.scenario("Mage arena tele (#140) usable after guardian talk", "routing-issue-140")
            .from(3163, 3485, 0).to(3363, 3295, 0)
            .profile(UNIT_TEST)
            .account(a -> a.varbit(VarbitID.MAGICTRAINING_ENTRA_NOOB, 1))
            .settings(s -> {
                s.setUseTeleportationMinigames(true);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Isafdar clearing (#141) reachable via forest obstacles", "routing-issue-141")
            .from(2760, 3238, 0).to(2205, 3158, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.AGILITY, 56))
            .settings(s -> {
                s.setUseAgilityShortcuts(true);
                s.setUseCharterShips(true);
                s.setCurrencyThreshold(5000);
            });

        suite.scenario("Isafdar clearing (#141) DLR landing pocket sealed", "routing-issue-141")
            .from(2213, 3098, 0).to(2205, 3158, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.AGILITY, 99))
            .settings(s -> {
                s.setUseAgilityShortcuts(true);
                s.setUseTeleportationSpellsHome(false);
            })
            .expectUnreachable();

        suite.scenario("Tithe farm (#162) unlocked uses minigame teleport", "routing-issue-162")
            .from(3163, 3485, 0).to(1792, 3501, 0)
            .profile(UNIT_TEST)
            .account(a -> a.varbit(VarbitID.ZEAH_TITHE_MINIGAME_UNLOCKED, 1))
            .settings(s -> {
                s.setUseTeleportationMinigames(true);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Tithe farm (#162) locked walks without teleport", "routing-issue-162")
            .from(3163, 3485, 0).to(1792, 3501, 0)
            .profile(UNIT_TEST)
            .account(a -> a.varbit(VarbitID.ZEAH_TITHE_MINIGAME_UNLOCKED, 0))
            .settings(s -> {
                s.setUseTeleportationMinigames(true);
                s.setBypassVarbitChecks(false);
                // Its walking route reaches Kourend through the Abyss soul rift (#693).
                s.setUnlockSoulRift(true);
            })
            .minimumLength(100);

        suite.scenario("Crandor (#170) north clue via volcano dungeon", "routing-issue-170")
            .from(2856, 3167, 0).to(2848, 3296, 0)
            .profile(UNIT_TEST)
            .account(a -> a.varbit(VarbitID.DRAGONSLAYER_CRANDOR_FOUND_SECRET_DOOR, 1))
            .settings(s -> s.setBypassVarbitChecks(false));

        suite.scenario("Crandor (#170) locked wall cannot reach island", "routing-issue-170")
            .from(2856, 3167, 0).to(2848, 3296, 0)
            .profile(UNIT_TEST)
            .account(a -> a.varbit(VarbitID.DRAGONSLAYER_CRANDOR_FOUND_SECRET_DOOR, 0))
            .settings(s -> s.setBypassVarbitChecks(false))
            .expectUnreachable();

        suite.scenario("Crandor (#170) rocks shortcut at 84 agility", "routing-issue-170")
            .from(2832, 3255, 0).to(2845, 3240, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.AGILITY, 84))
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("Crandor (#170) south shore without agility detours", "routing-issue-170")
            .from(2832, 3255, 0).to(2845, 3240, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.AGILITY, 0))
            .settings(s -> s.setUseAgilityShortcuts(true))
            .minimumLength(80);

        suite.scenario("Swamp caves (#170) dark hole reaches cave", "routing-issue-170")
            .from(3169, 3171, 0).to(3185, 9570, 0)
            .profile(UNIT_TEST);

        suite.scenario("MEP2 temple (#202) dark maze pocket unreachable", "routing-issue-202")
            .from(2311, 9793, 0).to(2290, 9880, 0)
            .profile(UNIT_TEST)
            .expectUnreachable();

        suite.scenario("MEP2 temple (#202) landing reaches north ascent", "routing-issue-202")
            .from(2311, 9793, 0).to(2305, 9915, 0)
            .profile(UNIT_TEST);

        suite.scenario("Lava maze webs (#204) webs blocked without slash weapon", "routing-issue-204")
            .from(3115, 3857, 0).to(3090, 3860, 0)
            .profile(UNIT_TEST)
            .expectUnreachable();

        suite.scenario("Lava maze webs (#204) south approach control", "routing-control")
            .from(3115, 3857, 0).to(3105, 3845, 0)
            .profile(UNIT_TEST);

        suite.scenario("Mage bank (#205) lever reaches interior", "routing-issue-205")
            .from(3088, 3950, 0).to(2534, 4712, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseTeleportationLevers(true));

        suite.scenario("Mage bank (#205) lever returns to surface", "routing-control")
            .from(2539, 4712, 0).to(3088, 3950, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseTeleportationLevers(true));

        suite.scenario("Xerics talisman (#214) Honour offered without unlock varbit", "routing-issue-214")
            .from(1500, 3560, 0).to(1256, 3562, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.XERIC_TALISMAN, 1))
            .settings(s -> {
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Xerics talisman (#214) Lookout control teleport", "routing-control")
            .from(1500, 3560, 0).to(1579, 3530, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.XERIC_TALISMAN, 1))
            .settings(s -> {
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Kandarin headgear 3 (#216) teleports when daily unused", "routing-issue-216")
            .from(3160, 3486, 0).to(2726, 3420, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varbit(VarbitID.KANDARIN_DIARY_HARD_COMPLETE, 1)
                .varbit(VarbitID.SEERS_SHERLOCK_TELEPORT, 0)
                .inventory(ItemID.SEERS_HEADBAND_HARD, 1))
            .settings(s -> {
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Kandarin headgear 3 (#216) detours when daily used", "routing-issue-216")
            .from(3160, 3486, 0).to(2726, 3420, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varbit(VarbitID.KANDARIN_DIARY_HARD_COMPLETE, 1)
                .varbit(VarbitID.SEERS_SHERLOCK_TELEPORT, 1)
                .inventory(ItemID.SEERS_HEADBAND_HARD, 1))
            .settings(s -> {
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setBypassVarbitChecks(false);
            })
            .minimumLength(300);

        suite.scenario("Entrana (#237) boat reachable carrying an axe", "routing-issue-237")
            .from(3045, 3235, 0).to(2852, 3350, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.RUNE_AXE, 1));

        suite.scenario("Entrana (#237) boat reachable carrying nothing", "routing-control")
            .from(3045, 3235, 0).to(2852, 3350, 0)
            .profile(UNIT_TEST);

        suite.scenario("Legends cavern (#244) NW rocks descent missing", "routing-issue-244")
            .from(2820, 2940, 0).to(2790, 9340, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.AGILITY, 99))
            .expectUnreachable();

        suite.scenario("Kharazi jungle (#244) control surface walk", "routing-control")
            .from(2820, 2940, 0).to(2899, 2942, 0)
            .profile(UNIT_TEST);

        suite.scenario("Pyramid plunder (#245) interior exit unmapped", "routing-issue-245")
            .from(1927, 4428, 0).to(3315, 2796, 0)
            .profile(UNIT_TEST)
            .settings(s -> {
                s.setUseTeleportationSpellsHome(false);
                s.setUseTeleportationMinigames(false);
            })
            .minimumLength(41);

        suite.scenario("Sophanem (#245) control pyramid area to city", "routing-control")
            .from(3288, 2801, 0).to(3315, 2796, 0)
            .profile(UNIT_TEST);

        suite.scenario("Ape Atoll bridge (#265) jump-off shortcut missing", "routing-issue-265")
            .from(2805, 2735, 0).to(2820, 2700, 0)
            .profile(UNIT_TEST)
            .minimumLength(42);

        suite.scenario("Ape Atoll SE (#265) control pocket walk", "routing-control")
            .from(2820, 2700, 0).to(2850, 2680, 0)
            .profile(UNIT_TEST);

        suite.scenario("Diary cape (#267) teleports when diaries done", "routing-issue-267")
            .from(3222, 3218, 0).to(2560, 3320, 0)
            .profile(UNIT_TEST)
            .account(eliteDiaries())
            .account(a -> a.inventory(ItemID.SKILLCAPE_AD_TRIMMED, 1))
            .settings(s -> {
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Diary cape (#267) walk only without cape", "routing-control")
            .from(3222, 3218, 0).to(2560, 3320, 0)
            .profile(UNIT_TEST)
            .settings(s -> {
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Shrimp and Parrot (#273) back room cannot exit until door mapped", "routing-issue-273")
            .from(2800, 3192, 0).to(2794, 3184, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseTeleportationSpellsHome(false))
            .expectUnreachable();

        suite.scenario("Brimhaven street (#273) control pub interior walk", "routing-control")
            .from(2794, 3184, 0).to(2793, 3191, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseTeleportationSpellsHome(false));

        suite.scenario("Trollweiss crevasse (#275) descent blocked without rope", "routing-issue-275")
            .from(2784, 3865, 0).to(2772, 10231, 0)
            .profile(UNIT_TEST)
            .expectUnreachable();

        suite.scenario("Trollweiss tunnel (#275) control cave entrance to summit", "routing-control")
            .from(2822, 3745, 0).to(2784, 3865, 0)
            .profile(UNIT_TEST);

        suite.scenario("POH Digsite pendant (#317) mounted pendant reaches Lithkren", "routing-issue-317")
            .from(3222, 3218, 0).to(3549, 10456, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.POH_TABLET_TELEPORTTOHOUSE, 1))
            .settings(s -> {
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setUsePoh(true);
                s.setUsePohMountedItems(true);
            });

        suite.scenario("POH Digsite pendant (#317) no mounted item unreachable", "routing-control")
            .from(3222, 3218, 0).to(3549, 10456, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.POH_TABLET_TELEPORTTOHOUSE, 1))
            .settings(s -> {
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setUsePoh(true);
                s.setUsePohMountedItems(false);
            })
            .expectUnreachable();

        suite.scenario("Keldagrim minecart (#324) varbit 571 below threshold cannot reach station", "routing-issue-324")
            .from(3141, 3504, 0).to(2909, 10174, 0)
            .profile(UNIT_TEST)
            .account(a -> a.varbit(VarbitID.GIANTDWARF_QUEST, 4))
            .settings(s -> {
                s.setUseMinecarts(true);
                s.setBypassVarbitChecks(false);
            })
            .expectUnreachable();

        suite.scenario("GE recruiter (#342) Castle Wars walk-only detour", "routing-issue-342")
            .from(3160, 3486, 0).to(2440, 3089, 0)
            .profile(UNIT_TEST)
            .minimumLength(19);

        suite.scenario("Castle Wars lobby (#342) minigame tele reaches", "routing-control")
            .from(3160, 3486, 0).to(2440, 3089, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseTeleportationMinigames(true));

        suite.scenario("Shantay arrest (#343) pass to jail missing", "routing-issue-343")
            .from(3303, 3124, 0).to(3013, 3180, 0)
            .profile(UNIT_TEST)
            .minimumLength(3);

        suite.scenario("Port Sarim jail (#343) street to cell walk", "routing-control")
            .from(3013, 3190, 0).to(3013, 3180, 0)
            .profile(UNIT_TEST);

        suite.scenario("Mogre camp (#354) sealed until diving transport mapped", "routing-issue-354")
            .from(2893, 9388, 0).to(2659, 3155, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseTeleportationSpellsHome(false))
            .expectUnreachable();

        suite.scenario("Port Khazard (#354) dock walk control", "routing-control")
            .from(2659, 3155, 0).to(2663, 3157, 0)
            .profile(UNIT_TEST);

        suite.scenario("Sinclair mansion (#369) cape or ring picks best", "routing-issue-369")
            .from(3160, 3486, 0).to(2740, 3552, 0)
            .profile(UNIT_TEST)
            .account(eliteDiaries())
            .account(a -> a.inventory(ItemID.SKILLCAPE_AD_TRIMMED, 1))
            .settings(s -> {
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setUseFairyRings(true);
            });

        suite.scenario("Sinclair mansion (#369) fairy ring only route", "routing-issue-369")
            .from(3160, 3486, 0).to(2740, 3552, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseFairyRings(true));

        suite.scenario("Sinclair mansion (#369) diary cape only route", "routing-issue-369")
            .from(3160, 3486, 0).to(2740, 3552, 0)
            .profile(UNIT_TEST)
            .account(eliteDiaries())
            .account(a -> a.inventory(ItemID.SKILLCAPE_AD_TRIMMED, 1))
            .settings(s -> s.setUseTeleportationItems(TeleportationItem.INVENTORY));

        suite.scenario("Fremennik cave (#371) shortcuts reach turoth chamber", "routing-issue-371")
            .from(2808, 10002, 0).to(2700, 9995, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.AGILITY, 61))
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("Fremennik cave (#371) no agility walks all chambers", "routing-issue-371")
            .from(2808, 10002, 0).to(2700, 9995, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.AGILITY, 0))
            .settings(s -> s.setUseAgilityShortcuts(true))
            .minimumLength(180);

        suite.scenario("Slayer cave approach (#371) log balance short hop", "routing-issue-371")
            .from(2722, 3590, 0).to(2796, 3615, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.AGILITY, 48))
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("Slayer cave approach (#371) no agility walks around", "routing-issue-371")
            .from(2722, 3590, 0).to(2796, 3615, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.AGILITY, 0))
            .settings(s -> s.setUseAgilityShortcuts(true))
            .minimumLength(150);

        suite.scenario("Slayer cave (#371) entrance walk control", "routing-control")
            .from(2796, 3615, 0).to(2808, 10002, 0)
            .profile(UNIT_TEST);

        suite.scenario("Dragontooth island (#427) no ecto tokens cannot board", "routing-issue-427")
            .from(3703, 3487, 0).to(3800, 3556, 0)
            .profile(UNIT_TEST)
            .settings(s -> {
                s.setUseBoats(true);
                s.setUseTeleportationSpellsHome(false);
            })
            .expectUnreachable();

        suite.scenario("Dragontooth island (#427) ecto tokens board boat", "routing-issue-427")
            .from(3703, 3487, 0).to(3800, 3556, 0)
            .profile(UNIT_TEST)
            .account(a -> a.inventory(ItemID.ECTOTOKEN, 25))
            .settings(s -> s.setUseBoats(true));

        suite.scenario("Canoe (#431) axe in inventory paddles to Champions Guild", "routing-issue-431")
            .from(3243, 3237, 0).to(3200, 3344, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.RUNE_AXE, 1)
                .level(Skill.WOODCUTTING, 99))
            .settings(s -> s.setUseCanoes(true));

        suite.scenario("Canoe (#431) no axe walks to Champions Guild", "routing-issue-431")
            .from(3243, 3237, 0).to(3200, 3344, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.WOODCUTTING, 99))
            .settings(s -> s.setUseCanoes(true))
            .minimumLength(100);

        suite.scenario("Ardougne tele (#488) lunar book walks", "routing-issue-488")
            .from(3160, 3486, 0).to(2660, 3298, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .spellbook(Spellbook.LUNAR)
                .inventory(ItemID.LAWRUNE, 4)
                .inventory(ItemID.WATERRUNE, 4)
                .inventory(ItemID.SOULRUNE, 4)
                .inventory(ItemID.ASTRALRUNE, 4)
                .level(Skill.MAGIC, 99)
                .quest(Quest.LUNAR_DIPLOMACY, QuestState.FINISHED)
                .quest(Quest.PLAGUE_CITY, QuestState.FINISHED))
            .settings(s -> {
                s.setUseTeleportationSpells(true);
                s.setBypassVarbitChecks(false);
            })
            .minimumLength(80);

        suite.scenario("Ardougne tele (#488) standard book teleports", "routing-issue-488")
            .from(3160, 3486, 0).to(2660, 3298, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .spellbook(Spellbook.STANDARD)
                .inventory(ItemID.LAWRUNE, 2)
                .inventory(ItemID.WATERRUNE, 2)
                .level(Skill.MAGIC, 51)
                .quest(Quest.PLAGUE_CITY, QuestState.FINISHED))
            .settings(s -> {
                s.setUseTeleportationSpells(true);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Rogues den (#492) cooldown banks for necklace", "routing-issue-492")
            .from(3160, 3486, 0).to(3040, 4969, 1)
            .profile(UNIT_TEST)
            .account(a -> a
                .minigameTeleportUsedAt(29999999)
                .bank(ItemID.NECKLACE_OF_MINIGAMES_8, 1))
            .settings(bankTeleports())
            .settings(s -> {
                s.setUseTeleportationMinigames(true);
                s.setBypassVarPlayerChecks(false);
            });

        suite.scenario("Rogues den (#492) minigame tele direct no bank", "routing-control")
            .from(3160, 3486, 0).to(3040, 4969, 1)
            .profile(UNIT_TEST)
            .account(a -> a.varbit(VarbitID.CLEANUP_PROGRESS, 0))
            .settings(s -> {
                s.setUseTeleportationMinigames(true);
                s.setBypassVarPlayerChecks(false);
            });

        suite.scenario("Mist staff (#496) covers Falador Teleport runes", "routing-issue-496")
            .from(3160, 3486, 0).to(2967, 3380, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.LAWRUNE, 1)
                .equipment(ItemID.MYSTIC_MIST_BATTLESTAFF, 1)
                .level(Skill.MAGIC, 40))
            .settings(s -> {
                s.setUseTeleportationSpells(true);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Mist staff (#496) no law rune walks", "routing-issue-496")
            .from(3160, 3486, 0).to(2967, 3380, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .equipment(ItemID.MYSTIC_MIST_BATTLESTAFF, 1)
                .level(Skill.MAGIC, 40))
            .settings(s -> {
                s.setUseTeleportationSpells(true);
                s.setBypassVarbitChecks(false);
            })
            .minimumLength(100);

        suite.scenario("Meiyerditch labs cave (#498) 93 agility crosses", "routing-issue-498")
            .from(3480, 9845, 0).to(3492, 9870, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .level(Skill.AGILITY, 93)
                .level(Skill.MINING, 78)
                .quest(Quest.DARKNESS_OF_HALLOWVALE, QuestState.FINISHED)
                .quest(Quest.SINS_OF_THE_FATHER, QuestState.FINISHED))
            .settings(s -> s.setUseTeleportationSpellsHome(false));

        suite.scenario("Home portal (#500) inside-mode exits to Rimmington", "routing-issue-500")
            .from(3160, 3486, 0).to(2953, 3212, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varbit(VarbitID.POH_HOUSE_LOCATION, 1)
                .varbit(VarbitID.POH_TELE_TOGGLE, 0)
                .inventory(ItemID.LAWRUNE, 1)
                .inventory(ItemID.AIRRUNE, 1)
                .inventory(ItemID.EARTHRUNE, 1)
                .level(Skill.MAGIC, 40))
            .settings(s -> {
                s.setUsePoh(true);
                s.setUseTeleportationPortals(true);
                s.setUseTeleportationSpells(true);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Home portal (#500) outside-mode direct to Rimmington", "routing-issue-500")
            .from(3160, 3486, 0).to(2953, 3212, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varbit(VarbitID.POH_HOUSE_LOCATION, 1)
                .varbit(VarbitID.POH_TELE_TOGGLE, 1)
                .inventory(ItemID.LAWRUNE, 1)
                .inventory(ItemID.AIRRUNE, 1)
                .inventory(ItemID.EARTHRUNE, 1)
                .level(Skill.MAGIC, 40))
            .settings(s -> {
                s.setUsePoh(true);
                s.setUseTeleportationPortals(true);
                s.setUseTeleportationSpells(true);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Respawn portal (#502) post-quest reaches Prif normally", "routing-issue-502")
            .from(3213, 3428, 0).to(3227, 6095, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.PRIF_TELEPORT_CRYSTAL, 1)
                .inventory(ItemID.LAWRUNE, 1)
                .inventory(ItemID.AIRRUNE, 1)
                .inventory(ItemID.EARTHRUNE, 1)
                .level(Skill.MAGIC, 40)
                .quest(Quest.SONG_OF_THE_ELVES, QuestState.FINISHED))
            .settings(s -> {
                s.setUsePoh(true);
                s.setUseTeleportationPortalsPoh(true);
                s.setUseTeleportationPortals(true);
                s.setUseTeleportationSpells(true);
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Kharazi jungle (#504) machete alone still enters", "routing-issue-504")
            .from(2795, 2945, 0).to(2800, 2900, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varplayer(VarPlayerID.LEGENDSQUEST, 50)
                .inventory(ItemID.MACHETTE, 1))
            .settings(s -> s.setBypassVarPlayerChecks(false))
            .expectUnreachable();

        suite.scenario("Waterfall island (#505) post-quest raft reaches", "routing-issue-505")
            .from(3160, 3486, 0).to(2527, 3413, 0)
            .profile(UNIT_TEST)
            .account(a -> a.quest(Quest.WATERFALL_QUEST, QuestState.FINISHED))
            .settings(s -> {
                s.setBypassVarbitChecks(false);
                s.setBypassVarPlayerChecks(false);
            });

        suite.scenario("Deepfin bank (#508) undocked cannot reach", "routing-issue-508")
            .from(3160, 3486, 0).to(1935, 2754, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.SAILORS_AMULET, 1)
                .level(Skill.SAILING, 1))
            .settings(s -> {
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setUseCharterShips(true);
                s.setBypassVarbitChecks(false);
            })
            .expectUnreachable();

        suite.scenario("Deepfin bank (#508) docked amulet reaches", "routing-issue-508")
            .from(3160, 3486, 0).to(1935, 2754, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varbit(VarbitID.SAILORS_AMULET_DEEPFIN, 1)
                .inventory(ItemID.SAILORS_AMULET, 1)
                .inventory(ItemID.COINS, 5000)
                .level(Skill.SAILING, 67))
            .settings(s -> {
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setUseCharterShips(true);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("League briefcase (#510) works on league world", "routing-issue-510")
            .from(1697, 3140, 0).to(1510, 3410, 0)
            .profile(SEASONAL)
            .account(a -> a.inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1));

        suite.scenario("Bank tele item (#527) banked camulet detours without bank path", "routing-issue-527")
            .from(3160, 3486, 0).to(3105, 9315, 0)
            .profile(UNIT_TEST)
            .account(a -> a.bank(ItemID.CAMULET, 1))
            .settings(s -> s.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK));

        suite.scenario("Bank tele item (#527) bank path fetches camulet", "routing-control")
            .from(3160, 3486, 0).to(3105, 9315, 0)
            .profile(UNIT_TEST)
            .account(a -> a.bank(ItemID.CAMULET, 1))
            .settings(bankTeleports());

        suite.scenario("House tele (#528) varbit 9 exits at Prif portal", "routing-issue-528")
            .from(3160, 3486, 0).to(3239, 6075, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varbit(VarbitID.POH_HOUSE_LOCATION, 9)
                .varbit(VarbitID.POH_TELE_TOGGLE, 1)
                .inventory(ItemID.LAWRUNE, 1)
                .inventory(ItemID.AIRRUNE, 1)
                .inventory(ItemID.EARTHRUNE, 1)
                .level(Skill.MAGIC, 40)
                .quest(Quest.SONG_OF_THE_ELVES, QuestState.NOT_STARTED))
            .settings(s -> {
                s.setUsePoh(true);
                s.setUseTeleportationSpells(true);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("House tele (#528) varbit 9 cannot reach Aldarin portal", "routing-issue-528")
            .from(3160, 3486, 0).to(1422, 2965, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varbit(VarbitID.POH_HOUSE_LOCATION, 9)
                .varbit(VarbitID.POH_TELE_TOGGLE, 1)
                .inventory(ItemID.LAWRUNE, 1)
                .inventory(ItemID.AIRRUNE, 1)
                .inventory(ItemID.EARTHRUNE, 1)
                .level(Skill.MAGIC, 40)
                .quest(Quest.SONG_OF_THE_ELVES, QuestState.NOT_STARTED))
            .settings(s -> {
                s.setUsePoh(true);
                s.setUseTeleportationSpells(true);
                s.setBypassVarbitChecks(false);
            })
            .expectUnreachable();

        suite.scenario("House tele (#528) varbit 1 exits at Rimmington", "routing-control")
            .from(3160, 3486, 0).to(2953, 3212, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varbit(VarbitID.POH_HOUSE_LOCATION, 1)
                .varbit(VarbitID.POH_TELE_TOGGLE, 1)
                .inventory(ItemID.LAWRUNE, 1)
                .inventory(ItemID.AIRRUNE, 1)
                .inventory(ItemID.EARTHRUNE, 1)
                .level(Skill.MAGIC, 40))
            .settings(s -> {
                s.setUsePoh(true);
                s.setUseTeleportationSpells(true);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Sailors amulet (#532) docked teleports to Deepfin", "routing-issue-532")
            .from(3160, 3486, 0).to(1935, 2754, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varbit(VarbitID.SAILORS_AMULET_DEEPFIN, 1)
                .inventory(ItemID.SAILORS_AMULET, 1)
                .level(Skill.SAILING, 67))
            .settings(s -> {
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Sailors amulet (#532) locked dock cannot reach Deepfin", "routing-control")
            .from(3160, 3486, 0).to(1935, 2754, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varbit(VarbitID.SAILORS_AMULET_DEEPFIN, 0)
                .inventory(ItemID.SAILORS_AMULET, 1)
                .level(Skill.SAILING, 67))
            .settings(s -> {
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setBypassVarbitChecks(false);
            })
            .expectUnreachable();

        suite.scenario("Lava staff (#544) covers Civitas teleport runes", "routing-issue-544")
            .from(3160, 3486, 0).to(1700, 3141, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.LAWRUNE, 2)
                .equipment(ItemID.LAVA_BATTLESTAFF, 1)
                .level(Skill.MAGIC, 60)
                .quest(Quest.TWILIGHTS_PROMISE, QuestState.FINISHED)
                .quest(Quest.CHILDREN_OF_THE_SUN, QuestState.NOT_STARTED))
            .settings(s -> {
                s.setUseTeleportationSpells(true);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Civitas teleport (#544) air staff no rune coverage", "routing-control")
            .from(3160, 3486, 0).to(1700, 3141, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.LAWRUNE, 2)
                .equipment(ItemID.AIR_BATTLESTAFF, 1)
                .level(Skill.MAGIC, 60)
                .quest(Quest.TWILIGHTS_PROMISE, QuestState.FINISHED)
                .quest(Quest.CHILDREN_OF_THE_SUN, QuestState.NOT_STARTED))
            .settings(s -> {
                s.setUseTeleportationSpells(true);
                s.setBypassVarbitChecks(false);
            })
            .expectUnreachable();

        suite.scenario("Mountain guide (#548) met guide hops Gorge to Nemus", "routing-issue-548")
            .from(1486, 3232, 0).to(1411, 3361, 0)
            .profile(UNIT_TEST)
            .account(a -> a.unlock(Unlock.MET_AUBURN_MOUNTAIN_GUIDE))
            .settings(s -> s.setBypassVarbitChecks(false));

        suite.scenario("Mountain guide (#548) unmet still hops Gorge to Nemus", "routing-issue-548")
            .from(1486, 3232, 0).to(1411, 3361, 0)
            .profile(UNIT_TEST)
            .account(a -> a.lock(Unlock.MET_AUBURN_MOUNTAIN_GUIDE))
            .settings(s -> s.setBypassVarbitChecks(false));

        suite.scenario("Gutanoth crumbling wall (#549) pocket sealed without shortcut", "routing-issue-549")
            .from(2550, 3031, 0).to(2543, 3031, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .level(Skill.AGILITY, 71)
                .quest(Quest.WATCHTOWER, QuestState.FINISHED))
            .settings(s -> s.setUseAgilityShortcuts(true))
            .minimumLength(5);

        suite.scenario("Gutanoth crumbling wall (#549) below 71 agility stays sealed", "routing-issue-549")
            .from(2550, 3031, 0).to(2543, 3031, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .level(Skill.AGILITY, 70)
                .quest(Quest.WATCHTOWER, QuestState.FINISHED))
            .settings(s -> s.setUseAgilityShortcuts(true))
            .expectUnreachable();

        suite.scenario("Gutanoth east approach walk (#549) control", "routing-control")
            .from(2550, 3031, 0).to(2570, 3032, 0)
            .profile(UNIT_TEST);

        suite.scenario("Construction cape (#555) under 99 takes cape hop", "routing-issue-555")
            .from(3160, 3486, 0).to(2953, 3212, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varbit(VarbitID.POH_HOUSE_LOCATION, 1)
                .varbit(VarbitID.POH_TELE_TOGGLE, 1)
                .inventory(ItemID.SKILLCAPE_CONSTRUCTION, 1)
                .level(Skill.CONSTRUCTION, 50))
            .settings(s -> {
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setBypassVarbitChecks(false);
            })
            .minimumLength(270);

        suite.scenario("Construction cape (#555) at 99 reaches Rimmington portal", "routing-control")
            .from(3160, 3486, 0).to(2953, 3212, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varbit(VarbitID.POH_HOUSE_LOCATION, 1)
                .varbit(VarbitID.POH_TELE_TOGGLE, 1)
                .inventory(ItemID.SKILLCAPE_CONSTRUCTION, 1)
                .level(Skill.CONSTRUCTION, 99))
            .settings(s -> {
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Canoe (#563) Edgeville to Lumbridge paddles with axe", "routing-issue-563")
            .from(3132, 3510, 0).to(3243, 3235, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.RUNE_AXE, 1)
                .level(Skill.WOODCUTTING, 42))
            .settings(s -> s.setUseCanoes(true));

        suite.scenario("Canoe (#563) no axe walks Edgeville to Lumbridge", "routing-control")
            .from(3132, 3510, 0).to(3243, 3235, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.WOODCUTTING, 60))
            .settings(s -> {
                s.setUseCanoes(true);
                s.setUseTeleportationSpellsHome(false);
            })
            .minimumLength(50);

        suite.scenario("Baxtorian falls clue (#568) dig ledge unreachable", "routing-issue-568")
            .from(3505, 3488, 0).to(2512, 3467, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varplayer(VarPlayerID.WATERFALL_QUEST, 10)
                .quest(Quest.WATERFALL_QUEST, QuestState.FINISHED))
            .settings(s -> {
                s.setBypassVarbitChecks(false);
                s.setBypassVarPlayerChecks(false);
            })
            .expectUnreachable();

        suite.scenario("Baxtorian falls (#568) whirlpool shore reachable", "routing-control")
            .from(3505, 3488, 0).to(2511, 3511, 0)
            .profile(UNIT_TEST);

        suite.scenario("Dorgesh-kaan sphere (#225) teleports into the city", "routing-issue-225")
            .from(3213, 3428, 0).to(2703, 5350, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.DORGESH_TELEPORT_ARTIFACT, 1)
                .quest(Quest.DEATH_TO_THE_DORGESHUUN, QuestState.FINISHED))
            .settings(s -> {
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
                s.setUseFairyRings(false);
            });

        suite.scenario("Legends cavern (#244) NW rocks descent mapped", "routing-issue-244")
            .from(2820, 2940, 0).to(2790, 9340, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varplayer(VarPlayerID.LEGENDSQUEST, 50)
                .level(Skill.AGILITY, 99))
            .settings(s -> {
                s.setUseAgilityShortcuts(true);
                s.setBypassVarPlayerChecks(false);
            })
            .minimumLength(217);

        suite.scenario("Shrimp and Parrot (#273) locked house sealed at north door", "routing-issue-273")
            .from(2794, 3201, 0).to(2793, 3195, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseTeleportationSpellsHome(false))
            .expectUnreachable();

        suite.scenario("Shrimp and Parrot (#273) locked house sealed at west door", "routing-issue-273")
            .from(2793, 3195, 0).to(2794, 3184, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseTeleportationSpellsHome(false))
            .expectUnreachable();

        suite.scenario("Brimhaven street (#273) control pub south room to garden walk", "routing-control")
            .from(2794, 3184, 0).to(2786, 3195, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseTeleportationSpellsHome(false));

        suite.scenario("Mogre camp (#354) anchor exit reaches dock", "routing-issue-354")
            .from(2963, 9478, 1).to(2663, 3157, 0)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseTeleportationSpellsHome(false));

        suite.scenario("Mogre camp (#354) Murphy dive enters camp", "routing-issue-354")
            .from(2664, 3160, 0).to(2971, 9513, 1)
            .profile(UNIT_TEST)
            .account(a -> a
                .equipment(ItemID.HUNDRED_PIRATE_DIVING_HELMET, 1)
                .equipment(ItemID.HUNDRED_PIRATE_DIVING_BACKPACK, 1)
                .quest(Quest.RECIPE_FOR_DISASTER__PIRATE_PETE, QuestState.FINISHED))
            .settings(s -> s.setUseTeleportationSpellsHome(false));

        suite.scenario("Mogre camp (#354) dive gated without gear or quest", "routing-issue-354")
            .from(2664, 3160, 0).to(2971, 9513, 1)
            .profile(UNIT_TEST)
            .settings(s -> s.setUseTeleportationSpellsHome(false))
            .expectUnreachable();

        suite.scenario("Mogre camp (#354) cavern sealed without rocks moved", "routing-issue-354")
            .from(2950, 9515, 1).to(2950, 9526, 1)
            .profile(UNIT_TEST)
            .settings(s -> {
                s.setUseTeleportationSpellsHome(false);
                s.setBypassVarbitChecks(false);
            })
            .expectUnreachable();

        suite.scenario("Mogre camp (#354) cavern opens after rocks moved", "routing-issue-354")
            .from(2950, 9515, 1).to(2950, 9526, 1)
            .profile(UNIT_TEST)
            .account(a -> a.varbit(VarbitID.HUNDRED_PIRATE_ROCKS, 5))
            .settings(s -> {
                s.setUseTeleportationSpellsHome(false);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Waterfall island (#505) pre-quest raft should not reach", "routing-issue-505")
            .from(3160, 3486, 0).to(2512, 3481, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varplayer(VarPlayerID.WATERFALL_QUEST, 0)
                .quest(Quest.WATERFALL_QUEST, QuestState.NOT_STARTED))
            .settings(s -> {
                s.setBypassVarbitChecks(false);
                s.setBypassVarPlayerChecks(false);
            })
            .expectUnreachable();

        suite.scenario("Quetzal whistle (#537) works before Twilights Promise", "routing-issue-537")
            .from(3213, 3428, 0).to(1411, 3361, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.HG_QUETZALWHISTLE_BASIC, 1)
                .quest(Quest.TWILIGHTS_PROMISE, QuestState.NOT_STARTED))
            .settings(s -> {
                s.setUseQuetzals(true);
                s.setUseTeleportationItems(TeleportationItem.INVENTORY);
            })
            .minimumLength(2);

        suite.scenario("Baxtorian falls clue (#568) dig ledge with rope descends falls", "routing-issue-568")
            .from(3505, 3488, 0).to(2512, 3467, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varplayer(VarPlayerID.WATERFALL_QUEST, 10)
                .inventory(ItemID.ROPE, 1)
                .quest(Quest.WATERFALL_QUEST, QuestState.FINISHED))
            .settings(s -> {
                s.setBypassVarbitChecks(false);
                s.setBypassVarPlayerChecks(false);
            });

        suite.scenario("Baxtorian falls clue (#568) dig ledge unroped with medium diary", "routing-issue-568")
            .from(3505, 3488, 0).to(2512, 3467, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varbit(VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE, 1)
                .varplayer(VarPlayerID.WATERFALL_QUEST, 10)
                .quest(Quest.WATERFALL_QUEST, QuestState.FINISHED))
            .settings(s -> {
                s.setBypassVarbitChecks(false);
                s.setBypassVarPlayerChecks(false);
            });

        suite.scenario("MEP2 temple (#202) dodge trap crosses east alcove", "routing-issue-202")
            .from(1863, 4657, 2).to(1868, 4657, 2)
            .profile(UNIT_TEST)
            .account(a -> a
                .level(Skill.AGILITY, 50)
                .quest(Quest.MOURNINGS_END_PART_II, QuestState.FINISHED))
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("MEP2 temple (#202) trap sealed without MEP2 quest", "routing-issue-202")
            .from(1863, 4657, 2).to(1868, 4657, 2)
            .profile(UNIT_TEST)
            .account(a -> a
                .level(Skill.AGILITY, 50)
                .quest(Quest.MOURNINGS_END_PART_II, QuestState.NOT_STARTED))
            .settings(s -> s.setUseAgilityShortcuts(true))
            .expectUnreachable();

        suite.scenario("MEP2 temple (#202) low wall crosses refuge alcove", "routing-issue-202")
            .from(1881, 4620, 1).to(1885, 4620, 1)
            .profile(UNIT_TEST)
            .account(a -> a
                .level(Skill.AGILITY, 50)
                .quest(Quest.MOURNINGS_END_PART_II, QuestState.FINISHED))
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("MEP2 temple (#202) wall support crosses first-floor gap", "routing-issue-202")
            .from(1900, 4612, 1).to(1912, 4612, 1)
            .profile(UNIT_TEST)
            .account(a -> a
                .level(Skill.AGILITY, 50)
                .quest(Quest.MOURNINGS_END_PART_II, QuestState.FINISHED))
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("Wilderness slayer cave (#79) 77 agility squeezes south crevice", "routing-issue-79")
            .from(3334, 10117, 0).to(3334, 10122, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.AGILITY, 77))
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("Wilderness slayer cave (#79) crevice detours below 77 agility", "routing-issue-79")
            .from(3334, 10117, 0).to(3334, 10122, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.AGILITY, 76))
            .settings(s -> s.setUseAgilityShortcuts(true))
            .minimumLength(117);

        suite.scenario("Wilderness slayer cave (#79) 77 agility squeezes east crevice", "routing-issue-79")
            .from(3434, 10089, 0).to(3434, 10094, 0)
            .profile(UNIT_TEST)
            .account(a -> a.level(Skill.AGILITY, 77))
            .settings(s -> s.setUseAgilityShortcuts(true));

        suite.scenario("Ibans temple (#522) through Underground Pass", "routing-issue-522")
            .from(2433, 3312, 0).to(2147, 4648, 1)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.ROPE, 1)
                .inventory(ItemID.SPADE, 1)
                .quest(Quest.UNDERGROUND_PASS, QuestState.FINISHED)
                .quest(Quest.REGICIDE, QuestState.IN_PROGRESS));

        suite.scenario("Keldagrim minecart (#324) varbit 571 past threshold reaches station", "routing-issue-324")
            .from(3141, 3504, 0).to(2909, 10174, 0)
            .profile(UNIT_TEST)
            .account(a -> a.varbit(VarbitID.GIANTDWARF_QUEST, 5))
            .settings(s -> {
                s.setUseMinecarts(true);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Mage arena tele (#140) locked before guardian talk", "routing-issue-140")
            .from(3163, 3485, 0).to(3363, 3295, 0)
            .profile(UNIT_TEST)
            .account(a -> a.varbit(VarbitID.MAGICTRAINING_ENTRA_NOOB, 0))
            .settings(s -> {
                s.setUseTeleportationMinigames(true);
                s.setBypassVarbitChecks(false);
            })
            .minimumLength(100);

        suite.scenario("Meiyerditch labs cave (#498) 59 agility blocked", "routing-issue-498")
            .from(3480, 9845, 0).to(3492, 9870, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .level(Skill.AGILITY, 59)
                .level(Skill.MINING, 59)
                .quest(Quest.DARKNESS_OF_HALLOWVALE, QuestState.FINISHED)
                .quest(Quest.IN_AID_OF_THE_MYREQUE, QuestState.NOT_STARTED)
                .quest(Quest.SINS_OF_THE_FATHER, QuestState.NOT_STARTED))
            .settings(s -> s.setUseTeleportationSpellsHome(false))
            .expectUnreachable();

        suite.scenario("Respawn portal (#502) Lumbridge respawn cannot reach Prif", "routing-issue-502")
            .from(3213, 3428, 0).to(3227, 6095, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.LAWRUNE, 1)
                .inventory(ItemID.AIRRUNE, 1)
                .inventory(ItemID.EARTHRUNE, 1)
                .level(Skill.MAGIC, 40)
                .quest(Quest.SONG_OF_THE_ELVES, QuestState.NOT_STARTED))
            .settings(s -> {
                s.setUsePoh(true);
                s.setUseTeleportationPortalsPoh(true);
                s.setUseTeleportationPortals(true);
                s.setUseTeleportationSpells(true);
                s.setBypassVarbitChecks(false);
            })
            .expectUnreachable();

        suite.scenario("House tele (#528) varbit 13 exits at Aldarin portal", "routing-issue-528")
            .from(3160, 3486, 0).to(1422, 2965, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .varbit(VarbitID.POH_HOUSE_LOCATION, 13)
                .varbit(VarbitID.POH_TELE_TOGGLE, 1)
                .inventory(ItemID.LAWRUNE, 1)
                .inventory(ItemID.AIRRUNE, 1)
                .inventory(ItemID.EARTHRUNE, 1)
                .level(Skill.MAGIC, 40))
            .settings(s -> {
                s.setUsePoh(true);
                s.setUseTeleportationSpells(true);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Respawn tele (#502) Prifddinas spawn lands Prifddinas", "routing-issue-502")
            .from(3213, 3428, 0).to(3265, 6077, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .spellbook(Spellbook.ARCEUUS)
                .lock(Unlock.RESPAWN_FALADOR)
                .lock(Unlock.RESPAWN_CAMELOT)
                .lock(Unlock.RESPAWN_EDGEVILLE)
                .lock(Unlock.RESPAWN_FEROX_ENCLAVE)
                .lock(Unlock.RESPAWN_KOUREND_CASTLE)
                .lock(Unlock.RESPAWN_CIVITAS_ILLA_FORTIS)
                .inventory(ItemID.LAWRUNE, 1)
                .inventory(ItemID.SOULRUNE, 1)
                .level(Skill.MAGIC, 34))
            .settings(s -> {
                s.setUseTeleportationSpells(true);
                s.setRespawnPrifddinas(true);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Respawn tele (#502) Prifddinas spawn needs config", "routing-issue-502")
            .from(3213, 3428, 0).to(3265, 6077, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .spellbook(Spellbook.ARCEUUS)
                .lock(Unlock.RESPAWN_FALADOR)
                .lock(Unlock.RESPAWN_CAMELOT)
                .lock(Unlock.RESPAWN_EDGEVILLE)
                .lock(Unlock.RESPAWN_FEROX_ENCLAVE)
                .lock(Unlock.RESPAWN_KOUREND_CASTLE)
                .lock(Unlock.RESPAWN_CIVITAS_ILLA_FORTIS)
                .inventory(ItemID.LAWRUNE, 1)
                .inventory(ItemID.SOULRUNE, 1)
                .level(Skill.MAGIC, 34)
                .quest(Quest.SONG_OF_THE_ELVES, QuestState.NOT_STARTED))
            .settings(s -> {
                s.setUseTeleportationSpells(true);
                s.setBypassVarbitChecks(false);
            })
            .expectUnreachable();

        suite.scenario("Respawn portal (#502) Prifddinas spawn reaches Prif via portal", "routing-issue-502")
            .from(3213, 3428, 0).to(3227, 6095, 0)
            .profile(UNIT_TEST)
            .account(a -> a
                .inventory(ItemID.LAWRUNE, 1)
                .inventory(ItemID.AIRRUNE, 1)
                .inventory(ItemID.EARTHRUNE, 1)
                .level(Skill.MAGIC, 40)
                .quest(Quest.SONG_OF_THE_ELVES, QuestState.FINISHED))
            .settings(s -> {
                s.setUsePoh(true);
                s.setUseTeleportationPortalsPoh(true);
                s.setUseTeleportationPortals(true);
                s.setUseTeleportationSpells(true);
                s.setRespawnPrifddinas(true);
                s.setBypassVarbitChecks(false);
            });

        suite.scenario("Rogues den (#492) ready tele with banked necklace", "routing-issue-492")
            .from(3160, 3486, 0).to(3040, 4969, 1)
            .profile(UNIT_TEST)
            .account(a -> a
                .minigameTeleportUsedAt(0)
                .bank(ItemID.NECKLACE_OF_MINIGAMES_8, 1))
            .settings(bankTeleports())
            .settings(s -> {
                s.setUseTeleportationMinigames(true);
                s.setBypassVarPlayerChecks(false);
            });

        suite.scenario("Rogues den (#492) zero cost keeps banked detour", "routing-issue-332")
            .from(3160, 3486, 0).to(3040, 4969, 1)
            .profile(UNIT_TEST)
            .account(a -> a
                .minigameTeleportUsedAt(0)
                .bank(ItemID.NECKLACE_OF_MINIGAMES_8, 1))
            .settings(bankTeleports())
            .settings(s -> {
                s.setUseTeleportationMinigames(true);
                s.setBypassVarPlayerChecks(false);
                s.setCostBankVisit(0);
            });

        suite.scenario("Rogues den (#492) bank visit cost prefers ready tele", "routing-issue-332")
            .from(3160, 3486, 0).to(3040, 4969, 1)
            .profile(UNIT_TEST)
            .account(a -> a
                .minigameTeleportUsedAt(0)
                .bank(ItemID.NECKLACE_OF_MINIGAMES_8, 1))
            .settings(bankTeleports())
            .settings(s -> {
                s.setUseTeleportationMinigames(true);
                s.setBypassVarPlayerChecks(false);
                s.setCostBankVisit(20);
            });

        // Areas that only seasonal transports used to reach (#674); each must reach a tile inside
        // the area, from the Grand Exchange unless noted.
        seasonalOnlyArea(suite, "Inner Mor Ul Rek (#675) through a hot vent door", "routing-issue-675", 2433, 5120, 0)
            .account(a -> a.equipment(ItemID.TZHAAR_CAPE_FIRE, 1));
        seasonalOnlyArea(suite, "Giant Mole lair (#676) by digging a mole hill", "routing-issue-676", 1760, 5184, 0)
            .account(a -> a.inventory(ItemID.SPADE, 1));
        // UNIT_TEST reaches neither the Kharazi Jungle nor Aldarin, so these start nearby. The winch is
        // reached by the 96 Agility crevice; the obstacle route through the Shaman Caves is not modelled.
        seasonalOnlyArea(suite, "Viyeldi caves (#677) by the Shaman Caves winch", "routing-issue-677", 2407, 4673, 0)
            .from(2781, 2932, 0)
            .settings(s -> s.setUseAgilityShortcuts(true));
        seasonalOnlyArea(suite, "Ruins of Camdozaal (#678) through the Ice Mountain cave", "routing-issue-678", 2922, 5824, 0);
        seasonalOnlyArea(suite, "Hueycoatl arena (#684) down the Darkfrost slope", "routing-issue-684", 1514, 3269, 0);
        seasonalOnlyArea(suite, "Kourend Castle second floor (#685) by the stairs", "routing-issue-685", 1614, 3657, 2);
        seasonalOnlyArea(suite, "Alchemical Society basement (#686) by the stairs", "routing-issue-686", 1388, 9310, 0)
            .from(1388, 2925, 0);
        seasonalOnlyArea(suite, "Gauntlet lobby (#687) through the Gauntlet Portal", "routing-issue-687", 3027, 6118, 1);
        seasonalOnlyArea(suite, "Woodcutting Guild Ent dungeon bank (#688) over the roots", "routing-issue-688", 1554, 9871, 0);
        seasonalOnlyArea(suite, "Legends' Guild top floor (#689) by the ladder", "routing-issue-689", 2733, 3375, 2);
    }

    private static Scenario.Builder seasonalOnlyArea(Suite suite, String name, String category, int x, int y, int plane) {
        return suite.scenario(name, category)
            .from(3164, 3485, 0).to(x, y, plane)
            .profile(UNIT_TEST);
    }
}
