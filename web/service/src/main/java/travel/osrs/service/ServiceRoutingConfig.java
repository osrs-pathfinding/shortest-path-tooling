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
	private final ApiModels.AccountBuild account;
	private final ApiModels.RoutePolicy policy;
	private final Set<String> avoided;
	private String builtBoxes = "";
	private String builtPortals = "";

	ServiceRoutingConfig(ApiModels.AccountBuild account, ApiModels.RoutePolicy policy)
	{
		this.account = account;
		this.policy = policy;
		this.avoided = new HashSet<>(policy.avoidedTransportTypes);
	}

	private boolean use(String type)
	{
		return !avoided.contains(type);
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
	@Override public boolean useQuetzals() { return use("QUETZAL") && use("QUETZAL_WHISTLE"); }
	@Override public boolean useSpiritTrees() { return use("SPIRIT_TREE"); }
	@Override public TeleportationItem useTeleportationItems() {
		return use("TELEPORTATION_ITEM") ? TeleportationItem.INVENTORY_AND_BANK : TeleportationItem.NONE;
	}
	@Override public boolean useTeleportationLevers() { return use("TELEPORTATION_LEVER"); }
	@Override public boolean useTeleportationPortals() { return use("TELEPORTATION_PORTAL"); }
	@Override public boolean useTeleportationSpells() { return use("TELEPORTATION_SPELL"); }
	@Override public boolean useTeleportationSpellsHome() { return use("TELEPORTATION_SPELL_HOME"); }
	@Override public boolean useTeleportationMinigames() { return use("TELEPORTATION_MINIGAME"); }
	@Override public boolean useWildernessObelisks() { return use("WILDERNESS_OBELISK"); }
	@Override public boolean useSeasonalTransports() { return false; }
	@Override public boolean includeBankPath() { return !"never".equals(policy.banking); }
	@Override public int currencyThreshold() { return Integer.MAX_VALUE; }
	@Override public int calculationCutoff() { return 25; }

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

	@Override public int costConsumableTeleportationItems() {
		return "preserve-consumables".equals(policy.resources) ? 1000 : 0;
	}
	@Override public int costQuetzalWhistle() {
		return "preserve-consumables".equals(policy.resources) ? 1000 : 0;
	}
	@Override public String builtTeleportationBoxes() { return builtBoxes; }
	@Override public void setBuiltTeleportationBoxes(String content) { builtBoxes = content == null ? "" : content; }
	@Override public String builtTeleportationPortalsPoh() { return builtPortals; }
	@Override public void setBuiltTeleportationPortalsPoh(String content) { builtPortals = content == null ? "" : content; }
}
