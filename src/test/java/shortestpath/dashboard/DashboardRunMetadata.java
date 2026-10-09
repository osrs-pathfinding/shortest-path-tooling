package shortestpath.dashboard;

import shortestpath.profiles.PluginSettings;

/**
 * Fills dashboard-specific metadata fields on {@link PathfinderDashboardModels.RunRecord}s.
 * <p>
 * Replaces the former {@code ReachabilityRunMetadata} class which depended on the now-deleted
 * {@code RouteMode} / reachability-mode hierarchy.
 */
public final class DashboardRunMetadata {

    private DashboardRunMetadata() {
    }

    /**
     * @param run                   the record to annotate
     * @param presetId              preset name that was active for this run (e.g. {@code "ALL"})
     * @param config                the config snapshot after applying the preset and overrides
     * @param lumbridgeDiaryEliteStub the account's {@code LUMBRIDGE_DIARY_ELITE_COMPLETE} varbit during
     *                              the run ({@code 0} or {@code 1})
     */
    public static void apply(
            PathfinderDashboardModels.RunRecord run,
            String presetId,
            PluginSettings config,
            int lumbridgeDiaryEliteStub) {
        run.routeModeId = presetId;
        run.teleportationItems = config.useTeleportationItems().name();
        run.includeBankPath = config.includeBankPath();
        run.lumbridgeDiaryEliteStub = lumbridgeDiaryEliteStub;
        run.useTeleportationMinigames = config.useTeleportationMinigames();
    }
}
