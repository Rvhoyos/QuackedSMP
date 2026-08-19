package mc.smpessentials.timelapse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which part of a world a snapshot frames. A world picks up stray chunks
 * far from everything else (one trip toward the world border generates a few),
 * and a frame stretched to include them is nearly all empty space, which forces
 * the real terrain to downsample into noise. This groups regions that lie near
 * each other and keeps the group holding the most chunks.
 */
final class ChunkClusterFinder {

    // Empty regions tolerated between two regions of the same group. A region is
    // 512 blocks, so ungenerated gaps inside a world do not split it.
    private static final int GAP_REGIONS = 2;
    private static final int REACH = GAP_REGIONS + 1;

    private ChunkClusterFinder() {}

    /** The framed group: the regions to paint, the box they fill, and what was left out. */
    record Cluster(List<RegionPresence> regions, ChunkBounds bounds, int chunkCount, int excludedChunks) {}

    /** Returns the largest group of neighbouring regions, or null if nothing is generated. */
    static Cluster largest(List<RegionPresence> regions) {
        if (regions.isEmpty()) return null;

        Map<Long, RegionPresence> byPosition = new HashMap<>();
        int total = 0;
        for (RegionPresence region : regions) {
            byPosition.put(key(region.regionX(), region.regionZ()), region);
            total += region.chunkCount();
        }

        Set<Long> visited = new HashSet<>();
        List<RegionPresence> best = List.of();
        int bestCount = 0;
        for (RegionPresence seed : regions) {
            if (!visited.add(key(seed.regionX(), seed.regionZ()))) continue;
            List<RegionPresence> group = grow(seed, byPosition, visited);
            int count = 0;
            for (RegionPresence region : group) count += region.chunkCount();
            if (count > bestCount) {
                bestCount = count;
                best = group;
            }
        }
        return new Cluster(best, boundsOf(best), bestCount, total - bestCount);
    }

    // Breadth-first walk out from one region, claiming every region within reach.
    private static List<RegionPresence> grow(RegionPresence seed, Map<Long, RegionPresence> byPosition, Set<Long> visited) {
        List<RegionPresence> group = new ArrayList<>();
        ArrayDeque<RegionPresence> queue = new ArrayDeque<>();
        queue.add(seed);
        while (!queue.isEmpty()) {
            RegionPresence current = queue.poll();
            group.add(current);
            for (int dx = -REACH; dx <= REACH; dx++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    long neighbour = key(current.regionX() + dx, current.regionZ() + dz);
                    RegionPresence next = byPosition.get(neighbour);
                    if (next != null && visited.add(neighbour)) queue.add(next);
                }
            }
        }
        return group;
    }

    // Tight box around the chunks that actually exist, not around whole regions.
    private static ChunkBounds boundsOf(List<RegionPresence> regions) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (RegionPresence region : regions) {
            for (int lx = 0; lx < RegionPresence.SIDE; lx++) {
                for (int lz = 0; lz < RegionPresence.SIDE; lz++) {
                    if (!region.present(lx, lz)) continue;
                    int cx = region.chunkX(lx), cz = region.chunkZ(lz);
                    minX = Math.min(minX, cx);
                    maxX = Math.max(maxX, cx);
                    minZ = Math.min(minZ, cz);
                    maxZ = Math.max(maxZ, cz);
                }
            }
        }
        return new ChunkBounds(minX, minZ, maxX, maxZ);
    }

    private static long key(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
    }
}
