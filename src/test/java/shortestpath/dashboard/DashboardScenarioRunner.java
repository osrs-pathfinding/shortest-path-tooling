package shortestpath.dashboard;

import java.util.Map;
import net.runelite.api.Skill;
import shortestpath.JewelleryBoxTier;
import shortestpath.TeleportationItem;
import shortestpath.pathfinder.PathfinderBackend;
import shortestpath.pathfinder.TestPathfinderConfig;
import shortestpath.profiles.CompiledAccount;
import shortestpath.profiles.Profile;
import shortestpath.profiles.ProfileContext;
import shortestpath.profiles.Profiles;
import shortestpath.profiles.Setup;

/**
 * Applies a {@link DashboardScenario} row to its preset {@link Profile} and compiles the result.
 * The row's columns override the preset's account (varbits, varplayers, items, skills, quests)
 * and settings ({@code config_overrides}); later columns win over the preset.
 */
public final class DashboardScenarioRunner {

    /**
     * The result of applying a scenario.  Carries both the pathfinder config (for running the
     * algorithm) and the dashboard config snapshot (for metadata like
     * {@link DashboardRunMetadata#apply}).
     */
    public static final class ApplyResult {
        public final TestPathfinderConfig pathfinderConfig;
        public final DashboardPathfinderConfig dashboardConfig;
        public final int lumbridgeDiaryEliteStub;

        private ApplyResult(
                TestPathfinderConfig pathfinderConfig,
                DashboardPathfinderConfig dashboardConfig,
                int lumbridgeDiaryEliteStub) {
            this.pathfinderConfig = pathfinderConfig;
            this.dashboardConfig = dashboardConfig;
            this.lumbridgeDiaryEliteStub = lumbridgeDiaryEliteStub;
        }
    }

    private DashboardScenarioRunner() {
    }

    /**
     * Apply {@code scenario} to produce a fresh {@link ApplyResult}. The config refreshes on the
     * calling thread, so run the pathfinder on the same thread.
     */
    public static ApplyResult apply(DashboardScenario scenario) {
        Profile profile = Profiles.get(scenario.getPreset());
        Setup setup = profile.setup(new ProfileContext(scenario.getStartPoint(), true));

        scenario.getVarbits().forEach(setup.account::varbit);
        scenario.getVarplayers().forEach(setup.account::varplayer);
        for (DashboardScenario.ItemQuantity item : scenario.getInventory()) {
            setup.account.inventory(item.itemId, item.quantity);
        }
        for (DashboardScenario.ItemQuantity item : scenario.getEquipment()) {
            setup.account.equipment(item.itemId, item.quantity);
        }
        // BANK presets already bank every item; a row's bank column only matters elsewhere.
        if (!setup.account.build().hasUniversalBank()) {
            for (DashboardScenario.ItemQuantity item : scenario.getBank()) {
                setup.account.bank(item.itemId, item.quantity);
            }
        }
        for (Map.Entry<String, Integer> entry : scenario.getSkillLevels().entrySet()) {
            setup.account.level(Skill.valueOf(entry.getKey()), entry.getValue());
        }
        scenario.getQuestStates().forEach(setup.account::quest);
        applyConfigOverrides(scenario.getConfigOverrides(), setup.settings);

        CompiledAccount compiled = setup.compile();
        return new ApplyResult(compiled.getConfig(), compiled.getSettings(), Profiles.lumbridgeDiaryElite(profile));
    }

    /**
     * Dispatches {@code config_overrides} entries to setters on {@code config}.
     * Keys are the camelCase setter name without the "set" prefix (e.g. {@code "useFairyRings"}).
     */
    private static void applyConfigOverrides(Map<String, String> overrides, DashboardPathfinderConfig config) {
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            switch (key) {
                case "avoidWilderness": config.setAvoidWilderness(parseBoolean(value)); break;
                case "useAgilityShortcuts": config.setUseAgilityShortcuts(parseBoolean(value)); break;
                case "useGrappleShortcuts": config.setUseGrappleShortcuts(parseBoolean(value)); break;
                case "useBoats": config.setUseBoats(parseBoolean(value)); break;
                case "useCanoes": config.setUseCanoes(parseBoolean(value)); break;
                case "useCharterShips": config.setUseCharterShips(parseBoolean(value)); break;
                case "useShips": config.setUseShips(parseBoolean(value)); break;
                case "useFairyRings": config.setUseFairyRings(parseBoolean(value)); break;
                case "useGnomeGliders": config.setUseGnomeGliders(parseBoolean(value)); break;
                case "useHotAirBalloons": config.setUseHotAirBalloons(parseBoolean(value)); break;
                case "useMagicCarpets": config.setUseMagicCarpets(parseBoolean(value)); break;
                case "useMagicMushtrees": config.setUseMagicMushtrees(parseBoolean(value)); break;
                case "useMinecarts": config.setUseMinecarts(parseBoolean(value)); break;
                case "useQuetzals": config.setUseQuetzals(parseBoolean(value)); break;
                case "useSpiritTrees": config.setUseSpiritTrees(parseBoolean(value)); break;
                case "useTeleportationItems": config.setUseTeleportationItems(TeleportationItem.valueOf(value)); break;
                case "useTeleportationLevers": config.setUseTeleportationLevers(parseBoolean(value)); break;
                case "useTeleportationPortals": config.setUseTeleportationPortals(parseBoolean(value)); break;
                case "useTeleportationSpells": config.setUseTeleportationSpells(parseBoolean(value)); break;
                case "useTeleportationSpellsHome": config.setUseTeleportationSpellsHome(parseBoolean(value)); break;
                case "useTeleportationMinigames": config.setUseTeleportationMinigames(parseBoolean(value)); break;
                case "useWildernessObelisks": config.setUseWildernessObelisks(parseBoolean(value)); break;
                case "useSeasonalTransports": config.setUseSeasonalTransports(parseBoolean(value)); break;
                case "includeBankPath": config.setIncludeBankPath(parseBoolean(value)); break;
                case "bypassVarbitChecks": config.setBypassVarbitChecks(parseBoolean(value)); break;
                case "bypassVarPlayerChecks": config.setBypassVarPlayerChecks(parseBoolean(value)); break;
                case "currencyThreshold": config.setCurrencyThreshold(Integer.parseInt(value)); break;
                case "calculationCutoff": config.setCalculationCutoff(Integer.parseInt(value)); break;
                case "pathfinderBackend": config.setPathfinderBackend(PathfinderBackend.valueOf(value)); break;
                case "exactHeuristicWeight": config.setExactHeuristicWeight(Integer.parseInt(value)); break;
                case "usePoh": config.setUsePoh(parseBoolean(value)); break;
                case "usePohFairyRing": config.setUsePohFairyRing(parseBoolean(value)); break;
                case "usePohSpiritTree": config.setUsePohSpiritTree(parseBoolean(value)); break;
                case "useTeleportationPortalsPoh": config.setUseTeleportationPortalsPoh(parseBoolean(value)); break;
                case "usePohMountedItems": config.setUsePohMountedItems(parseBoolean(value)); break;
                case "usePohObelisk": config.setUsePohObelisk(parseBoolean(value)); break;
                case "costConsumableTeleportationItems": config.setCostConsumableTeleportationItems(Integer.parseInt(value)); break;
                case "costBankVisit": config.setCostBankVisit(Integer.parseInt(value)); break;
                case "respawnPrifddinas": config.setRespawnPrifddinas(parseBoolean(value)); break;
                case "unlockCanoeAxe": config.setUnlockCanoeAxe(parseBoolean(value)); break;
                case "unlockXericsHonour": config.setUnlockXericsHonour(parseBoolean(value)); break;
                case "unlockDragontoothPassage": config.setUnlockDragontoothPassage(parseBoolean(value)); break;
                case "costNonConsumableTeleportationItems": config.setCostNonConsumableTeleportationItems(Integer.parseInt(value)); break;
                case "costAgilityShortcuts": config.setCostAgilityShortcuts(Integer.parseInt(value)); break;
                case "costGrappleShortcuts": config.setCostGrappleShortcuts(Integer.parseInt(value)); break;
                case "costFairyRings": config.setCostFairyRings(Integer.parseInt(value)); break;
                case "costBoats": config.setCostBoats(Integer.parseInt(value)); break;
                case "costCanoes": config.setCostCanoes(Integer.parseInt(value)); break;
                case "costCharterShips": config.setCostCharterShips(Integer.parseInt(value)); break;
                case "costShips": config.setCostShips(Integer.parseInt(value)); break;
                case "costGnomeGliders": config.setCostGnomeGliders(Integer.parseInt(value)); break;
                case "costHotAirBalloons": config.setCostHotAirBalloons(Integer.parseInt(value)); break;
                case "costMagicCarpets": config.setCostMagicCarpets(Integer.parseInt(value)); break;
                case "costMagicMushtrees": config.setCostMagicMushtrees(Integer.parseInt(value)); break;
                case "costMinecarts": config.setCostMinecarts(Integer.parseInt(value)); break;
                case "costQuetzals": config.setCostQuetzals(Integer.parseInt(value)); break;
                case "costSpiritTrees": config.setCostSpiritTrees(Integer.parseInt(value)); break;
                case "costTeleportationLevers": config.setCostTeleportationLevers(Integer.parseInt(value)); break;
                case "costTeleportationPortals": config.setCostTeleportationPortals(Integer.parseInt(value)); break;
                case "costTeleportationSpells": config.setCostTeleportationSpells(Integer.parseInt(value)); break;
                case "costTeleportationSpellsHome": config.setCostTeleportationSpellsHome(Integer.parseInt(value)); break;
                case "costTeleportationMinigames": config.setCostTeleportationMinigames(Integer.parseInt(value)); break;
                case "costWildernessObelisks": config.setCostWildernessObelisks(Integer.parseInt(value)); break;
                case "costSeasonalTransports": config.setCostSeasonalTransports(Integer.parseInt(value)); break;
                case "costQuetzalWhistle": config.setCostQuetzalWhistle(Integer.parseInt(value)); break;
                case "costTeleportationBoxes": config.setCostTeleportationBoxes(Integer.parseInt(value)); break;
                case "pohJewelleryBoxTier": config.setPohJewelleryBoxTier(JewelleryBoxTier.valueOf(value)); break;
                case "unreachableTargetDistanceThreshold": config.setUnreachableTargetDistance(Integer.parseInt(value)); break;
                case "collisionAwareBlockedTargets": config.setCollisionAwareBlockedTargets(parseBoolean(value)); break;
                case "builtTeleportationBoxes": config.setBuiltTeleportationBoxes(value); break;
                case "builtTeleportationPortalsPoh": config.setBuiltTeleportationPortalsPoh(value); break;
                default:
                    throw new IllegalArgumentException("Unknown config_override key: '" + key + "'");
            }
        }
    }

    private static boolean parseBoolean(String value) {
        return Boolean.parseBoolean(value.trim());
    }
}
