package shortestpath.scenarios;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import shortestpath.profiles.Profiles;

/**
 * The canonical corpus ({@code corpus/corpus/routes-v1.json}) as the {@code canonical} suite: every
 * route with every canonical profile, named {@code <route id>/<profile>} like the benchmark cases
 * and tagged with the route's tiers ({@code smoke}, {@code standard}, {@code full}).
 */
public final class CanonicalScenarios {
    private CanonicalScenarios() { }

    /** The corpus directory: {@code -Dbenchmark.corpusDir}, default the repository's {@code corpus/}. */
    public static Path defaultCorpusDir() {
        return Path.of(System.getProperty("benchmark.corpusDir", "corpus"));
    }

    public static List<Route> routes(Path corpusDir) throws IOException {
        return Route.load(corpusDir.resolve("corpus/routes-v1.json"));
    }

    static void define(Suite suite, Path corpusDir) throws IOException {
        Suites.addRoutes(suite, routes(corpusDir), List.copyOf(Profiles.canonicalNames()));
    }
}
