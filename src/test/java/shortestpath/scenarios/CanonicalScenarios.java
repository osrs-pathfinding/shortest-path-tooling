package shortestpath.scenarios;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import shortestpath.benchmark.canonical.CanonicalCorpusLoader;
import shortestpath.benchmark.canonical.CanonicalRoute;
import shortestpath.profiles.Profiles;

/**
 * The canonical corpus ({@code corpus/corpus/routes-v1.json}) as suites: every route of a tier
 * with every canonical profile, named {@code <route id>/<profile>} like the benchmark cases.
 * A profile in the route's {@code negativeProfiles} must not reach it.
 */
final class CanonicalScenarios {
    private CanonicalScenarios() { }

    /** The corpus directory: {@code -Dbenchmark.corpusDir}, default the repository's {@code corpus/}. */
    static Path corpusDir() {
        return Path.of(System.getProperty("benchmark.corpusDir", "corpus"));
    }

    static void define(Suite suite, String tier) {
        try {
            for (CanonicalRoute route : CanonicalCorpusLoader.loadRoutes(
                    corpusDir().resolve("corpus/routes-v1.json"))) {
                if (!route.getTiers().contains(tier)) {
                    continue;
                }
                for (String profile : CanonicalCorpusLoader.PROFILES) {
                    int[] start = route.getStart();
                    int[] target = route.getTarget();
                    suite.scenario(route.getId() + "/" + profile, category(route.getId()))
                        .description(route.getName())
                        .from(start[0], start[1], start[2]).to(target[0], target[1], target[2])
                        .profile(Profiles.get(profile))
                        .allowTransports(route.isAllowTransports())
                        .expectReachable(!route.getNegativeProfiles().contains(profile));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code gps-natural-0012} → {@code gps-natural}: the route family, for dashboard grouping. */
    static String category(String routeId) {
        return routeId.replaceFirst("-\\d+$", "");
    }
}
