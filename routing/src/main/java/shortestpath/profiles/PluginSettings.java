package shortestpath.profiles;

import java.util.EnumSet;
import java.util.Set;
import shortestpath.poh.JewelleryBoxTier;
import shortestpath.ShortestPathConfig;
import shortestpath.settings.KeyedValues;
import shortestpath.settings.TeleportationItem;
import shortestpath.accounts.Poh;
import shortestpath.poh.PohNexusPortal;
import shortestpath.poh.PohMountedItem;

/**
 * The plugin settings a route is planned with: a mutable {@link ShortestPathConfig}. Every
 * {@link Profiles profile} starts from one, and scenarios override it with
 * {@code .settings(s -> s.setUseFairyRings(true))}.
 * <p>
 * Every functionally relevant setting has a field and a setter ({@code ConfigParityTest} keeps
 * them in step with the plugin's config items).
 * <p>
 * Display and UI methods (drawMap, drawMinimap, colours, hotkeys …) are not overridden and
 * fall through to the {@link ShortestPathConfig} interface defaults, which are never called
 * during pathfinding.
 * <p>
 * Defaults are chosen to match the {@link ShortestPathConfig} interface defaults so that
 * scenarios only state the settings they care about, with three
 * intentional exceptions:
 * <ul>
 *   <li>{@code calculationCutoff} = 500 (interface default is 5 — far too small for long routes)</li>
 *   <li>{@code useTeleportationItems} = {@code NONE} (profiles always set this explicitly)</li>
 *   <li>{@code currencyThreshold} = 10 000 000 (generous; ensures currency-gated routes are never
 *       blocked by a threshold)</li>
 * </ul>
 */
public class PluginSettings implements ShortestPathConfig, KeyedValues {

    // -------------------------------------------------------------------------
    // Transport toggles
    // -------------------------------------------------------------------------
    private boolean avoidWilderness = true;
    private boolean useAgilityShortcuts = true;
    private boolean useGrappleShortcuts = false;
    private boolean useBoats = true;
    private boolean useCanoes = false;
    private boolean useCharterShips = false;
    private boolean useShips = true;
    private boolean useFairyRings = true;
    private boolean useGnomeGliders = true;
    private boolean useHotAirBalloons = false;
    private boolean useMagicCarpets = true;
    private boolean useMagicMushtrees = true;
    private boolean useMinecarts = true;
    private boolean useQuetzals = true;
    private boolean useSpiritTrees = true;
    private TeleportationItem useTeleportationItems = TeleportationItem.NONE;
    private boolean useTeleportationLevers = true;
    private boolean useTeleportationPortals = true;
    private boolean useTeleportationSpells = true;
    private boolean useTeleportationSpellsHome = true;
    private boolean useTeleportationMinigames = true;
    private boolean useWildernessObelisks = true;
    private boolean useSeasonalTransports = false;

    // -------------------------------------------------------------------------
    // Pathfinder behaviour
    // -------------------------------------------------------------------------
    private boolean includeBankPath = false;
    private boolean bypassVarbitChecks = true;
    private boolean bypassVarPlayerChecks = true;
    private int currencyThreshold = 10_000_000;
    private int calculationCutoff = 500;
    private int unreachableTargetDistance = 2;
    private boolean collisionAwareBlockedTargets = true;
    private int aStarHeuristicWeight = 100;
    private boolean useSailingMoves = true;

    // -------------------------------------------------------------------------
    // POH settings
    // -------------------------------------------------------------------------
    private boolean usePoh = false;
    private boolean usePohFairyRing = false;
    private boolean usePohSpiritTree = false;
    private boolean useTeleportationPortalsPoh = false;
    private Set<PohNexusPortal> pohNexusPortals = EnumSet.noneOf(PohNexusPortal.class);
    private JewelleryBoxTier pohJewelleryBoxTier = JewelleryBoxTier.ORNATE;
    private Set<PohMountedItem> pohMountedItems = EnumSet.allOf(PohMountedItem.class);
    private boolean usePohObelisk = false;

    // -------------------------------------------------------------------------
    // Built-item state (stored as serialised strings)
    // -------------------------------------------------------------------------
    private String builtTeleportationBoxes = "";
    private String builtTeleportationPortalsPoh = "";
    private String blockedTeleportItems = "";

    // -------------------------------------------------------------------------
    // Cost thresholds
    // -------------------------------------------------------------------------
    private int costAgilityShortcuts = 0;
    private int costGrappleShortcuts = 0;
    private int costBoats = 0;
    private int costCanoes = 0;
    private int costCharterShips = 0;
    private int costShips = 0;
    private int costFairyRings = 0;
    private int costGnomeGliders = 0;
    private int costHotAirBalloons = 0;
    private int costMagicCarpets = 0;
    private int costMagicMushtrees = 0;
    private int costMinecarts = 0;
    private int costQuetzals = 0;
    private int costQuetzalWhistle = 15;
    private int costSpiritTrees = 0;
    private int costNonConsumableTeleportationItems = 0;
    private int costConsumableTeleportationItems = 0;
    private int costTeleportationBoxes = 0;
    private int costTeleportationLevers = 0;
    private int costTeleportationPortals = 0;
    private int costTeleportationSpells = 0;
    private int costTeleportationSpellsHome = 0;
    private int costTeleportationMinigames = 0;
    private int costWildernessObelisks = 0;
    private int costSeasonalTransports = 0;
    private int costBankVisit = 0;

    // -------------------------------------------------------------------------
    // Unlocks (config-declared states the game does not expose)
    // -------------------------------------------------------------------------
    private boolean respawnPrifddinas = false;
    private boolean unlockCanoeAxe = false;
    private boolean unlockXericsHonour = false;
    private boolean unlockDragontoothPassage = false;
    private boolean unlockMorUlRek = false;
    private boolean unlockBalloonLogBasket = false;

    // =========================================================================
    // Getters (implements ShortestPathConfig)
    // =========================================================================

    public boolean avoidWilderness() { return avoidWilderness; }
    public boolean useAgilityShortcuts() { return useAgilityShortcuts; }
    public boolean useGrappleShortcuts() { return useGrappleShortcuts; }
    public boolean useBoats() { return useBoats; }
    public boolean useCanoes() { return useCanoes; }
    public boolean useCharterShips() { return useCharterShips; }
    public boolean useShips() { return useShips; }
    public boolean useFairyRings() { return useFairyRings; }
    public boolean useGnomeGliders() { return useGnomeGliders; }
    public boolean useHotAirBalloons() { return useHotAirBalloons; }
    public boolean useMagicCarpets() { return useMagicCarpets; }
    public boolean useMagicMushtrees() { return useMagicMushtrees; }
    public boolean useMinecarts() { return useMinecarts; }
    public boolean useQuetzals() { return useQuetzals; }
    public boolean useSpiritTrees() { return useSpiritTrees; }
    public TeleportationItem useTeleportationItems() { return useTeleportationItems; }
    public boolean useTeleportationLevers() { return useTeleportationLevers; }
    public boolean useTeleportationPortals() { return useTeleportationPortals; }
    public boolean useTeleportationSpells() { return useTeleportationSpells; }
    public boolean useTeleportationSpellsHome() { return useTeleportationSpellsHome; }
    public boolean useTeleportationMinigames() { return useTeleportationMinigames; }
    public boolean useWildernessObelisks() { return useWildernessObelisks; }
    public boolean useSeasonalTransports() { return useSeasonalTransports; }

    public boolean includeBankPath() { return includeBankPath; }
    public int currencyThreshold() { return currencyThreshold; }
    public int calculationCutoff() { return calculationCutoff; }
    public int unreachableTargetDistance() { return unreachableTargetDistance; }
    public boolean collisionAwareBlockedTargets() { return collisionAwareBlockedTargets; }
    public int aStarHeuristicWeight() { return aStarHeuristicWeight; }
    // Not marked @Override: plugin checkouts without the sailing search don't have this option (see src/sailing)
    public boolean useSailingMoves() { return useSailingMoves; }

    public boolean usePoh() { return usePoh; }
    public boolean usePohFairyRing() { return usePohFairyRing; }
    public boolean usePohSpiritTree() { return usePohSpiritTree; }
    public boolean useTeleportationPortalsPoh() { return useTeleportationPortalsPoh; }
    public Set<PohNexusPortal> pohNexusPortals() { return pohNexusPortals; }
    public JewelleryBoxTier pohJewelleryBoxTier() { return pohJewelleryBoxTier; }
    public boolean usePohMountedItems() { return !pohMountedItems.isEmpty(); }
    public Set<PohMountedItem> pohMountedItems() { return pohMountedItems; }
    public boolean usePohObelisk() { return usePohObelisk; }

    public String builtTeleportationBoxes() { return builtTeleportationBoxes; }
    public String builtTeleportationPortalsPoh() { return builtTeleportationPortalsPoh; }
    public String blockedTeleportItems() { return blockedTeleportItems; }

    public int costAgilityShortcuts() { return costAgilityShortcuts; }
    public int costGrappleShortcuts() { return costGrappleShortcuts; }
    public int costBoats() { return costBoats; }
    public int costCanoes() { return costCanoes; }
    public int costCharterShips() { return costCharterShips; }
    public int costShips() { return costShips; }
    public int costFairyRings() { return costFairyRings; }
    public int costGnomeGliders() { return costGnomeGliders; }
    public int costHotAirBalloons() { return costHotAirBalloons; }
    public int costMagicCarpets() { return costMagicCarpets; }
    public int costMagicMushtrees() { return costMagicMushtrees; }
    public int costMinecarts() { return costMinecarts; }
    public int costQuetzals() { return costQuetzals; }
    public int costQuetzalWhistle() { return costQuetzalWhistle; }
    public int costSpiritTrees() { return costSpiritTrees; }
    public int costNonConsumableTeleportationItems() { return costNonConsumableTeleportationItems; }
    public int costConsumableTeleportationItems() { return costConsumableTeleportationItems; }
    public int costTeleportationBoxes() { return costTeleportationBoxes; }
    public int costTeleportationLevers() { return costTeleportationLevers; }
    public int costTeleportationPortals() { return costTeleportationPortals; }
    public int costTeleportationSpells() { return costTeleportationSpells; }
    public int costTeleportationSpellsHome() { return costTeleportationSpellsHome; }
    public int costTeleportationMinigames() { return costTeleportationMinigames; }
    public int costWildernessObelisks() { return costWildernessObelisks; }
    public int costSeasonalTransports() { return costSeasonalTransports; }
    public int costBankVisit() { return costBankVisit; }
    public boolean respawnPrifddinas() { return respawnPrifddinas; }
    public boolean unlockCanoeAxe() { return unlockCanoeAxe; }
    public boolean unlockXericsHonour() { return unlockXericsHonour; }
    public boolean unlockDragontoothPassage() { return unlockDragontoothPassage; }
    public boolean unlockMorUlRek() { return unlockMorUlRek; }
    public boolean unlockBalloonLogBasket() { return unlockBalloonLogBasket; }

    // =========================================================================
    // Written state setters (were interface methods; persisted by key)
    // =========================================================================

    public void setBuiltTeleportationBoxes(String content) {
        this.builtTeleportationBoxes = content != null ? content : "";
    }

    public void setBuiltTeleportationPortalsPoh(String content) {
        this.builtTeleportationPortalsPoh = content != null ? content : "";
    }

    // =========================================================================
    // Setters
    // =========================================================================

    public void setAvoidWilderness(boolean v) { avoidWilderness = v; }
    public void setUseAgilityShortcuts(boolean v) { useAgilityShortcuts = v; }
    public void setUseGrappleShortcuts(boolean v) { useGrappleShortcuts = v; }
    public void setUseBoats(boolean v) { useBoats = v; }
    public void setUseCanoes(boolean v) { useCanoes = v; }
    public void setUseCharterShips(boolean v) { useCharterShips = v; }
    public void setUseShips(boolean v) { useShips = v; }
    public void setUseFairyRings(boolean v) { useFairyRings = v; }
    public void setUseGnomeGliders(boolean v) { useGnomeGliders = v; }
    public void setUseHotAirBalloons(boolean v) { useHotAirBalloons = v; }
    public void setUseMagicCarpets(boolean v) { useMagicCarpets = v; }
    public void setUseMagicMushtrees(boolean v) { useMagicMushtrees = v; }
    public void setUseMinecarts(boolean v) { useMinecarts = v; }
    public void setUseQuetzals(boolean v) { useQuetzals = v; }
    public void setUseSpiritTrees(boolean v) { useSpiritTrees = v; }
    public void setUseTeleportationItems(TeleportationItem v) { useTeleportationItems = v; }
    public void setUseTeleportationLevers(boolean v) { useTeleportationLevers = v; }
    public void setUseTeleportationPortals(boolean v) { useTeleportationPortals = v; }
    public void setUseTeleportationSpells(boolean v) { useTeleportationSpells = v; }
    public void setUseTeleportationSpellsHome(boolean v) { useTeleportationSpellsHome = v; }
    public void setUseTeleportationMinigames(boolean v) { useTeleportationMinigames = v; }
    public void setUseWildernessObelisks(boolean v) { useWildernessObelisks = v; }
    public void setUseSeasonalTransports(boolean v) { useSeasonalTransports = v; }

    public void setIncludeBankPath(boolean v) { includeBankPath = v; }
    public boolean isBypassVarbitChecks() { return bypassVarbitChecks; }
    public void setBypassVarbitChecks(boolean v) { bypassVarbitChecks = v; }
    public boolean isBypassVarPlayerChecks() { return bypassVarPlayerChecks; }
    public void setBypassVarPlayerChecks(boolean v) { bypassVarPlayerChecks = v; }
    public void setCurrencyThreshold(int v) { currencyThreshold = v; }
    public void setCalculationCutoff(int v) { calculationCutoff = v; }
    public void setUnreachableTargetDistance(int v) { unreachableTargetDistance = v; }
    public void setCollisionAwareBlockedTargets(boolean v) { collisionAwareBlockedTargets = v; }
    public void setAStarHeuristicWeight(int v) { aStarHeuristicWeight = v; }
    public void setUseSailingMoves(boolean v) { useSailingMoves = v; }

    public void setUsePoh(boolean v) { usePoh = v; }
    public void setUsePohFairyRing(boolean v) { usePohFairyRing = v; }
    public void setUsePohSpiritTree(boolean v) { usePohSpiritTree = v; }
    public void setUseTeleportationPortalsPoh(boolean v) {
        useTeleportationPortalsPoh = v;
        pohNexusPortals = v ? EnumSet.allOf(PohNexusPortal.class) : EnumSet.noneOf(PohNexusPortal.class);
    }
    public void setPohNexusPortals(Set<PohNexusPortal> v) {
        pohNexusPortals = v == null || v.isEmpty()
            ? EnumSet.noneOf(PohNexusPortal.class) : EnumSet.copyOf(v);
    }
    public void setPohJewelleryBoxTier(JewelleryBoxTier v) { pohJewelleryBoxTier = v; }
    public void setUsePohMountedItems(boolean v) {
        pohMountedItems = v ? EnumSet.allOf(PohMountedItem.class) : EnumSet.noneOf(PohMountedItem.class);
    }
    public void setPohMountedItems(Set<PohMountedItem> v) {
        pohMountedItems = v == null || v.isEmpty()
            ? EnumSet.noneOf(PohMountedItem.class) : EnumSet.copyOf(v);
    }
    public void setUsePohObelisk(boolean v) { usePohObelisk = v; }

    public void setCostAgilityShortcuts(int v) { costAgilityShortcuts = v; }
    public void setCostGrappleShortcuts(int v) { costGrappleShortcuts = v; }
    public void setCostBoats(int v) { costBoats = v; }
    public void setCostCanoes(int v) { costCanoes = v; }
    public void setCostCharterShips(int v) { costCharterShips = v; }
    public void setCostShips(int v) { costShips = v; }
    public void setCostFairyRings(int v) { costFairyRings = v; }
    public void setCostGnomeGliders(int v) { costGnomeGliders = v; }
    public void setCostHotAirBalloons(int v) { costHotAirBalloons = v; }
    public void setCostMagicCarpets(int v) { costMagicCarpets = v; }
    public void setCostMagicMushtrees(int v) { costMagicMushtrees = v; }
    public void setCostMinecarts(int v) { costMinecarts = v; }
    public void setCostQuetzals(int v) { costQuetzals = v; }
    public void setCostQuetzalWhistle(int v) { costQuetzalWhistle = v; }
    public void setCostSpiritTrees(int v) { costSpiritTrees = v; }
    public void setCostNonConsumableTeleportationItems(int v) { costNonConsumableTeleportationItems = v; }
    public void setCostConsumableTeleportationItems(int v) { costConsumableTeleportationItems = v; }
    public void setCostTeleportationBoxes(int v) { costTeleportationBoxes = v; }
    public void setCostTeleportationLevers(int v) { costTeleportationLevers = v; }
    public void setCostTeleportationPortals(int v) { costTeleportationPortals = v; }
    public void setCostTeleportationSpells(int v) { costTeleportationSpells = v; }
    public void setCostTeleportationSpellsHome(int v) { costTeleportationSpellsHome = v; }
    public void setCostTeleportationMinigames(int v) { costTeleportationMinigames = v; }
    public void setCostWildernessObelisks(int v) { costWildernessObelisks = v; }
    public void setCostSeasonalTransports(int v) { costSeasonalTransports = v; }
    public void setCostBankVisit(int v) { costBankVisit = v; }
    public void setRespawnPrifddinas(boolean v) { respawnPrifddinas = v; }
    public void setUnlockCanoeAxe(boolean v) { unlockCanoeAxe = v; }
    public void setUnlockXericsHonour(boolean v) { unlockXericsHonour = v; }
    public void setUnlockDragontoothPassage(boolean v) { unlockDragontoothPassage = v; }
    public void setUnlockMorUlRek(boolean v) { unlockMorUlRek = v; }

    /**
     * Sets the POH settings from the account's facilities: the POH is used, and its fairy ring,
     * spirit tree, obelisk, mounted items and nexus portals only when the route allows transports.
     */
    public void applyPoh(Poh poh, boolean allowTransports) {
        setUsePoh(true);
        setUsePohFairyRing(allowTransports && poh.fairyRing);
        setUsePohSpiritTree(allowTransports && poh.spiritTree);
        setUsePohObelisk(allowTransports && poh.obelisk);
        setPohJewelleryBoxTier(JewelleryBoxTier.valueOf(poh.jewelleryBox.name()));
        setPohMountedItems(allowTransports ? mountedItems(poh) : EnumSet.noneOf(PohMountedItem.class));
        Set<PohNexusPortal> portals = nexusPortals(poh);
        setUseTeleportationPortalsPoh(allowTransports && !portals.isEmpty());
        setPohNexusPortals(portals);
    }

    static Set<PohNexusPortal> nexusPortals(Poh poh) {
        if (poh.portals == null) {
            return EnumSet.allOf(PohNexusPortal.class);
        }
        Set<PohNexusPortal> result = EnumSet.noneOf(PohNexusPortal.class);
        for (String displayInfo : poh.portals) {
            PohNexusPortal portal = PohNexusPortal.fromDisplayInfo(displayInfo);
            if (portal == null) {
                throw new IllegalArgumentException("unknown POH portal: " + displayInfo);
            }
            result.add(portal);
        }
        return result;
    }

    static Set<PohMountedItem> mountedItems(Poh poh) {
        Set<PohMountedItem> result = EnumSet.noneOf(PohMountedItem.class);
        if (poh.mountedGlory) result.add(PohMountedItem.GLORY);
        if (poh.mountedXerics) result.add(PohMountedItem.XERICS_TALISMAN);
        if (poh.mountedDigsite) result.add(PohMountedItem.DIGSITE_PENDANT);
        if (poh.mountedMythical) result.add(PohMountedItem.MYTHICAL_CAPE);
        return result;
    }

    public void setUnlockBalloonLogBasket(boolean v) { unlockBalloonLogBasket = v; }
    public void setBlockedTeleportItems(String v) { blockedTeleportItems = v != null ? v : ""; }

    @Override
    public Object value(String keyName) {
        switch (keyName) {
            case "avoidWilderness": return avoidWilderness;
            case "useAgilityShortcuts": return useAgilityShortcuts;
            case "useGrappleShortcuts": return useGrappleShortcuts;
            case "useBoats": return useBoats;
            case "useCanoes": return useCanoes;
            case "useCharterShips": return useCharterShips;
            case "useShips": return useShips;
            case "useFairyRings": return useFairyRings;
            case "useGnomeGliders": return useGnomeGliders;
            case "useHotAirBalloons": return useHotAirBalloons;
            case "useMagicCarpets": return useMagicCarpets;
            case "useMagicMushtrees": return useMagicMushtrees;
            case "useMinecarts": return useMinecarts;
            case "useQuetzals": return useQuetzals;
            case "useSpiritTrees": return useSpiritTrees;
            case "useTeleportationItems": return useTeleportationItems;
            case "useTeleportationLevers": return useTeleportationLevers;
            case "useTeleportationPortals": return useTeleportationPortals;
            case "useTeleportationSpells": return useTeleportationSpells;
            case "useTeleportationSpellsHome": return useTeleportationSpellsHome;
            case "useTeleportationMinigames": return useTeleportationMinigames;
            case "useWildernessObelisks": return useWildernessObelisks;
            case "useSeasonalTransports": return useSeasonalTransports;
            case "respawnPrifddinas": return respawnPrifddinas;
            case "unlockCanoeAxe": return unlockCanoeAxe;
            case "unlockXericsHonour": return unlockXericsHonour;
            case "unlockDragontoothPassage": return unlockDragontoothPassage;
            case "unlockMorUlRek": return unlockMorUlRek;
            case "includeBankPath": return includeBankPath;
            case "currencyThreshold": return currencyThreshold;
            case "unreachableTargetDistanceThreshold": return unreachableTargetDistance;
            case "calculationCutoff": return calculationCutoff;
            case "aStarHeuristicWeight": return aStarHeuristicWeight;
            case "collisionAwareBlockedTargets": return collisionAwareBlockedTargets;
            case "useSailingMoves": return useSailingMoves;
            case "usePoh": return usePoh;
            case "usePohFairyRing": return usePohFairyRing;
            case "usePohSpiritTree": return usePohSpiritTree;
            case "useTeleportationPortalsPoh": return useTeleportationPortalsPoh;
            case "pohNexusPortals": return pohNexusPortals;
            case "pohJewelleryBoxTier": return pohJewelleryBoxTier;
            case "pohMountedItems": return pohMountedItems;
            case "usePohObelisk": return usePohObelisk;
            case "costAgilityShortcuts": return costAgilityShortcuts;
            case "costGrappleShortcuts": return costGrappleShortcuts;
            case "costBoats": return costBoats;
            case "costCanoes": return costCanoes;
            case "costCharterShips": return costCharterShips;
            case "costShips": return costShips;
            case "costFairyRings": return costFairyRings;
            case "costGnomeGliders": return costGnomeGliders;
            case "costHotAirBalloons": return costHotAirBalloons;
            case "costMagicCarpets": return costMagicCarpets;
            case "costMagicMushtrees": return costMagicMushtrees;
            case "costMinecarts": return costMinecarts;
            case "costQuetzals": return costQuetzals;
            case "costQuetzalWhistle": return costQuetzalWhistle;
            case "costSpiritTrees": return costSpiritTrees;
            case "costNonConsumableTeleportationItems": return costNonConsumableTeleportationItems;
            case "costConsumableTeleportationItems": return costConsumableTeleportationItems;
            case "costTeleportationBoxes": return costTeleportationBoxes;
            case "costTeleportationLevers": return costTeleportationLevers;
            case "costTeleportationPortals": return costTeleportationPortals;
            case "costTeleportationSpells": return costTeleportationSpells;
            case "costTeleportationSpellsHome": return costTeleportationSpellsHome;
            case "costTeleportationMinigames": return costTeleportationMinigames;
            case "costWildernessObelisks": return costWildernessObelisks;
            case "costSeasonalTransports": return costSeasonalTransports;
            case "costBankVisit": return costBankVisit;
            case "builtTeleportationBoxes": return builtTeleportationBoxes;
            case "builtTeleportationPortalsPoh": return builtTeleportationPortalsPoh;
            case "blockedTeleportItems": return blockedTeleportItems;
            case "unlockBalloonLogBasket": return unlockBalloonLogBasket;
            default: return null;
        }
    }
}
