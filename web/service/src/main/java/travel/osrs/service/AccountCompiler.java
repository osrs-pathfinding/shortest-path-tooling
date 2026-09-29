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
		int total = levels.getOrDefault("Total", levels.entrySet().stream()
			.filter(entry -> !"Quest".equals(entry.getKey())).mapToInt(Map.Entry::getValue).sum());
		return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
			(proxy, method, args) -> {
				switch (method.getName())
				{
					case "getGameState": return GameState.LOGGED_IN;
					case "getClientThread": return Thread.currentThread();
					case "getWorldType": return EnumSet.noneOf(WorldType.class);
					case "getVarbitValue":
						int varbit = (Integer) args[0];
						if (varbit == VarbitID.FAIRY2_QUEENCURE_QUEST
							&& !account.routingVariables.varbits.containsKey(varbit))
							return account.fairyRingsUnlocked ? 100 : 0;
						return account.routingVariables.varbits.getOrDefault(varbit, 0);
					case "getVarpValue":
						int varp = (Integer) args[0];
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
