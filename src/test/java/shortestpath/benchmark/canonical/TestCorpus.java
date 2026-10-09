package shortestpath.benchmark.canonical;

import java.nio.file.Path;

/** The canonical corpus used by tests: -PcorpusDir, default the repository's corpus/. */
final class TestCorpus {
    private TestCorpus() { }

    static Path dir() {
        return Path.of(System.getProperty("benchmark.corpusDir", "corpus"));
    }
}
