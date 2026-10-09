package shortestpath.scenarios;

import static shortestpath.profiles.Profiles.SEASONAL;
import static shortestpath.scenarios.Overrides.bankTeleports;
import static shortestpath.scenarios.Overrides.leagueAreas;

import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.leagues.LeagueRegion;

/**
 * Demonic Pacts League routes, including the briefcase teleports. Suite {@code seasonal-briefcase-routes}; exact lengths in
 * {@code scenarios/expected-lengths/seasonal-briefcase-routes.json}.
 */
final class SeasonalBriefcaseScenarios {
    private SeasonalBriefcaseScenarios() { }

    static void define(Suite suite) {
        suite.scenario("Civitas → Hunter Guild (Varlamore walk)", "walk")
            .from(1735, 3093, 0).to(1542, 3039, 0)
            .profile(SEASONAL);

        suite.scenario("Civitas → Mor Ul Rek (Karamja free unlock)", "briefcase")
            .from(1735, 3093, 0).to(2451, 5179, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.KARAMJA))
            .account(a -> a.inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1));

        suite.scenario("Civitas → Mor Ul Rek (Karamja LOCKED — should fail)", "briefcase")
            .from(1735, 3093, 0).to(2451, 5179, 0)
            .profile(SEASONAL)
            .account(a -> a.inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1))
            .expectUnreachable();

        suite.scenario("Civitas → Falador East (Asgarnia pick)", "briefcase")
            .from(1735, 3093, 0).to(3010, 3354, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.ASGARNIA, LeagueRegion.KANDARIN))
            .account(a -> a.inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1));

        suite.scenario("Civitas → Catherby (Kandarin pick)", "briefcase")
            .from(1735, 3093, 0).to(2807, 3442, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.ASGARNIA, LeagueRegion.KANDARIN))
            .account(a -> a.inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1));

        suite.scenario("Civitas → Catherby (briefcase only in bank)", "briefcase")
            .from(1735, 3093, 0).to(2807, 3442, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.ASGARNIA, LeagueRegion.KANDARIN))
            .account(a -> a.bank(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1))
            .settings(bankTeleports());

        suite.scenario("Civitas → Kourend Castle (Kourend pick)", "briefcase")
            .from(1735, 3093, 0).to(1610, 3680, 2)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.ASGARNIA, LeagueRegion.KOUREND))
            .account(a -> a.inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1));

        suite.scenario("Civitas → Prifddinas (Tirannwn pick)", "briefcase")
            .from(1735, 3093, 0).to(3255, 6104, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.ASGARNIA, LeagueRegion.TIRANNWN))
            .account(a -> a.inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1));

        suite.scenario("Civitas → Varrock (Misthalin best-effort detour)", "briefcase")
            .from(1735, 3093, 0).to(3186, 3438, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.ASGARNIA, LeagueRegion.KANDARIN, LeagueRegion.FREMENNIK))
            .account(a -> a.inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1));

        suite.scenario("Civitas → Mistrock (Varlamore briefcase deposit box)", "briefcase")
            .from(1735, 3093, 0).to(1383, 2869, 0)
            .profile(SEASONAL)
            .account(a -> a.inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1));

        suite.scenario("Civitas → Lletya (Tirannwn LOCKED — should fail)", "briefcase")
            .from(1735, 3093, 0).to(2350, 3162, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.ASGARNIA, LeagueRegion.KANDARIN, LeagueRegion.FREMENNIK))
            .account(a -> a.inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1))
            .expectUnreachable();

        // League area id 10 is not a region the plugin knows, so the slots stay raw.
        suite.scenario("Maxed picks → Lletya (Tirannwn)", "briefcase")
            .from(1735, 3093, 0).to(2350, 3162, 0)
            .profile(SEASONAL)
            .account(a -> a
                .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 3)
                .varbit(VarbitID.LEAGUE_AREA_SELECTION_2, 7)
                .varbit(VarbitID.LEAGUE_AREA_SELECTION_3, 8)
                .varbit(VarbitID.LEAGUE_AREA_SELECTION_4, 10)
                .inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1));

        // League area id 10 is not a region the plugin knows, so the slots stay raw.
        suite.scenario("Civitas → Volcanic Mine (Fossil Island always blocked)", "briefcase")
            .from(1735, 3093, 0).to(3820, 3808, 0)
            .profile(SEASONAL)
            .account(a -> a
                .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 3)
                .varbit(VarbitID.LEAGUE_AREA_SELECTION_2, 7)
                .varbit(VarbitID.LEAGUE_AREA_SELECTION_3, 8)
                .varbit(VarbitID.LEAGUE_AREA_SELECTION_4, 10)
                .inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1))
            .expectUnreachable();

        suite.scenario("Civitas → Trollheim climb via Map of Alacrity (Asgarnia override)", "briefcase")
            .from(1735, 3093, 0).to(2946, 3678, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.ASGARNIA))
            .account(a -> a.inventory(ItemID.LEAGUE_AGILITY_MAP, 1));

        suite.scenario("Trollheim climb (Wilderness unlocked / Asgarnia LOCKED — should fail)", "briefcase")
            .from(1735, 3093, 0).to(2946, 3678, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.WILDERNESS))
            .account(a -> a.inventory(ItemID.LEAGUE_AGILITY_MAP, 1))
            .expectUnreachable();

        suite.scenario("Civitas → Lighthouse via Fairy Mushroom (Fremennik chunk)", "briefcase")
            .from(1735, 3093, 0).to(2503, 3636, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.FREMENNIK))
            .account(a -> a.inventory(ItemID.LEAGUE_TRAILBLAZER_FAIRYS_FLIGHT_TELEPORT, 1));

        suite.scenario("Lighthouse fairy mushroom (Fremennik LOCKED — should fail)", "briefcase")
            .from(1735, 3093, 0).to(2503, 3636, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.KANDARIN))
            .account(a -> a.inventory(ItemID.LEAGUE_TRAILBLAZER_FAIRYS_FLIGHT_TELEPORT, 1))
            .expectUnreachable();

        suite.scenario("Civitas → Poison Waste via Fairy Mushroom (Kandarin override)", "briefcase")
            .from(1735, 3093, 0).to(2213, 3099, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.KANDARIN))
            .account(a -> a.inventory(ItemID.LEAGUE_TRAILBLAZER_FAIRYS_FLIGHT_TELEPORT, 1));

        suite.scenario("Poison Waste fairy mushroom (Kandarin LOCKED — reaches via Tirannwn walk)", "briefcase")
            .from(1735, 3093, 0).to(2213, 3099, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.TIRANNWN))
            .account(a -> a.inventory(ItemID.LEAGUE_TRAILBLAZER_FAIRYS_FLIGHT_TELEPORT, 1));

        suite.scenario("Poison Waste fairy mushroom (Kandarin + Tirannwn LOCKED — should fail)", "briefcase")
            .from(1735, 3093, 0).to(2213, 3099, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.FREMENNIK))
            .account(a -> a.inventory(ItemID.LEAGUE_TRAILBLAZER_FAIRYS_FLIGHT_TELEPORT, 1))
            .expectUnreachable();

        suite.scenario("Civitas → Barbarian Outpost Basalt causeway via Map of Alacrity (Kandarin override)", "briefcase")
            .from(1735, 3093, 0).to(2522, 3595, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.KANDARIN))
            .account(a -> a.inventory(ItemID.LEAGUE_AGILITY_MAP, 1));

        suite.scenario("Basalt causeway (Fremennik unlocked / Kandarin LOCKED — reaches via Fremennik walk)", "briefcase")
            .from(1735, 3093, 0).to(2522, 3595, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.FREMENNIK))
            .account(a -> a.inventory(ItemID.LEAGUE_AGILITY_MAP, 1));

        suite.scenario("Basalt causeway (Fremennik + Kandarin LOCKED — should fail)", "briefcase")
            .from(1735, 3093, 0).to(2522, 3595, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.WILDERNESS))
            .account(a -> a.inventory(ItemID.LEAGUE_AGILITY_MAP, 1))
            .expectUnreachable();

        suite.scenario("Civitas → Sinclair Mansion Log balance via Map of Alacrity (Kandarin override)", "briefcase")
            .from(1735, 3093, 0).to(2722, 3592, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.KANDARIN))
            .account(a -> a.inventory(ItemID.LEAGUE_AGILITY_MAP, 1));

        suite.scenario("Sinclair Mansion Log balance (Fremennik unlocked / Kandarin LOCKED — reaches via Fremennik walk)", "briefcase")
            .from(1735, 3093, 0).to(2722, 3592, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.FREMENNIK))
            .account(a -> a.inventory(ItemID.LEAGUE_AGILITY_MAP, 1));

        suite.scenario("Sinclair Mansion Log balance (Fremennik + Kandarin LOCKED — should fail)", "briefcase")
            .from(1735, 3093, 0).to(2722, 3592, 0)
            .profile(SEASONAL)
            .account(leagueAreas(LeagueRegion.WILDERNESS))
            .account(a -> a.inventory(ItemID.LEAGUE_AGILITY_MAP, 1))
            .expectUnreachable();
    }
}
