package shortestpath;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * Non-growth dependency-rule lint: the plugin's leaf packages
 * ({@code transport}, {@code pathfinder}, {@code requirement},
 * {@code leagues}, {@code overlay}, {@code settings} — subtrees included)
 * must not reference
 * {@code ShortestPathPlugin}. Cross-cutting configuration and world-geometry
 * access goes through owned services (the effective-config settings service,
 * the POH service) or leaf utilities; any new coupling arrives through an
 * injected seam instead.
 *
 * <p>Every pre-existing violation is frozen in {@link #ALLOWLIST}, keyed by
 * {@code path:line} relative to {@code shortest-path/src/main/java/}. The
 * set-equality assertion makes the lint self-maintaining: extraction work
 * <em>removes</em> entries as each site migrates — entries are removed,
 * never added.
 *
 * <p>The same freeze applies to leaf-to-leaf coupling: {@link #LEAF_EDGES}
 * records every package pair linked by a leaf-package import, so a new edge
 * — mutual or one-way — fails the build until it is justified in the table. The resource-anchor sites ({@code TransportLoader},
 * {@code SplitFlagMap}, {@code LeagueRegionChecker}) disappear when the data
 * loader lands self-anchored. An unlisted reference site fails the build
 * (new coupling); an allowlist key with no matching site fails it too
 * (stale entry — remove it in the same change that removed the reference).
 *
 * <p>Run with {@code ./gradlew test --tests '*DependencyRule*'}. Enforcement
 * is that command — the milestone's phase gate plus a checklist item on
 * every extraction pull request. The wrapper {@code test} task runs in
 * neither CI nor the maintenance-verify chain; that gap is deliberate, and
 * wiring the suite into CI is a separate scope decision.
 *
 * <p>{@link #observedSites(Path)} is factored out so
 * {@link #flagsUnlistedCoupling()} can prove both failure branches against
 * temporary fixture roots — the submodule tree is never written to.
 *
 * <p>Known gaps, by design. The scan token is the dotted form
 * {@code ShortestPathPlugin.}, so instance coupling — a leaf class that
 * accepts an injected {@code ShortestPathPlugin} and calls it through a
 * field, as several overlay classes do today — is not counted. Those sites
 * are documented in the architectural survey and migrate with their owning
 * extractions; widening the token set is a future enhancement, not a
 * silent rule change. And when the last allowlist entry is retired,
 * {@link #scanHasReach()} intentionally fails: the empty-allowlist
 * assertion exists to prevent a vacuous guard, so the change that removes
 * the final coupling should delete this lint (or relax that assertion) in
 * the same commit.
 *
 * <p>Two maintenance properties to know before touching allowlisted code.
 * The match is a raw substring scan — comments and string literals
 * containing {@code ShortestPathPlugin.} count as sites. And keys are
 * {@code path:line}, so edits above an allowlisted reference shift its
 * line number and must update the affected keys in the same change; that
 * line-granularity is what lets the lint detect stale entries.
 */
public class PluginDependencyRuleTest
{
	private static final Path MAIN_ROOT = Paths.get("shortest-path/src/main/java");

	// New leaf packages must be added here — the lint only covers what it enumerates.
	private static final List<String> LEAF_PACKAGES = List.of(
		"transport", "pathfinder", "requirement", "leagues", "overlay",
		"settings", "items", "spirittree");

	private static final String PLUGIN_REFERENCE = "ShortestPathPlugin.";

	/**
	 * Frozen census of existing {@code ShortestPathPlugin} reference sites in
	 * the leaf packages. Key = {@code path:line} relative to
	 * {@code shortest-path/src/main/java/}; value = justification + owning
	 * migration. Extraction work removes entries as sites migrate — entries
	 * are removed, never added.
	 */
	private static final Map<String, String> ALLOWLIST = new TreeMap<>();

	static
	{
		// Resource anchors — plugin-class resource reads that die when the
		// data loader anchors on its own class.
		ALLOWLIST.put("shortestpath/transport/TransportLoader.java:33",
			"resource anchor — transport TSV read; the loader migrates self-anchored");
		ALLOWLIST.put("shortestpath/pathfinder/SplitFlagMap.java:92",
			"resource anchor — collision-map resource read; the loader migrates self-anchored");
		ALLOWLIST.put("shortestpath/leagues/LeagueRegionChecker.java:105",
			"resource anchor — league-region TSV read; the loader migrates self-anchored");

		// Static imports of the POH landing-tile constants.
		ALLOWLIST.put("shortestpath/pathfinder/PathfinderConfig.java:29",
			"import static of POH_LANDING_X — migrates to the POH service");
		ALLOWLIST.put("shortestpath/pathfinder/PathfinderConfig.java:30",
			"import static of POH_LANDING_Y — migrates to the POH service");
		ALLOWLIST.put("shortestpath/pathfinder/TransportAvailability.java:9",
			"import static of POH_LANDING_X — migrates to the POH service");
		ALLOWLIST.put("shortestpath/pathfinder/TransportAvailability.java:10",
			"import static of POH_LANDING_Y — migrates to the POH service");

		// isInsidePoh world-geometry reads — migrate to the POH service.
		ALLOWLIST.put("shortestpath/pathfinder/PathfinderConfig.java:679",
			"isInsidePoh redirect filter — migrates to the POH service");
		ALLOWLIST.put("shortestpath/pathfinder/TransportAvailability.java:100",
			"isInsidePoh origin check — migrates to the POH service");
		ALLOWLIST.put("shortestpath/requirement/Requirements.java:194",
			"isInsidePoh POH gate — migrates to the POH service");
		ALLOWLIST.put("shortestpath/requirement/Requirements.java:262",
			"isInsidePoh POH-variant gate — migrates to the POH service");
		ALLOWLIST.put("shortestpath/overlay/PathTileOverlay.java:254",
			"isInsidePoh marker filter — migrates to the POH service");
		ALLOWLIST.put("shortestpath/overlay/PathTileOverlay.java:289",
			"isInsidePoh tracer filter — migrates to the POH service");
		ALLOWLIST.put("shortestpath/overlay/PathTileOverlay.java:324",
			"isInsidePoh marker filter — migrates to the POH service");
		ALLOWLIST.put("shortestpath/overlay/PathTileOverlay.java:368",
			"isInsidePoh marker filter — migrates to the POH service");
		ALLOWLIST.put("shortestpath/overlay/PathTileOverlay.java:766",
			"isInsidePoh transport-tile check — migrates to the POH service");
		ALLOWLIST.put("shortestpath/overlay/PathTileOverlay.java:767",
			"isInsidePoh player-tile check — migrates to the POH service");
	}

	/**
	 * Matches an import of a leaf-package type:
	 * {@code import [static] shortestpath.<pkg>.<Type>}. The captured group
	 * is the first package segment, so subpackage imports
	 * ({@code shortestpath.requirement.model.X}) resolve to their leaf.
	 */
	private static final Pattern LEAF_IMPORT =
		Pattern.compile("^\\s*import\\s+(?:static\\s+)?shortestpath\\.(\\w+)\\.");

	/**
	 * Frozen census of leaf-to-leaf package edges. Key = {@code "from -> to"}
	 * — observed when a source file under leaf package {@code from} imports
	 * {@code shortestpath.<to>.}; value = justification. Like
	 * {@link #ALLOWLIST} the table is self-maintaining: new coupling fails
	 * the build until it is justified here, and an entry whose edge
	 * disappeared fails until it is removed in the same change.
	 *
	 * <p>The {@code items} pairs are the extraction's documented seams:
	 * {@code items -> requirement}/{@code requirement -> items} share the
	 * eligibility snapshot and player-state source, and
	 * {@code items -> pathfinder}/{@code pathfinder -> items} are the
	 * bank-pickup projection and its {@code getBankPickup} facade — the
	 * projection is a candidate for moving back onto
	 * {@code PathfinderConfig} if the mutual edge needs breaking. The
	 * {@code spirittree <-> requirement} pair is likewise a documented
	 * seam: the service reads {@code PlayerStateSource} while the
	 * requirement side consumes its statics
	 * ({@code patchNameForTile}/{@code modalWidgetOpen}) — candidates for a
	 * leaf-neutral type if the mutual edge needs breaking.
	 */
	private static final Map<String, String> LEAF_EDGES = new TreeMap<>();

	static
	{
		// items package — the extraction's consumer/producer seams.
		LEAF_EDGES.put("items -> pathfinder",
			"ItemStateService reads PathStep/PathfinderConfig for the bank-pickup projection");
		LEAF_EDGES.put("items -> requirement",
			"ItemStateService.collectEligibility shares PlayerStateSource/TransportEligibility/BankPickupResult/Unlock");
		LEAF_EDGES.put("items -> settings",
			"ItemChange/ItemStateService read the TeleportationItem setting and Effect facts");
		LEAF_EDGES.put("pathfinder -> items",
			"PathfinderConfig holds the ItemStateService reference and the getBankPickup facade");
		LEAF_EDGES.put("requirement -> items",
			"ClientPlayerStateSource/RequirementContext read OwnedItems and collectEligibility");

		// spirittree package — the extraction's seams.
		LEAF_EDGES.put("pathfinder -> spirittree",
			"PathfinderConfig holds the SpiritTreeService reference");
		LEAF_EDGES.put("requirement -> spirittree",
			"Requirements/ClientPlayerStateSource consume SpiritTreeService.patchNameForTile and modalWidgetOpen");
		LEAF_EDGES.put("spirittree -> requirement",
			"SpiritTreeService reads PlayerStateSource for the availability refresh");
		LEAF_EDGES.put("spirittree -> settings",
			"TreeChange/SpiritTreeService read Effect facts and the teleportation settings");

		// Pre-existing edges frozen at lint introduction.
		LEAF_EDGES.put("leagues -> requirement",
			"LeagueModeState builds requirement checks");
		LEAF_EDGES.put("overlay -> pathfinder",
			"overlays read PathStep/PathfinderConfig path state");
		LEAF_EDGES.put("overlay -> requirement",
			"highlight overlays read requirement evaluation results");
		LEAF_EDGES.put("overlay -> settings",
			"overlays read config-facing settings types");
		LEAF_EDGES.put("overlay -> transport",
			"overlays read Transport records and destination geometry");
		LEAF_EDGES.put("pathfinder -> leagues",
			"league-region restrictions gate the search");
		LEAF_EDGES.put("pathfinder -> requirement",
			"PathfinderConfig consumes requirement evaluation");
		LEAF_EDGES.put("pathfinder -> settings",
			"PathfinderConfig reads effective-config settings");
		LEAF_EDGES.put("pathfinder -> transport",
			"the search consumes Transport/TransportAvailability data");
		LEAF_EDGES.put("requirement -> leagues",
			"requirements consult league-region checks");
		LEAF_EDGES.put("requirement -> pathfinder",
			"BankPickupRequirements walks PathStep/PathfinderConfig/TransportAvailability");
		LEAF_EDGES.put("requirement -> settings",
			"RoutingPolicy/TransportEligibility read settings types");
		LEAF_EDGES.put("requirement -> transport",
			"requirements evaluate Transport records");
		LEAF_EDGES.put("settings -> pathfinder",
			"Settings/EffectiveConfig expose pathfinder-facing config");
		LEAF_EDGES.put("settings -> requirement",
			"Settings/EffectiveConfig expose requirement-facing config");
		LEAF_EDGES.put("settings -> transport",
			"Settings/EffectiveConfig expose transport-facing config");
		LEAF_EDGES.put("transport -> leagues",
			"Transport records reference league regions");
		LEAF_EDGES.put("transport -> requirement",
			"transport parsers build requirement models");
		LEAF_EDGES.put("transport -> settings",
			"TransportTypeConfig reads settings types");
	}

	/**
	 * Scan the leaf packages under {@code mainRoot} (expected layout:
	 * {@code <mainRoot>/shortestpath/<pkg>} plus nested subdirectories) and
	 * return the sorted
	 * set of {@code path:line} keys — one per source line containing a
	 * {@code ShortestPathPlugin.} reference, paths relative to
	 * {@code mainRoot}. A line with several references yields one key.
	 */
	static SortedSet<String> observedSites(Path mainRoot) throws IOException
	{
		SortedSet<String> sites = new TreeSet<>();
		Path base = mainRoot.resolve("shortestpath");
		for (String pkg : LEAF_PACKAGES)
		{
			Path dir = base.resolve(pkg);
			if (!Files.isDirectory(dir))
			{
				continue;
			}
			List<Path> sources;
			try (Stream<Path> stream = Files.walk(dir))
			{
				sources = stream
					.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
					.collect(Collectors.toList());
			}
			for (Path source : sources)
			{
				String rel = mainRoot.relativize(source).toString()
					.replace(File.separatorChar, '/');
				List<String> lines = Files.readAllLines(source);
				for (int i = 0; i < lines.size(); i++)
				{
					if (lines.get(i).contains(PLUGIN_REFERENCE))
					{
						sites.add(rel + ":" + (i + 1));
					}
				}
			}
		}
		return sites;
	}

	/**
	 * Scan the leaf packages under {@code mainRoot} for leaf-to-leaf edges
	 * and return the sorted set of {@code "from -> to"} keys — one per leaf
	 * package pair where a source file in {@code from} has an
	 * {@code import shortestpath.<to>.} line. Same-package imports and
	 * imports of non-leaf packages are ignored; fully-qualified references
	 * without an import and javadoc links are not counted — the same
	 * known-gap policy as {@link #observedSites(Path)}.
	 */
	static SortedSet<String> observedEdges(Path mainRoot) throws IOException
	{
		SortedSet<String> edges = new TreeSet<>();
		Path base = mainRoot.resolve("shortestpath");
		for (String fromPkg : LEAF_PACKAGES)
		{
			Path dir = base.resolve(fromPkg);
			if (!Files.isDirectory(dir))
			{
				continue;
			}
			List<Path> sources;
			try (Stream<Path> stream = Files.walk(dir))
			{
				sources = stream
					.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
					.collect(Collectors.toList());
			}
			for (Path source : sources)
			{
				for (String line : Files.readAllLines(source))
				{
					Matcher m = LEAF_IMPORT.matcher(line);
					if (m.find())
					{
						String toPkg = m.group(1);
						if (!toPkg.equals(fromPkg) && LEAF_PACKAGES.contains(toPkg))
						{
							edges.add(fromPkg + " -> " + toPkg);
						}
					}
				}
			}
		}
		return edges;
	}

	@Test
	public void leafPackagesDoNotReferencePlugin() throws IOException
	{
		SortedSet<String> observed = observedSites(MAIN_ROOT);

		SortedSet<String> unlisted = new TreeSet<>(observed);
		unlisted.removeAll(ALLOWLIST.keySet());

		SortedSet<String> stale = new TreeSet<>(ALLOWLIST.keySet());
		stale.removeAll(observed);

		StringBuilder message = new StringBuilder();
		if (!unlisted.isEmpty())
		{
			message.append("new leaf-package coupling to ShortestPathPlugin is not ")
				.append("allowlisted — route cross-cutting config and world-geometry ")
				.append("access through an owned service or injected seam instead: ")
				.append(unlisted).append(' ');
		}
		if (!stale.isEmpty())
		{
			message.append("stale allowlist entries with no matching reference site ")
				.append("— remove this allowlist entry in the change that removed the ")
				.append("reference: ").append(stale);
		}
		assertTrue(message.toString(), unlisted.isEmpty() && stale.isEmpty());
	}

	@Test
	public void scanHasReach() throws IOException
	{
		assertFalse("the frozen allowlist must not be empty — a vacuous guard "
			+ "is worse than none", ALLOWLIST.isEmpty());
		Path base = MAIN_ROOT.resolve("shortestpath");
		for (String pkg : LEAF_PACKAGES)
		{
			Path dir = base.resolve(pkg);
			assertTrue("leaf package directory missing: " + dir, Files.isDirectory(dir));
			long sources;
			try (Stream<Path> stream = Files.walk(dir))
			{
				sources = stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).count();
			}
			assertTrue("leaf package scanned no .java files: " + dir, sources > 0);
		}
	}

	/**
	 * Red-direction proof: {@link #observedSites(Path)} must flag an
	 * unlisted {@code ShortestPathPlugin.} site, and an empty fixture root
	 * must mark every allowlist entry stale. Fixture roots live in the temp
	 * directory — the submodule is never written.
	 */
	@Test
	public void flagsUnlistedCoupling() throws IOException
	{
		Path fixture = Files.createTempDirectory("dep-rule-probe");
		Path dir = fixture.resolve("shortestpath").resolve("transport");
		Files.createDirectories(dir);
		Files.write(dir.resolve("LintProbeTmp.java"), Collections.singletonList(
			"class LintProbeTmp { String s = ShortestPathPlugin.CONFIG_GROUP; }"));

		SortedSet<String> observed = observedSites(fixture);
		assertTrue("probe reference not observed: " + observed,
			observed.contains("shortestpath/transport/LintProbeTmp.java:1"));

		SortedSet<String> unlisted = new TreeSet<>(observed);
		unlisted.removeAll(ALLOWLIST.keySet());
		assertEquals("the probe site must land in the unlisted-hit set",
			Collections.singleton("shortestpath/transport/LintProbeTmp.java:1"), unlisted);

		Path empty = Files.createTempDirectory("dep-rule-empty");
		SortedSet<String> nothing = observedSites(empty);
		assertTrue("an empty fixture root must observe no sites: " + nothing,
			nothing.isEmpty());
		SortedSet<String> stale = new TreeSet<>(ALLOWLIST.keySet());
		stale.removeAll(nothing);
		assertEquals("an empty fixture must mark every allowlist entry stale",
			new TreeSet<>(ALLOWLIST.keySet()), stale);
	}

	/**
	 * The leaf-to-leaf edge set is frozen: a new leaf-package dependency
	 * fails until the coupling is justified in {@link #LEAF_EDGES}, and a
	 * removed edge fails until its entry leaves the table in the same
	 * change. Mutual edges (cycles) are documented where they exist rather
	 * than forbidden outright — the check exists so coupling cannot grow
	 * silently.
	 */
	@Test
	public void leafPackageEdgesAreFrozen() throws IOException
	{
		SortedSet<String> observed = observedEdges(MAIN_ROOT);

		SortedSet<String> unlisted = new TreeSet<>(observed);
		unlisted.removeAll(LEAF_EDGES.keySet());

		SortedSet<String> stale = new TreeSet<>(LEAF_EDGES.keySet());
		stale.removeAll(observed);

		StringBuilder message = new StringBuilder();
		if (!unlisted.isEmpty())
		{
			message.append("new leaf-package edge is not documented — justify ")
				.append("the coupling in LEAF_EDGES or route through a neutral ")
				.append("seam instead: ").append(unlisted).append(' ');
		}
		if (!stale.isEmpty())
		{
			message.append("stale edge entries with no matching import — remove ")
				.append("the entry in the change that removed the edge: ")
				.append(stale);
		}
		assertTrue(message.toString(), unlisted.isEmpty() && stale.isEmpty());
	}

	/**
	 * Red-direction proof for {@link #observedEdges(Path)}: a fixture import
	 * of a leaf package must surface as an unlisted edge, and a same-package
	 * import must not produce an edge.
	 */
	@Test
	public void flagsUnlistedEdge() throws IOException
	{
		Path fixture = Files.createTempDirectory("dep-rule-edge");
		Path dir = fixture.resolve("shortestpath").resolve("leagues");
		Files.createDirectories(dir);
		Files.write(dir.resolve("EdgeProbeTmp.java"), Arrays.asList(
			"package shortestpath.leagues;",
			"import shortestpath.items.ItemStateService;",
			"import shortestpath.leagues.LeagueModeState;",
			"class EdgeProbeTmp { }"));

		SortedSet<String> edges = observedEdges(fixture);
		assertEquals("the probe edge must be the only observed edge: " + edges,
			Collections.singleton("leagues -> items"), edges);

		SortedSet<String> unlisted = new TreeSet<>(edges);
		unlisted.removeAll(LEAF_EDGES.keySet());
		assertEquals("the undocumented probe edge must land in the unlisted set",
			Collections.singleton("leagues -> items"), unlisted);
	}
}
