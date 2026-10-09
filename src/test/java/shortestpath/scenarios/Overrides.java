package shortestpath.scenarios;

import java.util.Map;
import java.util.function.Consumer;
import net.runelite.api.gameval.VarbitID;
import shortestpath.TeleportationItem;
import shortestpath.accounts.Account;
import shortestpath.dashboard.DashboardPathfinderConfig;
import shortestpath.leagues.LeagueRegion;

/**
 * Named account and settings overrides that scenarios repeat. Each is a value for
 * {@link Scenario.Builder#account} or {@link Scenario.Builder#settings}:
 *
 * <pre>
 * .account(leagueAreas(LeagueRegion.ASGARNIA, LeagueRegion.KANDARIN))
 * .settings(bankTeleports())
 * </pre>
 */
public final class Overrides {
    private Overrides() { }

    /** The league area ids the plugin understands ({@code LeagueModeState.AREA_VARBIT_TO_REGION}). */
    static final Map<LeagueRegion, Integer> LEAGUE_AREA_IDS = Map.ofEntries(
        Map.entry(LeagueRegion.MISTHALIN, 1),
        Map.entry(LeagueRegion.KARAMJA, 2),
        Map.entry(LeagueRegion.ASGARNIA, 3),
        Map.entry(LeagueRegion.KANDARIN, 4),
        Map.entry(LeagueRegion.MORYTANIA, 5),
        Map.entry(LeagueRegion.DESERT, 6),
        Map.entry(LeagueRegion.TIRANNWN, 7),
        Map.entry(LeagueRegion.FREMENNIK, 8),
        Map.entry(LeagueRegion.WILDERNESS, 11),
        Map.entry(LeagueRegion.KOUREND, 20),
        Map.entry(LeagueRegion.VARLAMORE, 21));

    private static final int[] LEAGUE_AREA_SLOTS = {
        VarbitID.LEAGUE_AREA_SELECTION_1, VarbitID.LEAGUE_AREA_SELECTION_2, VarbitID.LEAGUE_AREA_SELECTION_3,
        VarbitID.LEAGUE_AREA_SELECTION_4, VarbitID.LEAGUE_AREA_SELECTION_5,
    };

    /**
     * League area picks, in pick order, written to {@code LEAGUE_AREA_SELECTION_1} onwards (slot 0
     * is the area every league account starts with). Use with the {@code SEASONAL} profile.
     */
    public static Consumer<Account.Builder> leagueAreas(LeagueRegion... picks) {
        if (picks.length > LEAGUE_AREA_SLOTS.length) {
            throw new IllegalArgumentException("at most " + LEAGUE_AREA_SLOTS.length + " league area picks");
        }
        return account -> {
            for (int i = 0; i < picks.length; i++) {
                Integer id = LEAGUE_AREA_IDS.get(picks[i]);
                if (id == null) {
                    throw new IllegalArgumentException(picks[i] + " cannot be picked");
                }
                account.varbit(LEAGUE_AREA_SLOTS[i], id);
            }
        };
    }

    /**
     * The elite-complete varbit of every diary the achievement diary cape checks: all but Kourend &amp;
     * Kebos. Only the elite flags are set, not the easy to hard ones.
     */
    public static Consumer<Account.Builder> eliteDiaries() {
        return account -> account
            .varbit(VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE, 1)
            .varbit(VarbitID.DESERT_DIARY_ELITE_COMPLETE, 1)
            .varbit(VarbitID.FALADOR_DIARY_ELITE_COMPLETE, 1)
            .varbit(VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE, 1)
            .varbit(VarbitID.KANDARIN_DIARY_ELITE_COMPLETE, 1)
            .varbit(VarbitID.KARAMJA_DIARY_ELITE_COMPLETE, 1)
            .varbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 1)
            .varbit(VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE, 1)
            .varbit(VarbitID.VARROCK_DIARY_ELITE_COMPLETE, 1)
            .varbit(VarbitID.WESTERN_DIARY_ELITE_COMPLETE, 1)
            .varbit(VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE, 1);
    }

    /** Teleport items from the inventory and the bank, with bank visits in the route. */
    public static Consumer<DashboardPathfinderConfig> bankTeleports() {
        return settings -> {
            settings.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK);
            settings.setIncludeBankPath(true);
        };
    }
}
