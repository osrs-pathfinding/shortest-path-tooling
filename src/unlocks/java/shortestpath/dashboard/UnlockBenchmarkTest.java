package shortestpath.dashboard;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Assume;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import shortestpath.pathfinder.AStarPathfinder;
import shortestpath.pathfinder.PathTerminationReason;
import shortestpath.pathfinder.UnlockSuggestions;
import shortestpath.profiles.Setup;
import shortestpath.requirement.Capability;
import shortestpath.requirement.model.SkillLevels;
import shortestpath.scenarios.Observation;
import shortestpath.scenarios.Scenario;
import shortestpath.scenarios.ScenarioRunner;
import shortestpath.scenarios.Suites;
import shortestpath.transport.Transport;

/**
 * Measures the A* backend's unlock-suggestion pass: what the plugin suggests unlocking when a target is
 * unreachable (shortest-path PR #836), over every scenario of the chosen suites.
 *
 * <p>Each scenario runs once with the A* search. For each one that ends unreachable, the pass then runs as the
 * plugin runs it (right after the search, on the same thread, within its 600 ms budget), and each suggestion is
 * checked again independently: the scenario's account is given the suggested quests, levels or items through the
 * account model, compiled afresh, and searched. Then the unreachable scenarios run {@code rounds - 1} more times
 * to time the pass warm. The first pass of each scenario is the cold one; the JVM warms up over the run, so the
 * earliest scenarios are the coldest.</p>
 *
 * <p>The report lists headline counts and timings, then what needs a look: passes that ran out of time,
 * unreachable scenarios with no suggestion (and why), suggestions that look out of order, and suggestions the
 * independent check couldn't confirm. The JSON has every unreachable scenario with its suggestions.</p>
 *
 * <h3>Gradle invocation</h3>
 * <pre>
 * ./gradlew unlockBenchmark -PshortestPathDir=../shortest-path
 * </pre>
 *
 * <h3>System properties</h3>
 * <table>
 *   <tr><th>Property</th><th>Default</th></tr>
 *   <tr><td>{@code unlockBenchmark.suites}</td><td>every suite but {@code sailing-routes}</td></tr>
 *   <tr><td>{@code unlockBenchmark.filter}</td><td>none (only scenarios whose name or category contains it)</td></tr>
 *   <tr><td>{@code unlockBenchmark.rounds}</td><td>{@code 3}</td></tr>
 *   <tr><td>{@code unlockBenchmark.outputDir}</td><td>{@code build/reports/unlock-benchmark}</td></tr>
 * </table>
 */
public class UnlockBenchmarkTest {

    static {
        ((Logger) LoggerFactory.getLogger("shortestpath.transport.Transport")).setLevel(Level.OFF);
    }

    /** A suggestion that needs at least this many levels, quest points or quests counts as a major unlock. */
    private static final int MAJOR_LEVELS = 30;
    private static final int MAJOR_QUESTS = 3;
    /** A skill suggestion needing at most this many levels counts as a small one. */
    private static final int SMALL_LEVELS = 5;

    /** One suggestion, as the overlay words it, and whether the account with it really reaches the target. */
    static final class Suggested {
        String label;
        String effort;
        String transport;
        boolean confirmed;
        transient Capability capability;
    }

    /** One scenario the search found unreachable. */
    static final class Unreachable {
        String suite;
        String scenario;
        String description;
        String profile;
        String category;
        boolean expectedReachable;
        double searchMs;
        double coldMs;
        List<Double> warmMs = new ArrayList<>();
        int candidates;
        int reruns;
        boolean outOfTime;
        boolean alreadyConnected;
        List<Suggested> suggestions = new ArrayList<>();
        List<String> suspicious = new ArrayList<>();

        String route() {
            return description == null || description.isEmpty() ? scenario : scenario + " (" + description + ")";
        }

        double warmMedian() {
            return warmMs.isEmpty() ? coldMs : median(warmMs);
        }

        boolean unconfirmed() {
            return suggestions.stream().anyMatch(suggestion -> !suggestion.confirmed);
        }

        /** Why there is no suggestion, or {@code null} when there is one. */
        String noneReason() {
            if (!suggestions.isEmpty()) {
                return null;
            }
            if (alreadyConnected) {
                return "already connected on the component graph";
            }
            if (candidates == 0) {
                return "no locked transport a single unlock opens";
            }
            if (outOfTime) {
                return "out of time";
            }
            return "no candidate reached the target";
        }
    }

    /** The whole run, as written to the JSON report. */
    static final class Report {
        String shortestPathCommit;
        List<String> suites;
        int rounds;
        int scenarios;
        int reached;
        int cutOff;
        int failed;
        List<Unreachable> unreachable = new ArrayList<>();
    }

    @Test
    public void run() throws IOException {
        Assume.assumeTrue("Run with ./gradlew unlockBenchmark", Boolean.getBoolean("unlockBenchmark.run"));

        List<String> suites = suites(System.getProperty("unlockBenchmark.suites"));
        String filter = System.getProperty("unlockBenchmark.filter");
        int rounds = Math.max(1, Integer.getInteger("unlockBenchmark.rounds", 3));
        Path outputDir = Paths.get(System.getProperty("unlockBenchmark.outputDir", "build/reports/unlock-benchmark"));

        Map<Scenario, String> scenarios = new LinkedHashMap<>();
        for (String suite : suites) {
            List<Scenario> loaded = Suites.load(suite);
            for (Scenario scenario : filter == null || filter.isEmpty() ? loaded : Suites.filter(loaded, filter)) {
                scenarios.put(scenario, suite);
            }
        }
        System.out.printf("Unlock benchmark: %d scenarios from %s, %d round(s)%n%n", scenarios.size(), suites, rounds);

        ScenarioRunner runner = new ScenarioRunner(ScenarioRunner.Backend.ASTAR);
        runner.prepare();
        Report report = new Report();
        report.shortestPathCommit = System.getProperty("unlockBenchmark.shortestPathCommit", "unknown");
        report.suites = suites;
        report.rounds = rounds;
        Map<Unreachable, Scenario> unreachableScenarios = new LinkedHashMap<>();
        int done = 0;
        for (Map.Entry<Scenario, String> entry : scenarios.entrySet()) {
            Scenario scenario = entry.getKey();
            report.scenarios++;
            if (++done % 250 == 0) {
                System.out.printf("[%d/%d] %d unreachable so far%n", done, scenarios.size(), report.unreachable.size());
            }
            Observation observation;
            try {
                observation = runner.run(scenario);
            } catch (RuntimeException error) {
                report.failed++;
                System.out.println("FAILED " + scenario.getName() + ": " + error);
                continue;
            }
            if (observation.isReached()) {
                report.reached++;
                continue;
            }
            if (observation.getResult().getTerminationReason() != PathTerminationReason.SEARCH_EXHAUSTED) {
                report.cutOff++;
                continue;
            }
            Unreachable row = new Unreachable();
            row.suite = entry.getValue();
            row.scenario = scenario.getName();
            row.description = scenario.getDescription();
            row.profile = scenario.getProfile().name();
            row.category = scenario.getCategory();
            row.expectedReachable = scenario.isExpectedReachable();
            row.searchMs = observation.getTotalNanos() / 1e6;
            AStarPathfinder search = observation.getAStar();
            search.findUnlockSuggestions();
            row.coldMs = search.getUnlockSearchNanos() / 1e6;
            UnlockSuggestions.Found found = search.getUnlockSearch();
            row.candidates = found.getCandidates();
            row.reruns = found.getReruns();
            row.outOfTime = found.isOutOfTime();
            row.alreadyConnected = found.isAlreadyConnected();
            for (UnlockSuggestions.Suggestion suggestion : found.getSuggestions()) {
                Suggested suggested = new Suggested();
                suggested.capability = suggestion.getCapability();
                suggested.label = label(suggestion.getCapability());
                suggested.effort = suggestion.getCapability().getEffort().name();
                suggested.transport = transportLabel(suggestion.getTransport());
                suggested.confirmed = confirm(runner, scenario, suggestion.getCapability());
                row.suggestions.add(suggested);
            }
            row.suspicious = suspicious(row.suggestions);
            report.unreachable.add(row);
            unreachableScenarios.put(row, scenario);
        }

        for (int round = 1; round < rounds; round++) {
            System.out.printf("Warm round %d of %d over %d unreachable scenarios%n", round, rounds - 1,
                unreachableScenarios.size());
            for (Map.Entry<Unreachable, Scenario> entry : unreachableScenarios.entrySet()) {
                AStarPathfinder search = runner.run(entry.getValue()).getAStar();
                search.findUnlockSuggestions();
                entry.getKey().warmMs.add(search.getUnlockSearchNanos() / 1e6);
            }
        }

        Files.createDirectories(outputDir);
        Files.write(outputDir.resolve("unlock-benchmark.md"), markdown(report).getBytes(StandardCharsets.UTF_8));
        Files.write(outputDir.resolve("unlock-benchmark.json"),
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report)
                .getBytes(StandardCharsets.UTF_8));
        System.out.println("Written to " + outputDir.toAbsolutePath().resolve("unlock-benchmark.md"));
    }

    private static List<String> suites(String property) {
        if (property != null && !property.isBlank()) {
            return Arrays.stream(property.split(",")).map(String::trim).collect(Collectors.toList());
        }
        // The sailing routes run on a boat, where the plugin uses the sailing search, not A*.
        return Suites.names().stream().filter(name -> !name.equals("sailing-routes")).collect(Collectors.toList());
    }

    /**
     * Whether the scenario's account, given the capability through the account model rather than by the plugin,
     * reaches the target.
     */
    private static boolean confirm(ScenarioRunner runner, Scenario scenario, Capability capability) {
        Setup setup = scenario.setup();
        capability.getQuests().forEach(quest -> setup.account.quest(quest, QuestState.FINISHED));
        SkillLevels levels = capability.getSkillLevels();
        for (Skill skill : Skill.values()) {
            if (levels.get(skill.ordinal()) > 0) {
                setup.account.level(skill, levels.get(skill.ordinal()));
            }
        }
        if (levels.get(SkillLevels.TOTAL_LEVEL_INDEX) > 0) {
            setup.account.totalLevel(levels.get(SkillLevels.TOTAL_LEVEL_INDEX));
        }
        if (levels.get(SkillLevels.QUEST_POINTS_INDEX) > 0) {
            setup.account.questPoints(levels.get(SkillLevels.QUEST_POINTS_INDEX));
        }
        capability.getItems().forEach((id, quantity) -> setup.account.inventory(id, quantity));
        return runner.run(scenario, setup.compile(), ScenarioRunner.Profiling.OFF).isReached();
    }

    /**
     * What looks out of order: a major unlock suggested first, or a quest ranked above a few levels of a skill.
     * The plugin ranks every quest above every skill, which this is here to question.
     */
    static List<String> suspicious(List<Suggested> suggestions) {
        List<String> reasons = new ArrayList<>();
        if (!suggestions.isEmpty() && isMajor(suggestions.get(0).capability)) {
            reasons.add("major unlock first: " + suggestions.get(0).label);
        }
        for (int i = 0; i < suggestions.size(); i++) {
            for (int j = i + 1; j < suggestions.size(); j++) {
                Capability above = suggestions.get(i).capability;
                Capability below = suggestions.get(j).capability;
                if (!above.getQuests().isEmpty() && levelsMissing(below) > 0 && levelsMissing(below) <= SMALL_LEVELS) {
                    reasons.add(suggestions.get(i).label + " above " + suggestions.get(j).label);
                }
            }
        }
        return reasons;
    }

    private static boolean isMajor(Capability capability) {
        SkillLevels levels = capability.getSkillLevels();
        return capability.getQuests().size() >= MAJOR_QUESTS
            || levels.get(SkillLevels.TOTAL_LEVEL_INDEX) > 0 || levels.get(SkillLevels.QUEST_POINTS_INDEX) > 0
            || levelsMissing(capability) >= MAJOR_LEVELS;
    }

    /** The levels a skill capability needs: the plugin's own within-tier measure. */
    private static int levelsMissing(Capability capability) {
        return capability.getEffort() == Capability.Effort.SKILLS ? capability.getAmount() : 0;
    }

    // --- Wording, close to the plugin's overlay line ---

    private static Map<Integer, String> itemNames;

    static String itemName(int id) {
        if (itemNames == null) {
            Map<Integer, String> names = new HashMap<>();
            for (Field field : ItemID.class.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                    try {
                        String name = field.getName().replace('_', ' ').toLowerCase(Locale.ROOT);
                        names.putIfAbsent(field.getInt(null), Character.toUpperCase(name.charAt(0)) + name.substring(1));
                    } catch (IllegalAccessException ignored) {
                        // Public constants only.
                    }
                }
            }
            itemNames = names;
        }
        return itemNames.getOrDefault(id, "Item " + id);
    }

    /** "Complete Twilight's Promise", "Reach 60 Agility", "Carry 20 Coins". */
    static String label(Capability capability) {
        List<String> parts = new ArrayList<>();
        capability.getQuests().forEach(quest -> parts.add(quest.getName()));
        SkillLevels levels = capability.getSkillLevels();
        for (int i = 0; i < SkillLevels.LENGTH; i++) {
            int level = levels.get(i);
            if (level > 0) {
                parts.add(i == SkillLevels.TOTAL_LEVEL_INDEX ? "total level " + level
                    : i == SkillLevels.COMBAT_LEVEL_INDEX ? "combat level " + level
                    : i == SkillLevels.QUEST_POINTS_INDEX ? level + " quest points"
                    : level + " " + Skill.values()[i].getName());
            }
        }
        capability.getItems().forEach((id, quantity) -> parts.add(quantity > 1 ? quantity + " " + itemName(id) : itemName(id)));
        Collections.sort(parts);
        String verb = !capability.getQuests().isEmpty() ? "Complete " : capability.getItems().isEmpty() ? "Reach " : "Carry ";
        return verb + String.join(", ", parts);
    }

    private static String transportLabel(Transport transport) {
        String type = transport.getType().name().replace('_', ' ').toLowerCase(Locale.ROOT);
        type = Character.toUpperCase(type.charAt(0)) + type.substring(1);
        String where = String.format(" (%s)", transport);
        return (transport.getDisplayInfo() != null ? type + " " + transport.getDisplayInfo()
            : transport.getObjectInfo() != null ? transport.getObjectInfo().replaceAll(" \\d+$", "") : type) + where;
    }

    // --- The Markdown report ---

    static double median(List<Double> values) {
        return percentile(values, 50);
    }

    /** Nearest-rank percentile; 0 for no values. */
    static double percentile(List<Double> values, double percent) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(percent / 100 * sorted.size());
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, rank - 1)));
    }

    private static String timingRow(String name, List<Double> values) {
        return String.format(Locale.ROOT, "| %s | %d | %.0f | %.0f | %.0f |%n", name, values.size(),
            median(values), percentile(values, 95), values.stream().mapToDouble(Double::doubleValue).max().orElse(0));
    }

    private static String cell(String value) {
        return value.replace("|", "\\|");
    }

    private static String suggestionList(Unreachable row) {
        return row.suggestions.isEmpty() ? "—" : row.suggestions.stream()
            .map(suggestion -> cell(suggestion.label) + (suggestion.confirmed ? "" : " **(not confirmed)**"))
            .collect(Collectors.joining("; "));
    }

    static String markdown(Report report) {
        List<Unreachable> rows = report.unreachable;
        StringBuilder md = new StringBuilder();
        md.append("# Unlock suggestions benchmark\n\n");
        md.append(String.format("Plugin `%s`, suites %s, %d round(s). A* search; the suggestion pass runs on each "
            + "scenario the search finds unreachable, as the plugin runs it.%n%n", report.shortestPathCommit,
            report.suites, report.rounds));

        long withSuggestion = rows.stream().filter(row -> !row.suggestions.isEmpty()).count();
        long outOfTime = rows.stream().filter(row -> row.outOfTime).count();
        md.append("## Summary\n\n| | Scenarios |\n|---|---:|\n");
        md.append(String.format("| Run | %d |%n| Reached | %d |%n| Cut off by the calculation limit (no pass) | %d |%n",
            report.scenarios, report.reached, report.cutOff));
        if (report.failed > 0) {
            md.append(String.format("| Failed to run | %d |%n", report.failed));
        }
        md.append(String.format("| **Unreachable** | **%d** |%n| … with at least one suggestion | %d |%n", rows.size(),
            withSuggestion));
        Map<String, Long> reasons = rows.stream().filter(row -> row.noneReason() != null)
            .collect(Collectors.groupingBy(Unreachable::noneReason, TreeMap::new, Collectors.counting()));
        reasons.forEach((reason, count) -> md.append(String.format("| … none: %s | %d |%n", reason, count)));
        md.append(String.format("| … out of time (any result) | %d |%n", outOfTime));
        md.append(String.format("| … with a suggestion that looks out of order | %d |%n",
            rows.stream().filter(row -> !row.suspicious.isEmpty()).count()));
        md.append(String.format("| … with a suggestion the account check didn't confirm | %d |%n%n",
            rows.stream().filter(Unreachable::unconfirmed).count()));

        md.append("### By profile\n\n| Profile | Unreachable | With a suggestion | None | Out of time |\n"
            + "|---|---:|---:|---:|---:|\n");
        Map<String, List<Unreachable>> byProfile = rows.stream()
            .collect(Collectors.groupingBy(row -> row.profile, TreeMap::new, Collectors.toList()));
        byProfile.forEach((profile, group) -> md.append(String.format("| %s | %d | %d | %d | %d |%n", profile,
            group.size(), group.stream().filter(row -> !row.suggestions.isEmpty()).count(),
            group.stream().filter(row -> row.suggestions.isEmpty()).count(),
            group.stream().filter(row -> row.outOfTime).count())));

        md.append("\n## Time (ms)\n\n| | Scenarios | Median | p95 | Max |\n|---|---:|---:|---:|---:|\n");
        md.append(timingRow("Pass, first run (cold)", rows.stream().map(row -> row.coldMs).collect(Collectors.toList())));
        if (report.rounds > 1) {
            md.append(timingRow("Pass, warm (median of later runs)",
                rows.stream().map(Unreachable::warmMedian).collect(Collectors.toList())));
        }
        md.append(timingRow("Pass, with a suggestion (cold)", rows.stream().filter(row -> !row.suggestions.isEmpty())
            .map(row -> row.coldMs).collect(Collectors.toList())));
        md.append(timingRow("Search that found it unreachable", rows.stream().map(row -> row.searchMs)
            .collect(Collectors.toList())));
        md.append("\nThe pass has a 600 ms budget and starts no new rerun in its last 100 ms.\n");

        section(md, "Out of time", rows, row -> row.outOfTime, Comparator.comparingDouble((Unreachable row) -> -row.coldMs),
            "| Scenario | ms | Candidates | Reruns | Found |\n|---|---:|---:|---:|---|\n",
            row -> String.format(Locale.ROOT, "| %s | %.0f | %d | %d | %s |%n", cell(row.route()), row.coldMs,
                row.candidates, row.reruns, suggestionList(row)));
        section(md, "No suggestion", rows, row -> row.suggestions.isEmpty(),
            Comparator.comparing(Unreachable::noneReason).thenComparing(row -> row.scenario),
            "| Scenario | Why | Expected unreachable | Candidates | Reruns | ms |\n|---|---|---|---:|---:|---:|\n",
            row -> String.format(Locale.ROOT, "| %s | %s | %s | %d | %d | %.0f |%n", cell(row.route()), row.noneReason(),
                row.expectedReachable ? "no" : "yes", row.candidates, row.reruns, row.coldMs));
        section(md, "Looks out of order", rows, row -> !row.suspicious.isEmpty(), Comparator.comparing(row -> row.scenario),
            "| Scenario | Why | Suggestions |\n|---|---|---|\n",
            row -> String.format("| %s | %s | %s |%n", cell(row.route()), cell(String.join("; ", row.suspicious)),
                suggestionList(row)));
        section(md, "Not confirmed by the account check", rows, Unreachable::unconfirmed,
            Comparator.comparing(row -> row.scenario), "| Scenario | Suggestions |\n|---|---|\n",
            row -> String.format("| %s | %s |%n", cell(row.route()), suggestionList(row)));

        md.append("\n## Most common suggestions\n\n| Suggestion | Scenarios |\n|---|---:|\n");
        rows.stream().flatMap(row -> row.suggestions.stream().map(suggestion -> suggestion.label).distinct())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(25)
            .forEach(entry -> md.append(String.format("| %s | %d |%n", cell(entry.getKey()), entry.getValue())));

        md.append("\n## Slowest passes\n\n| Scenario | Cold ms | Warm ms | Candidates | Reruns | Found |\n"
            + "|---|---:|---:|---:|---:|---|\n");
        rows.stream().sorted(Comparator.comparingDouble((Unreachable row) -> -row.warmMedian())).limit(20)
            .forEach(row -> md.append(String.format(Locale.ROOT, "| %s | %.0f | %.0f | %d | %d | %s |%n",
                cell(row.route()), row.coldMs, row.warmMedian(), row.candidates, row.reruns, suggestionList(row))));

        md.append("\n<details><summary>Every unreachable scenario</summary>\n\n"
            + "| Scenario | Profile | Suggestions (with the transport each opens) |\n|---|---|---|\n");
        for (Unreachable row : rows) {
            String suggestions = row.suggestions.isEmpty() ? "— (" + row.noneReason() + ")" : row.suggestions.stream()
                .map(suggestion -> cell(suggestion.label) + " → " + cell(suggestion.transport)
                    + (suggestion.confirmed ? "" : " **(not confirmed)**"))
                .collect(Collectors.joining("<br>"));
            md.append(String.format("| %s | %s | %s |%n", cell(row.route()), row.profile, suggestions));
        }
        md.append("\n</details>\n");
        return md.toString();
    }

    private static void section(StringBuilder md, String title, List<Unreachable> rows, Predicate<Unreachable> include,
            Comparator<Unreachable> order, String header, Function<Unreachable, String> line) {
        List<Unreachable> selected = rows.stream().filter(include).sorted(order).collect(Collectors.toList());
        md.append(String.format("%n## %s (%d)%n%n", title, selected.size()));
        if (selected.isEmpty()) {
            md.append("None.\n");
            return;
        }
        md.append(header);
        selected.forEach(row -> md.append(line.apply(row)));
    }
}
