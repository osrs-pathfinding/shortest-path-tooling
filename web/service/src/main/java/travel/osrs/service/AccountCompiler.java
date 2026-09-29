package travel.osrs.service;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import shortestpath.ItemVariations;
import shortestpath.pathfinder.ServicePathfinderConfig;

final class AccountCompiler
{
	ServicePathfinderConfig compile(ApiModels.AccountBuild account, ApiModels.RoutePolicy policy)
	{
		Map<Integer, Integer> inventory = items("inventory", account.inventory);
		merge(inventory, items("runePouch", account.runePouch));
		ItemContainer carried = container(inventory);
		ItemContainer worn = container(items("equipment", account.equipment));
		Client client = client(account, carried, worn);
		ServicePathfinderConfig config = new ServicePathfinderConfig(client,
			new ServiceRoutingConfig(account, policy), new LinkedHashSet<>(account.completedQuests),
			account.benchmarkNowMinutes);
		config.bank = container(items("bank", account.bank));
		config.availableSpiritTrees = spiritTrees(account.plantedSpiritTrees);
		config.refresh();
		return config;
	}

	private static Client client(ApiModels.AccountBuild account, ItemContainer inventory, ItemContainer equipment)
	{
		Map<String, Integer> levels = account.levels;
		Map<Integer, Integer> semanticVarbits = semanticVarbits(account);
		Map<Integer, Integer> semanticVarplayers = semanticVarplayers(account);
		int total = levels.entrySet().stream()
			.filter(entry -> !"Quest".equals(entry.getKey()) && !"Total".equals(entry.getKey()))
			.mapToInt(Map.Entry::getValue).sum();
		return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
			(proxy, method, args) -> {
				switch (method.getName())
				{
					case "getGameState": return GameState.LOGGED_IN;
					case "getClientThread": return Thread.currentThread();
					case "getWorldType": return EnumSet.noneOf(WorldType.class);
					case "getVarbitValue":
						int varbit = (Integer) args[0];
						if (semanticVarbits.containsKey(varbit)) return semanticVarbits.get(varbit);
						return account.routingVariables.varbits.getOrDefault(varbit, 0);
					case "getVarpValue":
						int varp = (Integer) args[0];
						if (semanticVarplayers.containsKey(varp)) return semanticVarplayers.get(varp);
						if (varp == VarPlayerID.QP && !account.routingVariables.varplayers.containsKey(varp))
							return levels.getOrDefault("Quest", 0);
						return account.routingVariables.varplayers.getOrDefault(varp, 0);
					case "getBoostedSkillLevel":
					case "getRealSkillLevel": return levels.getOrDefault(skillName((Skill) args[0]), 1);
					case "getTotalLevel": return total;
					case "getItemContainer":
						int id = args[0] instanceof Integer ? (Integer) args[0] : ((net.runelite.api.InventoryID) args[0]).getId();
						return id == InventoryID.INV ? inventory : id == InventoryID.WORN ? equipment : null;
					case "getDBTableRows": return Collections.emptyList();
					case "toString": return "HeadlessClient(" + account.id + ")";
					case "hashCode": return System.identityHashCode(proxy);
					case "equals": return proxy == args[0];
					default: return defaultValue(method.getReturnType());
				}
			});
	}

	static Map<Integer, Integer> semanticVarbits(ApiModels.AccountBuild account)
	{
		Map<Integer, Integer> bits = new HashMap<>();
		bits.put(VarbitID.FAIRY2_QUEENCURE_QUEST, account.fairyRingsUnlocked ? 100 : 0);
		bits.put(VarbitID.SPELLBOOK, spellbook(account.runtime.spellbook));
		bits.put(VarbitID.POH_HOUSE_LOCATION, pohLocation(account.poh.location));
		diary(bits, account.diaries.getOrDefault("Ardougne", "NoDiary"), VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE, VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE, VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE);
		diary(bits, account.diaries.getOrDefault("Desert", "NoDiary"), VarbitID.DESERT_DIARY_EASY_COMPLETE, VarbitID.DESERT_DIARY_MEDIUM_COMPLETE, VarbitID.DESERT_DIARY_HARD_COMPLETE, VarbitID.DESERT_DIARY_ELITE_COMPLETE);
		diary(bits, account.diaries.getOrDefault("Falador", "NoDiary"), VarbitID.FALADOR_DIARY_EASY_COMPLETE, VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE, VarbitID.FALADOR_DIARY_HARD_COMPLETE, VarbitID.FALADOR_DIARY_ELITE_COMPLETE);
		diary(bits, account.diaries.getOrDefault("Fremennik", "NoDiary"), VarbitID.FREMENNIK_DIARY_EASY_COMPLETE, VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE, VarbitID.FREMENNIK_DIARY_HARD_COMPLETE, VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE);
		diary(bits, account.diaries.getOrDefault("Kandarin", "NoDiary"), VarbitID.KANDARIN_DIARY_EASY_COMPLETE, VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE, VarbitID.KANDARIN_DIARY_HARD_COMPLETE, VarbitID.KANDARIN_DIARY_ELITE_COMPLETE);
		diary(bits, account.diaries.getOrDefault("Karamja", "NoDiary"), VarbitID.ATJUN_EASY_DONE, VarbitID.ATJUN_MED_DONE, VarbitID.ATJUN_HARD_DONE, VarbitID.KARAMJA_DIARY_ELITE_COMPLETE);
		diary(bits, account.diaries.getOrDefault("KourendKebos", "NoDiary"), VarbitID.KOUREND_DIARY_EASY_COMPLETE, VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE, VarbitID.KOUREND_DIARY_HARD_COMPLETE, VarbitID.KOUREND_DIARY_ELITE_COMPLETE);
		diary(bits, account.diaries.getOrDefault("LumbridgeDraynor", "NoDiary"), VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE, VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE, VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE, VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE);
		diary(bits, account.diaries.getOrDefault("Morytania", "NoDiary"), VarbitID.MORYTANIA_DIARY_EASY_COMPLETE, VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE, VarbitID.MORYTANIA_DIARY_HARD_COMPLETE, VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE);
		diary(bits, account.diaries.getOrDefault("Varrock", "NoDiary"), VarbitID.VARROCK_DIARY_EASY_COMPLETE, VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE, VarbitID.VARROCK_DIARY_HARD_COMPLETE, VarbitID.VARROCK_DIARY_ELITE_COMPLETE);
		diary(bits, account.diaries.getOrDefault("WesternProvinces", "NoDiary"), VarbitID.WESTERN_DIARY_EASY_COMPLETE, VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE, VarbitID.WESTERN_DIARY_HARD_COMPLETE, VarbitID.WESTERN_DIARY_ELITE_COMPLETE);
		diary(bits, account.diaries.getOrDefault("Wilderness", "NoDiary"), VarbitID.WILDERNESS_DIARY_EASY_COMPLETE, VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE, VarbitID.WILDERNESS_DIARY_HARD_COMPLETE, VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE);
		questBit(bits, account, VarbitID.LOVAQUEST, "The Forsaken Tower", 11);
		questBit(bits, account, VarbitID.MY2ARM_STATUS, "Making Friends with My Arm", 207);
		questBit(bits, account, VarbitID.THZFE_BLOCKING_BARRICADE, "Zogre Flesh Eaters", 1);
		questBit(bits, account, VarbitID.HOSIDIUSQUEST, "The Depths of Despair", 7);
		questBit(bits, account, VarbitID.DRAGONSLAYER_CRANDOR_FOUND_SECRET_DOOR, "Dragon Slayer I", 1);
		questBit(bits, account, VarbitID.MYQ3_MAIN_QUEST, "Darkness of Hallowvale", 320);
		questBit(bits, account, VarbitID.MDAUGHTER_QUEST_VAR, "Mountain Daughter", 70);
		questBit(bits, account, VarbitID.DWARFROCK_QUEST, "Between a Rock...", 10);
		questBit(bits, account, VarbitID.GOLEM_A, "The Golem", 10);
		questBit(bits, account, VarbitID.ICS_LITTLE_VAR, "Icthlarin's Little Helper", 26);
		questBit(bits, account, VarbitID.TOG_JUNA_BOWL, "Tears of Guthix", 2);
		questBit(bits, account, VarbitID.ZOGRE, "Zogre Flesh Eaters", 14);
		questBit(bits, account, VarbitID.LOST_TRIBE_QUEST, "The Lost Tribe", 12);
		questBit(bits, account, VarbitID.SWANSONG, "Swan Song", 200);
		questBit(bits, account, VarbitID.FRIS_QUEST, "The Fremennik Isles", 340);
		questBit(bits, account, VarbitID.VEOS_PROGRESS, "Client of Kourend", 1);
		questBit(bits, account, VarbitID.HOSIDIUSQUEST_REWARD, "The Depths of Despair", 1);
		questBit(bits, account, VarbitID.PISCQUEST_REWARD, "The Queen of Thieves", 1);
		questBit(bits, account, VarbitID.SHAYZIENQUEST_REWARD, "The Tale of the Righteous", 1);
		questBit(bits, account, VarbitID.LOVAQUEST_REWARD, "The Forsaken Tower", 1);
		questBit(bits, account, VarbitID.ARCQUEST_REWARD, "Architectural Alliance", 1);
		questBit(bits, account, VarbitID.BCS, "Beneath Cursed Sands", 108);
		return bits;
	}

	static Map<Integer, Integer> semanticVarplayers(ApiModels.AccountBuild account)
	{
		Map<Integer, Integer> players = new HashMap<>();
		players.put(VarPlayerID.SLUG2_REGIONUID, "ready".equals(account.runtime.minigameTeleport.state)
			? Math.toIntExact(account.benchmarkNowMinutes - 21) : Math.toIntExact(account.runtime.minigameTeleport.minutes));
		questPlayer(players, account, VarPlayerID.LEGENDSQUEST, "Legends' Quest", 75);
		questPlayer(players, account, VarPlayerID.ZOMBIEQUEEN, "Shilo Village", 15);
		questPlayer(players, account, VarPlayerID.WATERFALL_QUEST, "Waterfall Quest", 10);
		questPlayer(players, account, VarPlayerID.FISHINGCOMPO, "Fishing Contest", 5);
		questPlayer(players, account, VarPlayerID.TREEQUEST, "Tree Gnome Village", 9);
		questPlayer(players, account, VarPlayerID.GRANDTREE, "The Grand Tree", 160);
		questPlayer(players, account, VarPlayerID.ELENAQUEST, "Plague City", 30);
		questPlayer(players, account, VarPlayerID.DRAGONQUEST, "Dragon Slayer I", 10);
		questPlayer(players, account, VarPlayerID.ITWATCHTOWER, "Watchtower", 14);
		questPlayer(players, account, VarPlayerID.REGICIDE_QUEST, "Regicide", 15);
		questPlayer(players, account, VarPlayerID.MISC_QUEST, "Throne of Miscellania", 100);
		questPlayer(players, account, VarPlayerID.MOURNING_QUEST, "Mourning's End Part I", 9);
		return players;
	}

	private static void questBit(Map<Integer, Integer> target, ApiModels.AccountBuild account, int id, String quest, int complete)
	{
		target.put(id, account.completedQuests.contains(quest) ? complete : 0);
	}

	private static void questPlayer(Map<Integer, Integer> target, ApiModels.AccountBuild account, int id, String quest, int complete)
	{
		target.put(id, account.completedQuests.contains(quest) ? complete : 0);
	}

	private static void diary(Map<Integer, Integer> target, String tier, int easy, int medium, int hard, int elite)
	{
		int level = List.of("NoDiary", "Easy", "Medium", "Hard", "Elite").indexOf(tier);
		if (level < 0) throw new IllegalArgumentException("unknown diary tier: " + tier);
		target.put(easy, level >= 1 ? 1 : 0);
		target.put(medium, level >= 2 ? 1 : 0);
		target.put(hard, level >= 3 ? 1 : 0);
		target.put(elite, level >= 4 ? 1 : 0);
	}

	private static int spellbook(String value)
	{
		switch (value)
		{
			case "Standard": return 0;
			case "Ancient": return 1;
			case "Lunar": return 2;
			case "Arceuus": return 3;
			default: throw new IllegalArgumentException("unknown spellbook: " + value);
		}
	}

	private static int pohLocation(String value)
	{
		switch (value)
		{
			case "Rimmington": return 1;
			case "Taverley": return 2;
			case "Pollnivneach": return 3;
			case "Rellekka": return 4;
			case "Brimhaven": return 5;
			case "Yanille": return 6;
			case "Prifddinas": return 7;
			case "Hosidius": return 8;
			case "Aldarin": return 9;
			default: throw new IllegalArgumentException("unknown POH location: " + value);
		}
	}

	private static ItemContainer container(Map<Integer, Integer> values)
	{
		Item[] items = values.entrySet().stream().map(entry -> new Item(entry.getKey(), entry.getValue()))
			.toArray(Item[]::new);
		return (ItemContainer) Proxy.newProxyInstance(ItemContainer.class.getClassLoader(),
			new Class<?>[] {ItemContainer.class}, (proxy, method, args) -> {
				switch (method.getName())
				{
					case "getItems": return items;
					case "size": case "count": return items.length;
					case "contains": return values.containsKey((Integer) args[0]);
					default: return defaultValue(method.getReturnType());
				}
			});
	}

	private static Map<Integer, Integer> items(String field, Map<String, Integer> source)
	{
		Map<Integer, Integer> result = new HashMap<>();
		for (Map.Entry<String, Integer> entry : source.entrySet())
		{
			int id;
			try { id = Integer.parseInt(entry.getKey()); }
			catch (NumberFormatException ignored)
			{
				ItemVariations variation = ItemVariations.fromName(entry.getKey());
				if (variation == null) throw new IllegalArgumentException("unknown item in " + field + ": " + entry.getKey());
				id = variation.getIds()[0];
			}
			result.merge(id, entry.getValue(), Math::addExact);
		}
		return result;
	}

	private static void merge(Map<Integer, Integer> target, Map<Integer, Integer> source)
	{
		source.forEach((id, quantity) -> target.merge(id, quantity, Math::addExact));
	}

	private static Set<String> spiritTrees(List<String> names)
	{
		Set<String> result = new LinkedHashSet<>();
		for (String name : names)
		{
			switch (name)
			{
				case "FARMING_GUILD": result.add("Farming Guild"); break;
				case "PORT_SARIM": result.add("Port Sarim"); break;
				case "ETCETERIA": result.add("Etceteria"); break;
				case "BRIMHAVEN": result.add("Brimhaven"); break;
				case "HOSIDIUS": result.add("Hosidius"); break;
				default: throw new IllegalArgumentException("unknown planted spirit tree: " + name);
			}
		}
		return Collections.unmodifiableSet(result);
	}

	private static String skillName(Skill skill)
	{
		String lower = skill.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private static Object defaultValue(Class<?> type)
	{
		if (!type.isPrimitive()) return null;
		if (type == boolean.class) return false;
		if (type == char.class) return '\0';
		if (type == byte.class) return (byte) 0;
		if (type == short.class) return (short) 0;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == float.class) return 0F;
		return 0D;
	}
}
