package shortestpath.scenarios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static shortestpath.profiles.Profiles.UNIT_TEST;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import org.junit.Test;
import shortestpath.leagues.LeagueRegion;
import shortestpath.profiles.CompiledAccount;
import shortestpath.profiles.Profiles;
import shortestpath.transport.Transport;
import shortestpath.transport.parser.VarCheckType;
import shortestpath.transport.parser.VarRequirement;

public class ScenariosTest {
    private static final Path RESOURCES = Path.of("src/test/resources");

    @Test
    public void everySuiteHasExactlyOneExpectedLengthsFile() throws IOException {
        Set<String> files;
        try (Stream<Path> listing = Files.list(RESOURCES.resolve("scenarios/expected-lengths"))) {
            files = listing.map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                .collect(Collectors.toSet());
        }
        assertEquals(Suites.names(), files);
    }

    @Test
    public void expectedLengthsNameExistingScenarios() throws IOException {
        for (String suite : Suites.names()) {
            Set<String> names = Suites.load(suite).stream().map(Scenario::getName).collect(Collectors.toSet());
            for (String name : ExpectedLengths.load(suite).keySet()) {
                assertTrue(suite + " has an expected length for missing scenario '" + name + "'",
                    names.contains(name));
            }
        }
    }

    @Test
    public void javaSuitesCompile() throws IOException {
        for (String suite : Suites.names()) {
            if (suite.equals("clue_locations_full")) {
                continue;
            }
            for (Scenario scenario : Suites.load(suite)) {
                scenario.compile();
            }
        }
    }

    @Test
    public void namesAreUniqueAndCommaFree() throws IOException {
        for (String suite : Suites.names()) {
            Set<String> names = new HashSet<>();
            for (Scenario scenario : Suites.load(suite)) {
                assertTrue(suite + ": duplicate " + scenario.getName(), names.add(scenario.getName()));
                // Dashboard reports and the issue store refer to scenarios by name.
                assertFalse(suite + ": comma in " + scenario.getName(), scenario.getName().contains(","));
            }
        }
    }

    @Test
    public void leagueAreasUnlockTheirRegionInThePlugin() {
        for (LeagueRegion region : Overrides.LEAGUE_AREA_IDS.keySet()) {
            CompiledAccount locked = Scenario.scenario("locked", "test").to(3222, 3218, 0)
                .profile(Profiles.SEASONAL).build().compile();
            CompiledAccount picked = Scenario.scenario("picked", "test").to(3222, 3218, 0)
                .profile(Profiles.SEASONAL).account(Overrides.leagueAreas(region)).build().compile();
            if (region.isAlwaysUnlocked() || region.isAlwaysBlocked()) {
                continue;
            }
            assertFalse(region + " is locked before picking it",
                locked.getConfig().getLeagueModeState().isUnlocked(region));
            assertTrue(region + " is unlocked after picking it",
                picked.getConfig().getLeagueModeState().isUnlocked(region));
        }
    }

    @Test
    public void questOverridesReachThePathfinderConfig() {
        CompiledAccount compiled = Scenario.scenario("Grand Tree not started", "quest-gating")
            .from(3284, 3213, 0).to(2971, 2968, 0)
            .profile(UNIT_TEST)
            .account(a -> a.quest(Quest.THE_GRAND_TREE, QuestState.NOT_STARTED))
            .build().compile();
        assertEquals(QuestState.NOT_STARTED, compiled.getConfig().getQuestState(Quest.THE_GRAND_TREE));
        assertEquals(QuestState.FINISHED, compiled.getConfig().getQuestState(Quest.BONE_VOYAGE));
    }

    /**
     * {@code varPlayerChecks} returns {@code true} when a requirement fails. Presets bypass
     * varplayer requirements unless a scenario turns {@code bypassVarPlayerChecks} off; then the
     * scenario's varplayers (0 when unset) decide.
     */
    @Test
    public void bypassVarPlayerChecksGatesVarPlayerRequirements() {
        // varp 139 (LEGENDSQUEST progress) is in the committed transport data, so refresh()
        // snapshots it from the client.
        Transport transport = new Transport.TransportBuilder()
            .varRequirements(Set.of(VarRequirement.varPlayer(139, 49, VarCheckType.GREATER)))
            .build();

        CompiledAccount bypassed = unitTest().account(a -> a.varplayer(139, 0)).build().compile();
        assertFalse(bypassed.getConfig().varPlayerChecks(transport, 0));

        CompiledAccount satisfied = unitTest().account(a -> a.varplayer(139, 50))
            .settings(s -> s.setBypassVarPlayerChecks(false)).build().compile();
        assertFalse(satisfied.getConfig().varPlayerChecks(transport, 0));

        CompiledAccount unset = unitTest().settings(s -> s.setBypassVarPlayerChecks(false)).build().compile();
        assertTrue(unset.getConfig().varPlayerChecks(transport, 0));
    }

    @Test
    public void dataRejectsOverrideColumns() {
        try {
            ScenarioData.parse("name,category,start_x,start_y,start_plane,x,y,plane,profile,varbits\n"
                + "a,b,1,2,0,3,4,0,ALL,1=2\n", "test.csv");
            fail("override columns belong in Java suites");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("varbits"));
        }
    }

    @Test
    public void dataLoadsProfilesAndExpectations() {
        List<Scenario.Builder> rows = ScenarioData.parse(
            "name,category,start_x,start_y,start_plane,x,y,plane,profile,minimum_length,expect_reachable\n"
                + "# comment\n"
                + "a,walk,3222,3218,0,3105,3251,0,unit_test,10,false\n"
                + "b,clue,,,,3105,3251,0,ALL,,\n", "test.csv");
        Scenario a = rows.get(0).build();
        assertEquals(UNIT_TEST, a.getProfile());
        assertEquals(10, a.getMinimumLength().getAsInt());
        assertFalse(a.isExpectedReachable());
        assertEquals(shortestpath.WorldPointUtil.UNDEFINED, rows.get(1).build().getStartPoint());
    }

    @Test
    public void expectedLengthsUpdateKeepsUncapturedLengthsInScenarioOrder() throws IOException {
        Path file = Files.createTempFile("lengths", ".json");
        try {
            Files.writeString(file, "{\"b\": 2, \"a\": 1}\n");
            List<Scenario> scenarios = List.of(unitTestNamed("a"), unitTestNamed("b"), unitTestNamed("c"));
            ExpectedLengths.update(file, scenarios, Map.of("b", 5, "c", 7));
            assertEquals(Map.of("a", 1, "b", 5, "c", 7), ExpectedLengths.parse(Files.readString(file)));
            assertEquals(List.of("a", "b", "c"), List.copyOf(ExpectedLengths.parse(Files.readString(file)).keySet()));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static Scenario.Builder unitTest() {
        return Scenario.scenario("varp", "test").to(2971, 2968, 0).profile(UNIT_TEST);
    }

    private static Scenario unitTestNamed(String name) {
        return Scenario.scenario(name, "test").to(2971, 2968, 0).profile(UNIT_TEST).build();
    }
}
