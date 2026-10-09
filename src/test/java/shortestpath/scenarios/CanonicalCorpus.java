package shortestpath.scenarios;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * The canonical corpus directory (default the repository's {@code corpus/}, or {@code -Dcorpus.dir})
 * and its routes, {@code corpus/routes-v1.json}. The {@code canonical} suite runs every route with
 * every canonical profile as {@code <route id>/<profile>}, the benchmark adapter's case names, tagged
 * with the route's tiers ({@code smoke}, {@code standard}, {@code full}).
 */
public final class CanonicalCorpus {
    private CanonicalCorpus() { }

    public static Path dir() {
        return Path.of(System.getProperty("corpus.dir", "corpus"));
    }

    public static List<Route> routes(Path corpusDir) throws IOException {
        return Route.load(corpusDir.resolve("corpus/routes-v1.json"));
    }
}
