package shortestpath.profiles;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.runelite.api.QuestState;
import net.runelite.api.WorldType;
import shortestpath.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.accounts.Account;
import shortestpath.accounts.Diary;
import shortestpath.accounts.Poh;
import shortestpath.accounts.Unlock;
import shortestpath.accounts.canonical.CanonicalAccounts;

/**
 * Every named profile: the four canonical accounts ({@code early}, {@code mid}, {@code end},
 * {@code maxed}) and the test presets ({@code ALL}, {@code NONE}, {@code UNIT_TEST},
 * {@code SEASONAL}), which share the {@linkplain #harness harness account} and differ mostly in
 * settings. Names are case-sensitive for the canonical profiles and case-insensitive for the
 * presets.
 */
public final class Profiles {
    private static final Map<String, Profile> CANONICAL = new LinkedHashMap<>();
    private static final Map<String, Profile> PRESETS = new LinkedHashMap<>();

    public static final Profile EARLY = canonical("early");
    public static final Profile MID = canonical("mid");
    public static final Profile END = canonical("end");
    public static final Profile MAXED = canonical("maxed");

    /** Every teleport item, no bank path. */
    public static final Profile ALL = preset("ALL", lumbridgeElite(), settings -> {
        settings.setUseTeleportationItems(TeleportationItem.ALL);
        settings.setIncludeBankPath(false);
    });
    /** No teleport items, no bank path. */
    public static final Profile NONE = preset("NONE", lumbridgeElite(), settings -> {
        settings.setUseTeleportationItems(TeleportationItem.NONE);
        settings.setIncludeBankPath(false);
    });
    /**
     * A Demonic Pacts League world: seasonal transports and carried teleport items, wilderness
     * avoided. League area picks are varbits 10662-10667, all locked unless a scenario sets them.
     */
    public static final Profile SEASONAL = preset("SEASONAL",
            lumbridgeElite().andThen(account -> account.world(WorldType.SEASONAL)), settings -> {
        settings.setUseTeleportationItems(TeleportationItem.INVENTORY);
        settings.setIncludeBankPath(false);
        settings.setUseSeasonalTransports(true);
        settings.setAvoidWilderness(true);
    });
    /**
     * The plugin's unit-test baseline: every transport toggle off, no teleport items, a
     * 30-tick cutoff, and no diaries. Scenarios enable what they test.
     */
    public static final Profile UNIT_TEST = preset("UNIT_TEST", account -> { }, settings -> {
        settings.setAvoidWilderness(false);
        settings.setUseAgilityShortcuts(false);
        settings.setUseBoats(false);
        settings.setUseShips(false);
        settings.setUseFairyRings(false);
        settings.setUseGnomeGliders(false);
        settings.setUseMagicCarpets(false);
        settings.setUseMagicMushtrees(false);
        settings.setUseMinecarts(false);
        settings.setUseQuetzals(false);
        settings.setUseSpiritTrees(false);
        settings.setUseTeleportationLevers(false);
        settings.setUseTeleportationPortals(false);
        settings.setUseTeleportationSpells(false);
        settings.setUseTeleportationMinigames(false);
        settings.setUseWildernessObelisks(false);
        settings.setUseTeleportationItems(TeleportationItem.NONE);
        settings.setIncludeBankPath(false);
        settings.setCalculationCutoff(30);
    });

    private Profiles() { }

    /** The canonical profile or preset called {@code name}. */
    public static Profile get(String name) {
        Profile profile = CANONICAL.get(name);
        if (profile == null && name != null) {
            profile = PRESETS.get(name.toUpperCase(Locale.ROOT));
        }
        if (profile == null) {
            throw new IllegalArgumentException("unknown profile: " + name);
        }
        return profile;
    }

    public static Set<String> canonicalNames() {
        return Collections.unmodifiableSet(CANONICAL.keySet());
    }

    public static Set<String> presetNames() {
        return Collections.unmodifiableSet(PRESETS.keySet());
    }

    private static Profile canonical(String name) {
        Profile profile = new Profile() {
            @Override public String name() { return name; }
            @Override public Setup setup(ProfileContext context) {
                Account account = CanonicalAccounts.account(name);
                return new Setup(account.toBuilder(), canonicalSettings(account.poh(), context.allowTransports));
            }
        };
        CANONICAL.put(name, profile);
        return profile;
    }

    /**
     * The canonical benchmark settings: every transport the route allows, neutral user cost
     * penalties, a 500-tick cutoff, and no harness bypass of varbit or varplayer requirements.
     */
    static PluginSettings canonicalSettings(Poh poh, boolean allowTransports) {
        PluginSettings settings = new PluginSettings();
        settings.setAvoidWilderness(false);
        settings.setUseAgilityShortcuts(allowTransports);
        settings.setUseGrappleShortcuts(allowTransports);
        settings.setUseBoats(allowTransports);
        settings.setUseCanoes(allowTransports);
        settings.setUseCharterShips(allowTransports);
        settings.setUseShips(allowTransports);
        settings.setUseFairyRings(allowTransports);
        settings.setUseGnomeGliders(allowTransports);
        settings.setUseHotAirBalloons(allowTransports);
        settings.setUseMagicCarpets(allowTransports);
        settings.setUseMagicMushtrees(allowTransports);
        settings.setUseMinecarts(allowTransports);
        settings.setUseQuetzals(allowTransports);
        settings.setUseSpiritTrees(allowTransports);
        settings.setUseTeleportationItems(allowTransports
            ? TeleportationItem.INVENTORY_AND_BANK : TeleportationItem.NONE);
        settings.setUseTeleportationLevers(allowTransports);
        settings.setUseTeleportationPortals(allowTransports);
        settings.setUseTeleportationSpells(allowTransports);
        settings.setUseTeleportationMinigames(allowTransports);
        settings.setUseWildernessObelisks(allowTransports);
        settings.setUseSeasonalTransports(false);
        settings.setIncludeBankPath(true);
        settings.setBypassVarbitChecks(false);
        settings.setBypassVarPlayerChecks(false);
        settings.setCurrencyThreshold(Integer.MAX_VALUE);
        settings.setCalculationCutoff(500);

        settings.applyPoh(poh, allowTransports);

        // User preference penalties are neutral; intrinsic transport costs stay in plugin data.
        settings.setCostAgilityShortcuts(0);
        settings.setCostGrappleShortcuts(0);
        settings.setCostBoats(0);
        settings.setCostCanoes(0);
        settings.setCostCharterShips(0);
        settings.setCostShips(0);
        settings.setCostFairyRings(0);
        settings.setCostGnomeGliders(0);
        settings.setCostHotAirBalloons(0);
        settings.setCostMagicCarpets(0);
        settings.setCostMagicMushtrees(0);
        settings.setCostMinecarts(0);
        settings.setCostQuetzals(0);
        settings.setCostQuetzalWhistle(0);
        settings.setCostSpiritTrees(0);
        settings.setCostNonConsumableTeleportationItems(0);
        settings.setCostConsumableTeleportationItems(0);
        settings.setCostTeleportationBoxes(0);
        settings.setCostTeleportationLevers(0);
        settings.setCostTeleportationPortals(0);
        settings.setCostTeleportationSpells(0);
        settings.setCostTeleportationMinigames(0);
        settings.setCostWildernessObelisks(0);
        settings.setCostSeasonalTransports(0);
        return settings;
    }

    /**
     * The account every preset starts from: every skill 99 (total 2277), every quest finished,
     * fairy rings unlocked, no diaries, standing on the scenario's start tile, and nothing carried
     * or banked unless the scenario adds it.
     */
    private static Account.Builder harness(ProfileContext context) {
        return Account.builder()
            .defaultLevel(99)
            .totalLevel(2277)
            .defaultQuestState(QuestState.FINISHED)
            .unlock(Unlock.FAIRY_RINGS)
            .location(WorldPointUtil.unpackWorldX(context.start),
                WorldPointUtil.unpackWorldY(context.start), WorldPointUtil.unpackWorldPlane(context.start));
    }

    private static Consumer<Account.Builder> lumbridgeElite() {
        return account -> account.diary(Diary.LUMBRIDGE_DRAYNOR, Diary.Tier.ELITE);
    }

    /**
     * A test preset: the {@linkplain #harness harness account} with {@code account} applied, and
     * settings from {@link PluginSettings}'s defaults (which bypass varbit and varplayer checks)
     * with {@code settings} applied.
     */
    private static Profile preset(String name, Consumer<Account.Builder> account,
            Consumer<PluginSettings> settings) {
        Profile profile = new Profile() {
            @Override public String name() { return name; }
            @Override public Setup setup(ProfileContext context) {
                Account.Builder builder = harness(context);
                account.accept(builder);
                PluginSettings config = new PluginSettings();
                settings.accept(config);
                return new Setup(builder, config);
            }
        };
        PRESETS.put(name, profile);
        return profile;
    }
}
