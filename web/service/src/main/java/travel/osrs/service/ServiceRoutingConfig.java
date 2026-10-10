package travel.osrs.service;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import shortestpath.JewelleryBoxTier;
import shortestpath.ShortestPathConfig;
import shortestpath.TeleportationItem;
import shortestpath.transport.PohMountedItem;
import shortestpath.transport.PohNexusPortal;

/** Headless equivalent of shortest-path-tooling's DashboardPathfinderConfig. */
final class ServiceRoutingConfig implements ShortestPathConfig
{
	/** The threshold, in ticks, behind the policy's "avoid" and "preserve" choices. */
	static final int AVOID_THRESHOLD = 1000;

	private final ApiModels.AccountBuild account;
	private final ApiModels.RoutePolicy policy;
	private final Set<String> avoided;
	private final Set<String> unlocks;
	private String builtBoxes = "";
	private String builtPortals = "";

	ServiceRoutingConfig(ApiModels.AccountBuild account, ApiModels.RoutePolicy policy)
	{
		this.account = account;
		this.policy = policy;
		this.avoided = new HashSet<>(policy.avoidedTransportTypes);
		this.unlocks = new HashSet<>(policy.declaredUnlocks);
	}

	private boolean use(String type)
	{
		return !avoided.contains(type);
	}

	private int cost(String type)
	{
		return policy.transportThresholds.getOrDefault(type, 0);
	}

	private boolean banks()
	{
		return !"never".equals(policy.banking);
	}

	@Override public boolean avoidWilderness() { return policy.avoidWilderness; }
	@Override public boolean useAgilityShortcuts() { return use("AGILITY_SHORTCUT"); }
	@Override public boolean useGrappleShortcuts() { return use("GRAPPLE_SHORTCUT"); }
	@Override public boolean useBoats() { return use("BOAT"); }
	@Override public boolean useCanoes() { return use("CANOE"); }
	@Override public boolean useCharterShips() { return use("CHARTER_SHIP"); }
	@Override public boolean useShips() { return use("SHIP"); }
	@Override public boolean useFairyRings() { return use("FAIRY_RING"); }
	@Override public boolean useGnomeGliders() { return use("GNOME_GLIDER"); }
	@Override public boolean useHotAirBalloons() { return use("HOT_AIR_BALLOON"); }
	@Override public boolean useMagicCarpets() { return use("MAGIC_CARPET"); }
	@Override public boolean useMagicMushtrees() { return use("MAGIC_MUSHTREE"); }
	@Override public boolean useMinecarts() { return use("MINECART"); }
	@Override public boolean useQuetzals() { return use("QUETZAL"); }
	@Override public boolean useSpiritTrees() { return use("SPIRIT_TREE"); }
	@Override public TeleportationItem useTeleportationItems()
	{
		if (!use("TELEPORTATION_ITEM")) return TeleportationItem.NONE;
		boolean permanent = "permanent-only".equals(policy.resources);
		if ("any".equals(policy.teleportItems))
			return permanent ? TeleportationItem.ALL_NON_CONSUMABLE : TeleportationItem.ALL;
		// The bank modes force bank paths on, so banking "never" must use the carried-only modes.
		if (!banks())
			return permanent ? TeleportationItem.INVENTORY_NON_CONSUMABLE : TeleportationItem.INVENTORY;
		return permanent ? TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE : TeleportationItem.INVENTORY_AND_BANK;
	}
	@Override public boolean useTeleportationLevers() { return use("TELEPORTATION_LEVER"); }
	@Override public boolean useTeleportationPortals() { return use("TELEPORTATION_PORTAL"); }
	@Override public boolean useTeleportationSpells() { return use("TELEPORTATION_SPELL"); }
	@Override public boolean useTeleportationSpellsHome() { return use("TELEPORTATION_SPELL_HOME"); }
	@Override public boolean useTeleportationMinigames() { return use("TELEPORTATION_MINIGAME"); }
	@Override public boolean useWildernessObelisks() { return use("WILDERNESS_OBELISK"); }
	// Seasonal transports also need a seasonal world, which a public account never has.
	@Override public boolean useSeasonalTransports() { return false; }
	@Override public boolean includeBankPath() { return banks(); }
	@Override public int currencyThreshold()
	{
		return policy.currencyThreshold == null ? Integer.MAX_VALUE : policy.currencyThreshold;
	}
	@Override public int calculationCutoff() { return 25; }

	@Override public boolean respawnPrifddinas() { return unlocks.contains("PRIFDDINAS_RESPAWN"); }
	@Override public boolean unlockCanoeAxe() { return unlocks.contains("CANOE_AXE"); }
	@Override public boolean unlockXericsHonour() { return unlocks.contains("XERICS_HONOUR"); }
	@Override public boolean unlockDragontoothPassage() { return unlocks.contains("DRAGONTOOTH_PASSAGE"); }

	@Override public boolean usePoh() { return account.poh != null; }
	@Override public boolean usePohFairyRing() { return account.poh.fairyRing; }
	@Override public boolean usePohSpiritTree() { return account.poh.spiritTree; }
	@Override public boolean usePohObelisk() { return account.poh.obelisk; }
	@Override public boolean useTeleportationPortalsPoh() { return !pohNexusPortals().isEmpty(); }
	@Override public Set<PohNexusPortal> pohNexusPortals()
	{
		if ("all".equals(account.poh.portals.mode)) return EnumSet.allOf(PohNexusPortal.class);
		Set<PohNexusPortal> portals = EnumSet.noneOf(PohNexusPortal.class);
		for (String name : account.poh.portals.destinations)
		{
			PohNexusPortal portal = PohNexusPortal.fromDisplayInfo(name);
			if (portal == null) throw new IllegalArgumentException("unknown POH portal: " + name);
			portals.add(portal);
		}
		return portals;
	}
	@Override public JewelleryBoxTier pohJewelleryBoxTier()
	{
		switch (account.poh.jewelleryBox)
		{
			case "NoJewelleryBox": return JewelleryBoxTier.NONE;
			case "FancyJewelleryBox": return JewelleryBoxTier.FANCY;
			case "OrnateJewelleryBox": return JewelleryBoxTier.ORNATE;
			default: throw new IllegalArgumentException("unknown POH jewellery box: " + account.poh.jewelleryBox);
		}
	}
	@Override public Set<PohMountedItem> pohMountedItems()
	{
		Set<PohMountedItem> items = EnumSet.noneOf(PohMountedItem.class);
		if (account.poh.mountedGlory) items.add(PohMountedItem.GLORY);
		if (account.poh.mountedXerics) items.add(PohMountedItem.XERICS_TALISMAN);
		if (account.poh.mountedDigsite) items.add(PohMountedItem.DIGSITE_PENDANT);
		if (account.poh.mountedMythical) items.add(PohMountedItem.MYTHICAL_CAPE);
		return items;
	}

	@Override public int costAgilityShortcuts() { return cost("AGILITY_SHORTCUT"); }
	@Override public int costGrappleShortcuts() { return cost("GRAPPLE_SHORTCUT"); }
	@Override public int costBoats() { return cost("BOAT"); }
	@Override public int costCanoes() { return cost("CANOE"); }
	@Override public int costCharterShips() { return cost("CHARTER_SHIP"); }
	@Override public int costShips() { return cost("SHIP"); }
	@Override public int costFairyRings() { return cost("FAIRY_RING"); }
	@Override public int costGnomeGliders() { return cost("GNOME_GLIDER"); }
	@Override public int costHotAirBalloons() { return cost("HOT_AIR_BALLOON"); }
	@Override public int costMagicCarpets() { return cost("MAGIC_CARPET"); }
	@Override public int costMagicMushtrees() { return cost("MAGIC_MUSHTREE"); }
	@Override public int costMinecarts() { return cost("MINECART"); }
	@Override public int costQuetzals() { return cost("QUETZAL"); }
	@Override public int costSpiritTrees() { return cost("SPIRIT_TREE"); }
	@Override public int costNonConsumableTeleportationItems() { return cost("TELEPORTATION_ITEM"); }
	@Override public int costTeleportationBoxes() { return cost("TELEPORTATION_BOX"); }
	@Override public int costTeleportationLevers() { return cost("TELEPORTATION_LEVER"); }
	@Override public int costTeleportationPortals() { return cost("TELEPORTATION_PORTAL"); }
	@Override public int costTeleportationSpells() { return cost("TELEPORTATION_SPELL"); }
	@Override public int costTeleportationSpellsHome() { return cost("TELEPORTATION_SPELL_HOME"); }
	@Override public int costTeleportationMinigames() { return cost("TELEPORTATION_MINIGAME"); }
	@Override public int costWildernessObelisks() { return cost("WILDERNESS_OBELISK"); }
	// Consumable teleport items pay only this threshold, and quetzal whistles pay it on top of QUETZAL's.
	@Override public int costConsumableTeleportationItems()
	{
		return "preserve-consumables".equals(policy.resources) ? AVOID_THRESHOLD : 0;
	}
	@Override public int costBankVisit() { return "avoid".equals(policy.banking) ? AVOID_THRESHOLD : 0; }
	@Override public String builtTeleportationBoxes() { return builtBoxes; }
	@Override public void setBuiltTeleportationBoxes(String content) { builtBoxes = content == null ? "" : content; }
	@Override public String builtTeleportationPortalsPoh() { return builtPortals; }
	@Override public void setBuiltTeleportationPortalsPoh(String content) { builtPortals = content == null ? "" : content; }
}
