package shortestpath.scenarios;

import static shortestpath.profiles.Profiles.SEASONAL;
import static shortestpath.scenarios.Scenario.scenario;

import java.util.List;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.TeleportationItem;

/**
 * Demonic Pacts League routes, including the briefcase teleports. Suite {@code seasonal_briefcase_routes}; exact lengths in
 * {@code scenarios/expected-lengths/seasonal_briefcase_routes.json}.
 */
final class SeasonalBriefcaseScenarios {
    private SeasonalBriefcaseScenarios() { }

    static List<Scenario.Builder> all() {
        return List.of(
            scenario("Civitas → Hunter Guild (Varlamore walk)", "walk")
                .from(1735, 3093, 0).to(1542, 3039, 0)
                .profile(SEASONAL),
            scenario("Civitas → Mor Ul Rek (Karamja free unlock)", "briefcase")
                .from(1735, 3093, 0).to(2451, 5179, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 2)
                    .inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1)),
            scenario("Civitas → Mor Ul Rek (Karamja LOCKED — should fail)", "briefcase")
                .from(1735, 3093, 0).to(2451, 5179, 0)
                .profile(SEASONAL)
                .account(a -> a.inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1))
                .expectUnreachable(),
            scenario("Civitas → Falador East (Asgarnia pick)", "briefcase")
                .from(1735, 3093, 0).to(3010, 3354, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 3)
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_2, 4)
                    .inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1)),
            scenario("Civitas → Catherby (Kandarin pick)", "briefcase")
                .from(1735, 3093, 0).to(2807, 3442, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 3)
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_2, 4)
                    .inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1)),
            scenario("Civitas → Catherby (briefcase only in bank)", "briefcase")
                .from(1735, 3093, 0).to(2807, 3442, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 3)
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_2, 4)
                    .bank(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1))
                .settings(s -> {
                    s.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK);
                    s.setIncludeBankPath(true);
                }),
            scenario("Civitas → Kourend Castle (Kourend pick)", "briefcase")
                .from(1735, 3093, 0).to(1610, 3680, 2)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 3)
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_2, 20)
                    .inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1)),
            scenario("Civitas → Prifddinas (Tirannwn pick)", "briefcase")
                .from(1735, 3093, 0).to(3255, 6104, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 3)
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_2, 7)
                    .inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1)),
            scenario("Civitas → Varrock (Misthalin best-effort detour)", "briefcase")
                .from(1735, 3093, 0).to(3186, 3438, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 3)
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_2, 4)
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_3, 8)
                    .inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1)),
            scenario("Civitas → Mistrock (Varlamore briefcase deposit box)", "briefcase")
                .from(1735, 3093, 0).to(1383, 2869, 0)
                .profile(SEASONAL)
                .account(a -> a.inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1)),
            scenario("Civitas → Lletya (Tirannwn LOCKED — should fail)", "briefcase")
                .from(1735, 3093, 0).to(2350, 3162, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 3)
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_2, 4)
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_3, 8)
                    .inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1))
                .expectUnreachable(),
            scenario("Maxed picks → Lletya (Tirannwn)", "briefcase")
                .from(1735, 3093, 0).to(2350, 3162, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 3)
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_2, 7)
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_3, 8)
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_4, 10)
                    .inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1)),
            scenario("Civitas → Volcanic Mine (Fossil Island always blocked)", "briefcase")
                .from(1735, 3093, 0).to(3820, 3808, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 3)
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_2, 7)
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_3, 8)
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_4, 10)
                    .inventory(ItemID.LEAGUE_BANK_HEIST_TELEPORT, 1))
                .expectUnreachable(),
            scenario("Civitas → Trollheim climb via Map of Alacrity (Asgarnia override)", "briefcase")
                .from(1735, 3093, 0).to(2946, 3678, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 3)
                    .inventory(ItemID.LEAGUE_AGILITY_MAP, 1)),
            scenario("Trollheim climb (Wilderness unlocked / Asgarnia LOCKED — should fail)", "briefcase")
                .from(1735, 3093, 0).to(2946, 3678, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 11)
                    .inventory(ItemID.LEAGUE_AGILITY_MAP, 1))
                .expectUnreachable(),
            scenario("Civitas → Lighthouse via Fairy Mushroom (Fremennik chunk)", "briefcase")
                .from(1735, 3093, 0).to(2503, 3636, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 8)
                    .inventory(ItemID.LEAGUE_TRAILBLAZER_FAIRYS_FLIGHT_TELEPORT, 1)),
            scenario("Lighthouse fairy mushroom (Fremennik LOCKED — should fail)", "briefcase")
                .from(1735, 3093, 0).to(2503, 3636, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 4)
                    .inventory(ItemID.LEAGUE_TRAILBLAZER_FAIRYS_FLIGHT_TELEPORT, 1))
                .expectUnreachable(),
            scenario("Civitas → Poison Waste via Fairy Mushroom (Kandarin override)", "briefcase")
                .from(1735, 3093, 0).to(2213, 3099, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 4)
                    .inventory(ItemID.LEAGUE_TRAILBLAZER_FAIRYS_FLIGHT_TELEPORT, 1)),
            scenario("Poison Waste fairy mushroom (Kandarin LOCKED — reaches via Tirannwn walk)", "briefcase")
                .from(1735, 3093, 0).to(2213, 3099, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 7)
                    .inventory(ItemID.LEAGUE_TRAILBLAZER_FAIRYS_FLIGHT_TELEPORT, 1)),
            scenario("Poison Waste fairy mushroom (Kandarin + Tirannwn LOCKED — should fail)", "briefcase")
                .from(1735, 3093, 0).to(2213, 3099, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 8)
                    .inventory(ItemID.LEAGUE_TRAILBLAZER_FAIRYS_FLIGHT_TELEPORT, 1))
                .expectUnreachable(),
            scenario("Civitas → Barbarian Outpost Basalt causeway via Map of Alacrity (Kandarin override)", "briefcase")
                .from(1735, 3093, 0).to(2522, 3595, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 4)
                    .inventory(ItemID.LEAGUE_AGILITY_MAP, 1)),
            scenario("Basalt causeway (Fremennik unlocked / Kandarin LOCKED — reaches via Fremennik walk)", "briefcase")
                .from(1735, 3093, 0).to(2522, 3595, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 8)
                    .inventory(ItemID.LEAGUE_AGILITY_MAP, 1)),
            scenario("Basalt causeway (Fremennik + Kandarin LOCKED — should fail)", "briefcase")
                .from(1735, 3093, 0).to(2522, 3595, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 11)
                    .inventory(ItemID.LEAGUE_AGILITY_MAP, 1))
                .expectUnreachable(),
            scenario("Civitas → Sinclair Mansion Log balance via Map of Alacrity (Kandarin override)", "briefcase")
                .from(1735, 3093, 0).to(2722, 3592, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 4)
                    .inventory(ItemID.LEAGUE_AGILITY_MAP, 1)),
            scenario("Sinclair Mansion Log balance (Fremennik unlocked / Kandarin LOCKED — reaches via Fremennik walk)", "briefcase")
                .from(1735, 3093, 0).to(2722, 3592, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 8)
                    .inventory(ItemID.LEAGUE_AGILITY_MAP, 1)),
            scenario("Sinclair Mansion Log balance (Fremennik + Kandarin LOCKED — should fail)", "briefcase")
                .from(1735, 3093, 0).to(2722, 3592, 0)
                .profile(SEASONAL)
                .account(a -> a
                    .varbit(VarbitID.LEAGUE_AREA_SELECTION_1, 11)
                    .inventory(ItemID.LEAGUE_AGILITY_MAP, 1))
                .expectUnreachable()
        );
    }
}
