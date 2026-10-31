package shortestpath.scenarios;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The canonical corpus directory (default the repository's {@code corpus/}, or {@code -Dcorpus.dir})
 * and its routes, {@code corpus/routes-v1.json}; without the directory, the copy this project packages
 * ({@code /scenarios/corpus/routes-v1.json}), for consumers outside the repository. The {@code canonical} suite runs every route with
 * every canonical profile as {@code <route id>/<profile>}, the benchmark adapter's case names, tagged
 * with the route's tiers ({@code smoke}, {@code standard}, {@code full}, or only {@code sailing}).
 */
public final class CanonicalCorpus {
    private CanonicalCorpus() { }

    public static Path dir() {
        return Path.of(System.getProperty("corpus.dir", "corpus"));
    }

    /** The corpus routes: from {@link #dir()} when it has them, else the packaged copy. */
    public static List<Route> routes() throws IOException {
        Path routes = dir().resolve("corpus/routes-v1.json");
        return Files.isRegularFile(routes) ? Route.load(routes) : Route.loadResource("/scenarios/corpus/routes-v1.json");
    }

    public static List<Route> routes(Path corpusDir) throws IOException {
        return Route.load(corpusDir.resolve("corpus/routes-v1.json"));
    }
}
