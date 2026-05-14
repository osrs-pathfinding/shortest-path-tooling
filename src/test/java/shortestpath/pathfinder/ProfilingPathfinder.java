package shortestpath.pathfinder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueModeState;
import shortestpath.transport.Transport;

/**
 * Test-only pathfinder that replicates the core search loop from {@link Pathfinder}
 * with full profiling instrumentation. This class never ships in the production build.
 *
 * <p>The search algorithm is kept structurally identical to
 * {@code Pathfinder.run()} so that the {@code profilingDoesNotAffectResults}
 * test can catch any drift.</p>
 */
public class ProfilingPathfinder {

    private final PathfinderConfig config;
    private final CollisionMap map;
    private final int start;
    private final Set<Integer> targets;
    private final boolean targetInWilderness;
    private final boolean targetInBlockedRegion;

    private final Deque<Node> boundary = new ArrayDeque<>(4096);
    private final Queue<TransportNode> pending = new PriorityQueue<>(256);
    private final VisitedTiles visited;

    private PathfinderProfile profile;
    private PathfinderResult result;

    private Node bestLastNode;
    private int bestRemainingDistance = Integer.MAX_VALUE;
    private int bestTravelledDistance = Integer.MAX_VALUE;
    private int bestX = Integer.MAX_VALUE;
    private int bestY = Integer.MAX_VALUE;
    private int reachedTarget = WorldPointUtil.UNDEFINED;
    private PathTerminationReason terminationReason;
    private int wildernessLevel;

    private int nodesChecked;
    private int transportsChecked;

    // Shared neighbor list, matching CollisionMap's single-threaded assumption
    private final List<Node> neighbors = new ArrayList<>(16);
    private final boolean[] traversable = new boolean[8];
    private static final OrdinalDirection[] ORDINAL_VALUES = OrdinalDirection.values();
    private static final boolean[] IS_CARDINAL = {true, true, true, true, false, false, false, false};
    private static final int PACKED_Y_STEP = 1 << 15;
    private static final int[] PACKED_OFFSETS = {
        -1, +1, -PACKED_Y_STEP, +PACKED_Y_STEP,
        -1 - PACKED_Y_STEP, +1 - PACKED_Y_STEP, -1 + PACKED_Y_STEP, +1 + PACKED_Y_STEP,
    };

    private static boolean anyInBlockedRegion(LeagueModeState league, Set<Integer> packed) {
        if (!league.isSeasonal() || packed == null || packed.isEmpty()) {
            return false;
        }
        for (Integer point : packed) {
            if (league.isInBlockedRegion(point)) {
                return true;
            }
        }
        return false;
    }

    public ProfilingPathfinder(PathfinderConfig config, int start, Set<Integer> targets) {
        this.config = config;
        this.map = config.getMap();
        this.start = start;
        this.targets = targets;
        this.visited = new VisitedTiles(map);
        this.targetInWilderness = WildernessChecker.isInWilderness(targets);
        this.targetInBlockedRegion = anyInBlockedRegion(config.getLeagueModeState(), targets);
        this.wildernessLevel = 31;
        this.profile = new PathfinderProfile();
    }

    /**
     * Runs the pathfinding algorithm with full profiling. After this method
     * returns, {@link #getProfile()} and {@link #getResult()} are available.
     */
    public void run() {
        long startNanos = System.nanoTime();
        boundary.addFirst(new Node(start, null, 0, false));

        long cutoffDurationMillis = config.getCalculationCutoffMillis();
        long cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
        int iteration = 0;

        while (!boundary.isEmpty() || !pending.isEmpty()) {
            // ── Queue selection phase ──
            long phaseStart = System.nanoTime();

            Node node = boundary.peekFirst();
            TransportNode p = pending.peek();

            if (p != null && (node == null || p.compareCost() < node.cost)) {
                node = pending.poll();

                // For delayed-visit nodes, check if the destination was already
                // reached by a cheaper path while this node was queued.
                if (node instanceof TransportNode && ((TransportNode) node).delayedVisit) {
                    if (visited.get(node.packedPosition, node.bankVisited)) {
                        profile.delayedVisitSkipped++;
                        profile.queueSelectionNanos += System.nanoTime() - phaseStart;
                        continue;
                    }
                    visited.set(node.packedPosition, node.bankVisited);
                }
            } else {
                node = boundary.removeFirst();
            }
            if (node == null) {
                profile.queueSelectionNanos += System.nanoTime() - phaseStart;
                continue;
            }

            profile.queueSelectionNanos += System.nanoTime() - phaseStart;

            // ── Wilderness check phase ──
            if (node.isTile()) {
                phaseStart = System.nanoTime();
                updateWildernessLevel(node);
                profile.wildernessCheckNanos += System.nanoTime() - phaseStart;
            }

            // ── Target check phase ──
            phaseStart = System.nanoTime();

            if (node.isTile() && targets.contains(node.packedPosition)) {
                bestLastNode = node;
                reachedTarget = node.packedPosition;
                terminationReason = PathTerminationReason.TARGET_REACHED;
                profile.targetCheckNanos += System.nanoTime() - phaseStart;
                break;
            }

            if (node.isTile() && updateBestPathWhenUnreachable(node)) {
                cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
            }

            profile.targetCheckNanos += System.nanoTime() - phaseStart;

            // ── Cutoff check phase ──
            phaseStart = System.nanoTime();

            if ((iteration & 0xFFF) == 0 && System.currentTimeMillis() > cutoffTimeMillis) {
                terminationReason = PathTerminationReason.CUTOFF_REACHED;
                profile.cutoffCheckNanos += System.nanoTime() - phaseStart;
                break;
            }

            profile.cutoffCheckNanos += System.nanoTime() - phaseStart;

            // ── addNeighbors phase ──
            phaseStart = System.nanoTime();
            addNeighbors(node);
            profile.addNeighborsNanos += System.nanoTime() - phaseStart;

            // ── Bookkeeping phase ──
            phaseStart = System.nanoTime();

            profile.updatePeakBoundarySize(boundary.size());
            profile.updatePeakPendingSize(pending.size());
            iteration++;
            if (profile.shouldSample(iteration)) {
                profile.recordSample(iteration, boundary.size(), pending.size(),
                    node.cost, System.nanoTime() - startNanos);
            }

            profile.bookkeepingNanos += System.nanoTime() - phaseStart;
        }

        if (terminationReason == null) {
            terminationReason = PathTerminationReason.SEARCH_EXHAUSTED;
        }

        long elapsedNanos = System.nanoTime() - startNanos;

        boundary.clear();
        visited.clear();
        pending.clear();

        boolean reached = reachedTarget != WorldPointUtil.UNDEFINED;
        int target = reached ? reachedTarget : (targets.isEmpty() ? WorldPointUtil.UNDEFINED : targets.iterator().next());
        int closestReached = bestLastNode != null ? bestLastNode.getClosestTilePosition() : start;
        List<PathStep> path = bestLastNode != null ? bestLastNode.getPathSteps() : List.of();

        result = new PathfinderResult(start, target, reached, path, closestReached,
            nodesChecked, transportsChecked, elapsedNanos, terminationReason);
    }

    // ── addNeighbors: matches Pathfinder.addNeighbors exactly ───────────

    private void addNeighbors(Node node) {
        List<Node> nodes;
        int boundaryBefore = boundary.size();
        if (node.isTile()) {
            nodes = getTileNeighbors(node);
        } else {
            // ── Abstract node expansion sub-phase ──
            long abstractExpansionStart = System.nanoTime();
            nodes = getAbstractNodeNeighbors(node);
            profile.abstractNodeNanos += System.nanoTime() - abstractExpansionStart;
            boundaryBefore = boundary.size(); // abstract nodes don't push to boundary
        }
        profile.tileNeighborsAdded += boundary.size() - boundaryBefore;
        nodesChecked += boundary.size() - boundaryBefore;

        // ── Enqueue sub-phase ──
        long enqueueStart = System.nanoTime();

        for (Node neighbor : nodes) {
            if (node.isTile() && neighbor.isTile()
                && config.avoidWilderness(node.packedPosition, neighbor.packedPosition, targetInWilderness)) {
                continue;
            }

            if (node.isTile() && neighbor.isTile()
                && config.avoidBlockedRegion(node.packedPosition, neighbor.packedPosition, targetInBlockedRegion)) {
                continue;
            }

            if (!(neighbor instanceof TransportNode && ((TransportNode) neighbor).delayedVisit)) {
                visited.set(neighbor);
            } else {
                profile.delayedVisitEnqueued++;
            }
            if (neighbor instanceof TransportNode) {
                pending.add((TransportNode) neighbor);
                ++transportsChecked;
                profile.transportNeighborsAdded++;
            } else {
                boundary.addLast(neighbor);
                ++nodesChecked;
                profile.tileNeighborsAdded++;
            }
        }

        // Tile visit counting for tile nodes
        if (node.isTile()) {
            profile.incrementTileVisit(node.packedPosition);
        }

        profile.enqueueNanos += System.nanoTime() - enqueueStart;
    }

    // ── getTileNeighbors: matches CollisionMap.getTileNeighbors ─────────
    // Only calls visited.get() (never visited.set()), returns the neighbor list.

    private List<Node> getTileNeighbors(Node node) {
        final int x = WorldPointUtil.unpackWorldX(node.packedPosition);
        final int y = WorldPointUtil.unpackWorldY(node.packedPosition);
        final int z = WorldPointUtil.unpackWorldPlane(node.packedPosition);

        neighbors.clear();

        // ── Bank check sub-phase ──
        long subStart = System.nanoTime();

        boolean pathBankVisited = node.bankVisited
            || (config.isBankPathEnabled() && config.bankAccessible(node.packedPosition));

        profile.bankCheckNanos += System.nanoTime() - subStart;
        if (pathBankVisited && !node.bankVisited) {
            profile.bankTransitions++;
        }

        // ── Transport lookup sub-phase ──
        subStart = System.nanoTime();

        Set<Transport> transports = config.getTransportsPacked(pathBankVisited).getOrDefault(node.packedPosition, Set.of());
        int inheritedDifferential = (node instanceof TransportNode && ((TransportNode) node).delayedVisit)
            ? ((TransportNode) node).differentialCost
            : 0;
        for (Transport transport : transports) {
            profile.transportEvaluations++;
            boolean delayedVisit = transport.getType().sharesDestinationsWith() != null;
            if (!delayedVisit && visited.get(transport.getDestination(), pathBankVisited)) {
                profile.visitedSkipped++;
                continue;
            }
            int chainPenalty = (delayedVisit && inheritedDifferential > 0) ? inheritedDifferential : 0;
            neighbors.add(new TransportNode(
                transport.getDestination(), node,
                transport.getDuration(), config.getAdditionalTransportCost(transport) + chainPenalty,
                pathBankVisited,
                delayedVisit,
                delayedVisit ? config.getDifferentialCost(transport) : 0));
        }

        profile.transportLookupNanos += System.nanoTime() - subStart;

        // ── Abstract node sub-phase ──
        subStart = System.nanoTime();

        AbstractNodeKind kind = AbstractNodeKind.fromWildernessLevel(wildernessLevel);
        if (!visited.isAbstractVisited(kind, pathBankVisited))
        {
            neighbors.add(Node.abstractNode(kind, node, pathBankVisited));
            profile.abstractNodesExpanded++;
        }

        profile.abstractNodeNanos += System.nanoTime() - subStart;

        // ── Collision check sub-phase ──
        subStart = System.nanoTime();

        // Mirror CollisionMap.getTileNeighbors: cache the current region's FlagMap
        // and use 5×5 bulk flag fetch from raw long[] (C5).
        FlagMap fm = map.getFlagMap(x, y);
        int regionX = x >>> 6;
        int regionY = y >>> 6;
        final boolean sameRegion = ((x - 2) >>> 6) == regionX && ((x + 2) >>> 6) == regionX
            && ((y - 2) >>> 6) == regionY && ((y + 2) >>> 6) == regionY;
        int nMask, eMask;
        if (fm != null && sameRegion)
        {
            long bulk = fm.bulkFlags5x5(x - 2, y - 2, z);
            nMask = (int) bulk;
            eMask = (int) (bulk >>> 32);
        }
        else
        {
            nMask = 0;
            eMask = 0;
            for (int row = 0; row < 5; row++)
            {
                int ty = y - 2 + row;
                for (int col = 0; col < 5; col++)
                {
                    int tx = x - 2 + col;
                    int pair = map.getPair(fm, regionX, regionY, tx, ty, z);
                    int bit = row * 5 + col;
                    nMask |= ((pair & 1) << bit);
                    eMask |= (((pair >>> 1) & 1) << bit);
                }
            }
        }
        // Bit position = row*5+col. Centre tile (x,y) at row 2, col 2 = bit 12.
        final int bitCurN = 1 << 12;
        final int bitCurE = 1 << 12;
        final int bitS_N  = 1 << 7;
        final int bitW_E  = 1 << 11;

        if ((nMask & (bitCurN | bitS_N)) == 0 && (eMask & (bitCurE | bitW_E)) == 0)
        {
            // Blocked branch — use 5×5 masks for isBlocked checks.
            // blockedInMask: is tile at (row,col) blocked? S-edge = N(row-1,col), W-edge = E(row,col-1)
            boolean westBlocked      = ((nMask & ((1 << 11) | (1 << 6))) == 0 && (eMask & ((1 << 11) | (1 << 10))) == 0);
            boolean eastBlocked      = ((nMask & ((1 << 13) | (1 << 8))) == 0 && (eMask & ((1 << 13) | (1 << 12))) == 0);
            boolean southBlocked     = ((nMask & ((1 << 7)  | (1 << 2))) == 0 && (eMask & ((1 << 7)  | (1 << 6)))  == 0);
            boolean northBlocked     = ((nMask & ((1 << 17) | (1 << 12)))== 0 && (eMask & ((1 << 17) | (1 << 16))) == 0);
            boolean southWestBlocked = ((nMask & ((1 << 6)  | (1 << 1))) == 0 && (eMask & ((1 << 6)  | (1 << 5)))  == 0);
            boolean southEastBlocked = ((nMask & ((1 << 8)  | (1 << 3))) == 0 && (eMask & ((1 << 8)  | (1 << 7)))  == 0);
            boolean northWestBlocked = ((nMask & ((1 << 16) | (1 << 11)))== 0 && (eMask & ((1 << 16) | (1 << 15))) == 0);
            boolean northEastBlocked = ((nMask & ((1 << 18) | (1 << 13)))== 0 && (eMask & ((1 << 18) | (1 << 17))) == 0);
            traversable[0] = !westBlocked;
            traversable[1] = !eastBlocked;
            traversable[2] = !southBlocked;
            traversable[3] = !northBlocked;
            traversable[4] = !southWestBlocked && !westBlocked && !southBlocked;
            traversable[5] = !southEastBlocked && !eastBlocked && !southBlocked;
            traversable[6] = !northWestBlocked && !westBlocked && !northBlocked;
            traversable[7] = !northEastBlocked && !eastBlocked && !northBlocked;
        }
        else
        {
            // Unblocked branch — all direction checks from the 5×5 masks.
            traversable[0] = (eMask & bitW_E) != 0;
            traversable[1] = (eMask & bitCurE) != 0;
            traversable[2] = (nMask & bitS_N) != 0;
            traversable[3] = (nMask & bitCurN) != 0;

            boolean canS = traversable[2];
            boolean canN = traversable[3];
            boolean canW = traversable[0];
            boolean canE = traversable[1];

            // SW: canS && canW && W(x,y-1) && S(x-1,y) — both at bit 6
            traversable[4] = canS && canW
                && (eMask & (1 << 6)) != 0 && (nMask & (1 << 6)) != 0;

            // SE: canS && canE && E(x,y-1)=bit7 && S(x+1,y)=N(x+1,y-1)=bit8
            traversable[5] = canS && canE
                && (eMask & (1 << 7)) != 0 && (nMask & (1 << 8)) != 0;

            // NW: canN && canW && W(x,y+1)=E(x-1,y+1)=bit16 && N(x-1,y)=bit11
            traversable[6] = canN && canW
                && (eMask & (1 << 16)) != 0 && (nMask & (1 << 11)) != 0;

            // NE: canN && canE && E(x,y+1)=bit17 && N(x+1,y)=bit13
            traversable[7] = canN && canE
                && (eMask & (1 << 17)) != 0 && (nMask & (1 << 13)) != 0;
        }

        profile.collisionCheckNanos += System.nanoTime() - subStart;

        // ── Walkable tile iteration sub-phase ──
        subStart = System.nanoTime();

        for (int i = 0; i < traversable.length; i++) {
            OrdinalDirection d = ORDINAL_VALUES[i];
            final int nx = x + d.x;
            final int ny = y + d.y;
            if (visited.get(nx, ny, z, pathBankVisited)) continue;

            if (traversable[i]) {
                int neighborPacked = node.packedPosition + PACKED_OFFSETS[i];
                if (!config.avoidWilderness(node.packedPosition, neighborPacked, targetInWilderness)
                    && !config.avoidBlockedRegion(node.packedPosition, neighborPacked, targetInBlockedRegion)) {
                    visited.set(nx, ny, z, pathBankVisited);
                    boundary.addLast(new Node(neighborPacked, node, Node.cost(neighborPacked, node), pathBankVisited));
                }
            } else if (IS_CARDINAL[i] && map.isBlocked(nx, ny, z)) {
                // Blocked-tile transport fallback
                profile.walkableTileNanos += System.nanoTime() - subStart;
                subStart = System.nanoTime();

                int neighborPacked = node.packedPosition + PACKED_OFFSETS[i];
                Set<Transport> neighborTransports = config.getTransportsPacked(pathBankVisited).getOrDefault(neighborPacked, Set.of());
                for (Transport transport : neighborTransports) {
                    profile.blockedTileTransportChecks++;
                    if (transport.getOrigin() == Transport.UNDEFINED_ORIGIN
                        || !transport.isUsableAtWildernessLevel(wildernessLevel)
                        || visited.get(transport.getOrigin(), pathBankVisited)) {
                        continue;
                    }
                    neighbors.add(new Node(transport.getOrigin(), node, Node.cost(transport.getOrigin(), node), pathBankVisited));
                }

                profile.blockedTileTransportNanos += System.nanoTime() - subStart;
                subStart = System.nanoTime();
            }
        }

        profile.walkableTileNanos += System.nanoTime() - subStart;

        return neighbors;
    }

    // ── getAbstractNodeNeighbors: matches CollisionMap.getAbstractNodeNeighbors ──

    private List<Node> getAbstractNodeNeighbors(Node node) {
        neighbors.clear();
        int sourceTile = node.getClosestTilePosition();
        for (Transport transport : config.getUsableTeleports(node.bankVisited)) {
            profile.transportEvaluations++;
            boolean delayedVisit = transport.getType().sharesDestinationsWith() != null;
            if (!delayedVisit && visited.get(transport.getDestination(), node.bankVisited)) {
                profile.visitedSkipped++;
                continue;
            }
            if (!transport.isUsableAtWildernessLevel(node.abstractKind.maxWildernessLevel())) {
                continue;
            }
            if (config.avoidWilderness(sourceTile, transport.getDestination(), targetInWilderness)) {
                continue;
            }
            int differentialCost = delayedVisit ? config.getDifferentialCost(transport) : 0;
            neighbors.add(new TransportNode(
                transport.getDestination(), node,
                transport.getDuration(), config.getAdditionalTransportCost(transport),
                node.bankVisited,
                delayedVisit,
                differentialCost));
        }
        return neighbors;
    }

    private boolean updateBestPathWhenUnreachable(Node node) {
        boolean update = false;
        for (int target : targets) {
            int remainingDistance = WorldPointUtil.distanceBetween(target, node.packedPosition, WorldPointUtil.EUCLIDEAN_SQUARED_DISTANCE_METRIC);
            int travelledDistance = node.cost;
            int x = WorldPointUtil.unpackWorldX(node.packedPosition);
            int y = WorldPointUtil.unpackWorldY(node.packedPosition);
            if ((remainingDistance < bestRemainingDistance) ||
                (remainingDistance == bestRemainingDistance && travelledDistance < bestTravelledDistance) ||
                (remainingDistance == bestRemainingDistance && travelledDistance == bestTravelledDistance && x < bestX) ||
                (remainingDistance == bestRemainingDistance && travelledDistance == bestTravelledDistance && x == bestX && y < bestY)) {
                bestRemainingDistance = remainingDistance;
                bestTravelledDistance = travelledDistance;
                bestX = x;
                bestY = y;
                bestLastNode = node;
                update = true;
            }
        }
        return update;
    }

    private void updateWildernessLevel(Node node) {
        int previousLevel = wildernessLevel;
        if (wildernessLevel <= 0) {
            return;
        }
        if (wildernessLevel > 30) {
            if (!WildernessChecker.isInLevel30Wilderness(node.packedPosition)) {
                wildernessLevel = 30;
            }
            if (!WildernessChecker.isInLevel20Wilderness(node.packedPosition)) {
                wildernessLevel = 20;
            }
            if (!WildernessChecker.isInWilderness(node.packedPosition)) {
                wildernessLevel = 0;
            }
        } else if (wildernessLevel > 20) {
            if (!WildernessChecker.isInLevel20Wilderness(node.packedPosition)) {
                wildernessLevel = 20;
            }
            if (!WildernessChecker.isInWilderness(node.packedPosition)) {
                wildernessLevel = 0;
            }
        } else {
            if (!WildernessChecker.isInWilderness(node.packedPosition)) {
                wildernessLevel = 0;
            }
        }
        if (wildernessLevel != previousLevel) {
            profile.wildernessLevelChanges++;
        }
    }

    public int getStart() { return start; }
    public Set<Integer> getTargets() { return targets; }
    public PathfinderProfile getProfile() { return profile; }
    public PathfinderResult getResult() { return result; }
}
