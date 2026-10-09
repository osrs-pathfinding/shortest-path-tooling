package shortestpath.profiles;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.runelite.api.QuestState;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarbitID;
import shortestpath.JewelleryBoxTier;
import shortestpath.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.accounts.Account;
import shortestpath.accounts.canonical.CanonicalAccounts;
import shortestpath.transport.PohMountedItem;
import shortestpath.transport.PohNexusPortal;

/**
 * Every named profile: the four canonical accounts ({@code early}, {@code mid}, {@code end},
 * {@code maxed}) and the dashboard presets ({@code ALL}, {@code NONE}, {@code UNIT_TEST},
 * {@code SEASONAL}, {@code BANK}, {@code BANK_PERM}, {@code INVENTORY},
 * {@code INVENTORY_NON_CONSUMABLE}). Names are case-sensitive for the canonical profiles and
 * case-insensitive for the presets.
 */
public final class Profiles {
    private static final Map<String, Profile> CANONICAL = new LinkedHashMap<>();
    private static final Map<String, Profile> PRESETS = new LinkedHashMap<>();

    public static final Profile EARLY = canonical("early");
    public static final Profile MID = canonical("mid");
    public static final Profile END = canonical("end");
    public static final Profile MAXED = canonical("maxed");

    /** Every teleport item, no bank path. */
    public static final Profile ALL = preset("ALL", 1, settings -> {
        settings.setUseTeleportationItems(TeleportationItem.ALL);
        settings.setIncludeBankPath(false);
    });
    /** No teleport items, no bank path. */
    public static final Profile NONE = preset("NONE", 1, settings -> {
        settings.setUseTeleportationItems(TeleportationItem.NONE);
        settings.setIncludeBankPath(false);
    });
    /** Banked teleport items from a bank holding every item; the Lumbridge elite diary is not done. */
    public static final Profile BANK = preset("BANK", 0, settings -> {
        settings.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK);
        settings.setIncludeBankPath(true);
        settings.setUseTeleportationMinigames(false);
    });
    /** {@link #BANK} with only non-consumable teleport items. */
    public static final Profile BANK_PERM = preset("BANK_PERM", 0, settings -> {
        settings.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE);
        settings.setIncludeBankPath(true);
        settings.setUseTeleportationMinigames(false);
    });
    public static final Profile INVENTORY = preset("INVENTORY", 1, settings -> {
        settings.setUseTeleportationItems(TeleportationItem.INVENTORY);
        settings.setIncludeBankPath(false);
    });
    public static final Profile INVENTORY_NON_CONSUMABLE = preset("INVENTORY_NON_CONSUMABLE", 1, settings -> {
        settings.setUseTeleportationItems(TeleportationItem.INVENTORY_NON_CONSUMABLE);
        settings.setIncludeBankPath(false);
    });
    /**
     * A Demonic Pacts League world: seasonal transports and carried teleport items, wilderness
     * avoided. League area picks are varbits 10662-10667, all locked unless a scenario sets them.
     */
    public static final Profile SEASONAL = preset("SEASONAL", 1, settings -> {
        settings.setUseTeleportationItems(TeleportationItem.INVENTORY);
        settings.setIncludeBankPath(false);
        settings.setUseSeasonalTransports(true);
        settings.setAvoidWilderness(true);
    });
    /**
     * The plugin's unit-test baseline: every transport toggle off, no teleport items, a
     * 30-tick cutoff, and the Lumbridge elite diary not done. Scenarios enable what they test.
     */
    public static final Profile UNIT_TEST = preset("UNIT_TEST", 0, settings -> {
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
    static PluginSettings canonicalSettings(Account.Poh poh, boolean allowTransports) {
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

        settings.setUsePoh(true);
        settings.setUsePohFairyRing(allowTransports && poh.fairyRing);
        settings.setUsePohSpiritTree(allowTransports && poh.spiritTree);
        settings.setUsePohObelisk(allowTransports && poh.obelisk);
        settings.setPohJewelleryBoxTier(JewelleryBoxTier.valueOf(poh.jewelleryBox.name()));
        settings.setPohMountedItems(mountedItems(poh));
        Set<PohNexusPortal> portals = nexusPortals(poh);
        settings.setUseTeleportationPortalsPoh(allowTransports && !portals.isEmpty());
        settings.setPohNexusPortals(portals);

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

    static Set<PohNexusPortal> nexusPortals(Account.Poh poh) {
        if (poh.portals == null) {
            return EnumSet.allOf(PohNexusPortal.class);
        }
        Set<PohNexusPortal> result = EnumSet.noneOf(PohNexusPortal.class);
        for (String displayInfo : poh.portals) {
            PohNexusPortal portal = PohNexusPortal.fromDisplayInfo(displayInfo);
            if (portal == null) {
                throw new IllegalArgumentException("unknown POH portal: " + displayInfo);
            }
            result.add(portal);
        }
        return result;
    }

    static Set<PohMountedItem> mountedItems(Account.Poh poh) {
        Set<PohMountedItem> result = EnumSet.noneOf(PohMountedItem.class);
        if (poh.mountedGlory) result.add(PohMountedItem.GLORY);
        if (poh.mountedXerics) result.add(PohMountedItem.XERICS_TALISMAN);
        if (poh.mountedDigsite) result.add(PohMountedItem.DIGSITE_PENDANT);
        if (poh.mountedMythical) result.add(PohMountedItem.MYTHICAL_CAPE);
        return result;
    }

    /**
     * A dashboard preset. Its account is the dashboard harness baseline: every skill 99 (total
     * 2277), every quest finished, fairy rings unlocked, the given Lumbridge elite diary state,
     * the player standing on the scenario's start tile, and nothing carried or banked unless the
     * scenario adds it ({@code BANK} presets bank every item). Settings start from
     * {@link PluginSettings}'s defaults, which bypass varbit and varplayer checks.
     */
    private static Profile preset(String name, int lumbridgeDiaryElite, Consumer<PluginSettings> settings) {
        Profile profile = new Profile() {
            @Override public String name() { return name; }
            @Override public Setup setup(ProfileContext context) {
                Account.Builder account = Account.builder()
                    .defaultLevel(99)
                    .totalLevel(2277)
                    .defaultQuestState(QuestState.FINISHED)
                    .varbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, lumbridgeDiaryElite)
                    .varbit(VarbitID.FAIRY2_QUEENCURE_QUEST, 100)
                    .location(WorldPointUtil.unpackWorldX(context.start),
                        WorldPointUtil.unpackWorldY(context.start), WorldPointUtil.unpackWorldPlane(context.start));
                if (name.equals("SEASONAL")) {
                    account.world(WorldType.SEASONAL);
                }
                if (name.equals("BANK") || name.equals("BANK_PERM")) {
                    account.universalBank();
                }
                PluginSettings config = new PluginSettings();
                settings.accept(config);
                return new Setup(account, config);
            }
        };
        PRESETS.put(name, profile);
        return profile;
    }

    /** The Lumbridge elite diary varbit a preset starts with, for dashboard run metadata. */
    public static int lumbridgeDiaryElite(Profile profile) {
        Setup setup = profile.setup(new ProfileContext(WorldPointUtil.UNDEFINED, true));
        return setup.account.build().varbits().getOrDefault(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 0);
    }
}
