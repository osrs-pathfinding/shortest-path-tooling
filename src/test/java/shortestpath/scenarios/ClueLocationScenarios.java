package shortestpath.scenarios;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.runelite.api.gameval.ItemID;
import shortestpath.accounts.Account;

/**
 * The {@code clue-locations-full} suite: every clue step in {@code /scenarios/clue-locations.json}
 * with the {@code ALL} preset, which carries nothing. Steps whose only way in needs an item carry
 * the item their clue requires, so the suite tests the item gate rather than skipping the step.
 */
final class ClueLocationScenarios {
    private static final Consumer<Account.Builder> ECTO_TOKENS = a -> a.inventory(ItemID.ECTOTOKEN, 25);
    private static final Consumer<Account.Builder> RING_OF_VISIBILITY = a -> a.equipment(ItemID.FD_RING_VISIBILITY, 1);
    private static final Consumer<Account.Builder> SLED = a -> a.equipment(ItemID.TROLLROMANCE_TOBOGGON_WAXED, 1);
    private static final Consumer<Account.Builder> DIVING_GEAR = a -> a
        .equipment(ItemID.HUNDRED_PIRATE_DIVING_HELMET, 1)
        .equipment(ItemID.HUNDRED_PIRATE_DIVING_BACKPACK, 1);

    /** Route id -> the items its way in needs. */
    private static final Map<String, Consumer<Account.Builder>> ITEMS = Map.of(
        // Dragontooth Island: the Port Phasmatys ghost captain takes 25 ecto-tokens.
        "coordinate-3822-3562-0", ECTO_TOKENS,
        "hot_cold_possible-3803-3532-0", ECTO_TOKENS,
        "hot_cold_possible-3811-3569-0", ECTO_TOKENS,
        // The Shadow Dungeon ladder needs a ring of visibility.
        "cryptic-2744-5116-0", RING_OF_VISIBILITY,
        "emote-2629-5071-0", RING_OF_VISIBILITY,
        // Trollweiss Mountain is reached by sledding down the slope.
        "cryptic-2780-3783-0", SLED,
        "emote-2776-3781-0", SLED,
        // Mogre Camp: Murphy's dive needs a fishbowl helmet and diving apparatus.
        "map-2953-9523-1", DIVING_GEAR);

    private ClueLocationScenarios() { }

    static void define(Suite suite) throws IOException {
        for (Route route : Route.loadResource("/scenarios/clue-locations.json")) {
            Consumer<Account.Builder> items = ITEMS.get(route.getId());
            for (String profile : route.getProfiles() != null ? route.getProfiles() : List.of("ALL")) {
                Scenario.Builder scenario = route.scenario(profile);
                if (items != null) {
                    scenario.account(items);
                }
                suite.add(scenario);
            }
        }
    }
}
