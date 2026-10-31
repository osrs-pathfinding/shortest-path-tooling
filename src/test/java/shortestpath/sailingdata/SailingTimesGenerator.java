package shortestpath.sailingdata;

import shortestpath.pathfinder.sailing.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.sailing.LegTimes;
import shortestpath.sailing.SeaArea;

/**
 * Writes the sailing data, from the collision map and the moorings: the collision map's sailing layer
 * ({@link SailingLayerBuilder}: the sea, and each boat kind's bodies of water), then the sailing transports
 * ({@link SailingTransports}): for each boat kind at each base speed a hull gives ({@link LegTimes#SPEEDS}), the ticks
 * from casting off at each mooring to docking at every other mooring it can, from one search by cost over the whole
 * sea per berth and speed. Run it
 * with the {@code sailingData} task whenever the collision map, the moorings or the sailing moves change, before the
 * routing cuts (whose fingerprint covers the whole collision map); {@code SailingTransportsTest} says when the
 * committed data is stale.
 * <p>
 * Each berth is searched once per kind and speed, with every restricted {@link SeaArea} the kind can be fitted for
 * open, and each speed's cell names the areas its leg sails through: for a boat with those parts at that speed the
 * time is the leg's, and for any other boat a bound on it, as {@link SailingTransports} says.
 */
public final class SailingTimesGenerator
{
	// The transport files' columns: origin, destination, skills, quests, sea areas, duration, a time per speed, and
	// display info
	private static final int COLUMNS = 7 + LegTimes.SPEEDS.length;

	private SailingTimesGenerator()
	{
	}

	public static void main(String[] args) throws Exception
	{
		Path resources = Path.of(args.length > 0 ? args[0] : "src/main/resources");
		Path zipPath = resources.resolve("collision-map.zip");
		byte[] zip = Files.readAllBytes(zipPath);
		List<SailingMooring> moorings = SailingMoorings.all();
		long started = System.nanoTime();
		String fingerprint = SailingTransports.fingerprint(zip, moorings);
		int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
		SortedMap<String, byte[]> layer = SailingLayerBuilder.build(SplitFlagMap.fromZip(new ByteArrayInputStream(zip)),
			moorings, fingerprint, threads);
		byte[] written = SailingLayerBuilder.withLayer(zip, layer);
		Files.write(zipPath, written);
		System.err.printf("wrote %s (%d KB) at %.1f s%n", zipPath, written.length / 1024, (System.nanoTime() - started) / 1e9);

		SplitFlagMap flags = SplitFlagMap.fromZip(new ByteArrayInputStream(written));
		CollisionMap shared = new CollisionMap(flags);
		SailingWaters waters = SailingWaters.of(shared);
		// Moorings whose land tile is blocked can be neither boarded nor landed at
		List<SailingMooring> usable = new ArrayList<>();
		for (SailingMooring mooring : moorings)
		{
			int land = mooring.landTile();
			if (!shared.isBlocked(WorldPointUtil.unpackWorldX(land), WorldPointUtil.unpackWorldY(land),
				WorldPointUtil.unpackWorldPlane(land))) usable.add(mooring);
		}
		ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, threads / 2));
		try
		{
			for (SailingVessel.Kind kind : SailingVessel.Kind.values())
			{
				List<Future<String>> rows = new ArrayList<>();
				for (SailingMooring from : usable)
				{
					if (from.fits(SailingVessel.of(kind, LegTimes.SPEEDS[0])))
						rows.add(pool.submit(() -> legsFrom(new CollisionMap(flags), waters, kind, from, usable)));
				}
				StringBuilder file = new StringBuilder(header(fingerprint));
				for (Future<String> row : rows) file.append(row.get());
				Path out = resources.resolve(SailingTransports.resource(kind).substring(1));
				Files.writeString(out, file.toString(), StandardCharsets.UTF_8);
				System.err.printf("wrote %s at %.1f s%n", out, (System.nanoTime() - started) / 1e9);
			}
		}
		finally
		{
			pool.shutdownNow();
		}
	}

	// The rows for the moorings a boat of kind leaving from's berth can dock at, at some speed
	@SuppressWarnings("unchecked")
	private static String legsFrom(CollisionMap map, SailingWaters waters, SailingVessel.Kind kind,
		SailingMooring from, List<SailingMooring> moorings)
	{
		List<SailingMooring> exits = new ArrayList<>();
		for (SailingMooring to : moorings)
		{
			if (to != from && to.fits(SailingVessel.of(kind, LegTimes.SPEEDS[0]))) exits.add(to);
		}
		List<List<Object[]>> bySpeed = new ArrayList<>();
		for (int speed = 0; speed < LegTimes.SPEEDS.length; speed++)
		{
			SailingVessel vessel = SailingVessel.of(kind, LegTimes.SPEEDS[speed]).withAreas(SeaArea.ALL);
			bySpeed.add(search(map, waters, vessel, from, exits));
		}
		StringBuilder rows = new StringBuilder(comment("# " + from.name()));
		for (int i = 0; i < exits.size(); i++)
		{
			int fastest = LegTimes.NONE;
			Set<SeaArea> every = EnumSet.noneOf(SeaArea.class);
			StringBuilder cells = new StringBuilder();
			for (List<Object[]> legs : bySpeed)
			{
				Object[] leg = legs.get(i);
				int ticks = leg == null ? LegTimes.NONE
					: SailingMooring.BOARD_TICKS + (int) leg[1] + SailingMooring.DISEMBARK_TICKS;
				Set<SeaArea> through = leg == null ? SeaArea.NONE : (Set<SeaArea>) leg[0];
				if (ticks != LegTimes.NONE && (fastest == LegTimes.NONE || ticks < fastest)) fastest = ticks;
				every.addAll(through);
				cells.append(LegTimes.format(ticks, through)).append('\t');
			}
			if (fastest == LegTimes.NONE) continue;
			SailingMooring to = exits.get(i);
			rows.append(point(from.landTile())).append('\t').append(point(to.landTile())).append('\t')
				.append(skills(from, to)).append('\t').append(quests(from, to)).append('\t')
				.append(SeaArea.format(every)).append('\t').append(fastest).append('\t')
				.append(cells).append(to.name()).append('\n');
		}
		return rows.toString();
	}

	/**
	 * One search by cost from where {@code vessel} casts off at {@code from}. Per exit, in order: the restricted areas
	 * its leg sails through and its ticks, or {@code null} if the boat can't dock there.
	 */
	private static List<Object[]> search(CollisionMap map, SailingWaters waters, SailingVessel vessel,
		SailingMooring from, List<SailingMooring> exits)
	{
		BoatHull hull = vessel.hull();
		// The boat casts off anywhere in docking range of the berth it fits, facing any way; every move ends on sea its
		// hull fits, in the areas the boat may enter
		SailingSolver solver = new SailingSolver(map, SailingQuery.of(vessel.moves(), hull).withExits(exits)
			.landingOn(waters.landing(map, vessel, hull)));
		solver.castOff(waters.castOff(map, from, vessel), 0);
		List<Object[]> legs = new ArrayList<>(Collections.nCopies(exits.size(), null));
		solver.advance(Integer.MAX_VALUE, () -> false, new SailingSolver.Listener()
		{
			@Override
			public boolean exit(int exit, int node)
			{
				if (legs.get(exit) != null) return true;
				SailingLeg leg = solver.leg(node, exits.get(exit).berthTile());
				Set<SeaArea> areas = EnumSet.noneOf(SeaArea.class);
				for (int i = 0; i < leg.size(); i++)
				{
					SeaArea area = waters.area(leg.point(i));
					if (area != null) areas.add(area);
				}
				legs.set(exit, new Object[] {areas, solver.cost(node)});
				return true;
			}
		});
		solver.release();
		return legs;
	}

	/** The files' header, for {@code fingerprint}: the column names first, as the transport parser needs. */
	static String header(String fingerprint)
	{
		StringBuilder columns = new StringBuilder("# Origin\tDestination\tSkills\tQuests\tSea areas\tDuration");
		for (int i = 0; i < LegTimes.SPEEDS.length; i++) columns.append('\t').append(LegTimes.column(i));
		return columns + "\tDisplay info\n"
			+ comment("# Sailing the player's own boat between moorings; generated by shortest-path-tooling's sailingData"
			+ " task, do not edit.")
			+ comment("# At <speed>: boarding (" + SailingMooring.BOARD_TICKS + " ticks), sailing from the first berth to"
			+ " within docking range of the second")
			+ comment("# at that many tiles per tick with every move ending on sea the hull fits, and stepping off ("
			+ SailingMooring.DISEMBARK_TICKS + " ticks),")
			+ comment("# then the restricted sea areas the leg sails through, with every part the boat can be fitted with;"
			+ " empty where")
			+ comment("# the boat can't dock there at that speed. Duration: the fastest of them. Sea areas: every area at"
			+ " any speed.")
			+ comment("# Skills and Quests: both moorings' docking requirements.")
			+ comment(SailingTransports.FINGERPRINT + fingerprint);
	}

	// A comment line, padded with tabs to the header's columns, as the plugin's TSV linters want of every line
	private static String comment(String text)
	{
		return text + "\t".repeat(COLUMNS - 1) + "\n";
	}

	private static String point(int packed)
	{
		return WorldPointUtil.unpackWorldX(packed) + " " + WorldPointUtil.unpackWorldY(packed) + " "
			+ WorldPointUtil.unpackWorldPlane(packed);
	}

	private static String skills(SailingMooring from, SailingMooring to)
	{
		List<String> skills = new ArrayList<>();
		for (Skill skill : Skill.values())
		{
			int level = Math.max(from.requirements().getSkillLevels().get(skill),
				to.requirements().getSkillLevels().get(skill));
			if (level > 1) skills.add(level + " " + skill.getName());
		}
		return String.join(";", skills);
	}

	private static String quests(SailingMooring from, SailingMooring to)
	{
		Set<String> quests = new TreeSet<>();
		for (Quest quest : from.requirements().getQuests()) quests.add(quest.getName());
		for (Quest quest : to.requirements().getQuests()) quests.add(quest.getName());
		return String.join(";", quests);
	}
}
