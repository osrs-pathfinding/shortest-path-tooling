package shortestpath.pathfinder;

import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import shortestpath.Destination;
import shortestpath.DestinationRequirements;
import shortestpath.ShortestPathConfig;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportLoader;

/** Resource-sharing PathfinderConfig for the headless service. */
public final class ServicePathfinderConfig extends PathfinderConfig
{
	private static final class Resources
	{
		static final SplitFlagMap MAP = SplitFlagMap.fromResources();
		static final Map<Integer, Set<Transport>> TRANSPORTS = transports();
		static final Map<String, Set<Integer>> DESTINATIONS = Destination.loadAllFromResources();
		static final Map<String, Set<Integer>> FILTERED = PathfinderConfig.filterDestinations(DESTINATIONS);
		static final Map<Integer, DestinationRequirements> BANKS = Destination.loadBankRequirementsFromResources();

		private static Map<Integer, Set<Transport>> transports()
		{
			Map<Integer, Set<Transport>> transports = TransportLoader.loadAllFromResources();
			PathfinderConfig.remapPohDestinations(transports);
			return transports;
		}
	}

	private final Set<String> completedQuests;
	private final long nowMinutes;

	public ServicePathfinderConfig(Client client, ShortestPathConfig config,
		Set<String> completedQuests, long nowMinutes)
	{
		super(client, config, Resources.MAP, Resources.TRANSPORTS, Resources.DESTINATIONS,
			Resources.FILTERED, Resources.BANKS);
		this.completedQuests = Set.copyOf(completedQuests);
		this.nowMinutes = nowMinutes;
	}

	@Override public QuestState getQuestState(Quest quest)
	{
		return completedQuests.contains(quest.getName()) ? QuestState.FINISHED : QuestState.NOT_STARTED;
	}

	@Override protected long currentTimeMinutes()
	{
		return nowMinutes;
	}
}
