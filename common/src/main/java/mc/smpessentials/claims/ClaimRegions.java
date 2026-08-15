package mc.smpessentials.claims;

import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Pure chunk topology. A region is a maximal set of chunks connected N/S/E/W.
 * Callers scope the input set to a single owner and dimension; this class only
 * walks adjacency and holds no game state.
 */
public final class ClaimRegions {
    private ClaimRegions() {
    }

    /** Partitions {@code all} into connected components. */
    public static List<Set<ChunkPos>> connectedComponents(Set<ChunkPos> all) {
        List<Set<ChunkPos>> regions = new ArrayList<>();
        Set<ChunkPos> visited = new HashSet<>();
        for (ChunkPos start : all) {
            if (visited.contains(start))
                continue;
            regions.add(flood(all, start, visited));
        }
        return regions;
    }

    /** The connected component of {@code all} containing {@code origin}, or empty if origin is not in the set. */
    public static Set<ChunkPos> componentContaining(Set<ChunkPos> all, ChunkPos origin) {
        if (!all.contains(origin))
            return Set.of();
        return flood(all, origin, new HashSet<>());
    }

    private static Set<ChunkPos> flood(Set<ChunkPos> all, ChunkPos start, Set<ChunkPos> visited) {
        Set<ChunkPos> region = new HashSet<>();
        Queue<ChunkPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            ChunkPos curr = queue.poll();
            region.add(curr);
            for (ChunkPos n : neighbors(curr)) {
                if (all.contains(n) && visited.add(n)) {
                    queue.add(n);
                }
            }
        }
        return region;
    }

    public static ChunkPos[] neighbors(ChunkPos c) {
        int x = c.x();
        int z = c.z();
        return new ChunkPos[] {
                new ChunkPos(x + 1, z), new ChunkPos(x - 1, z),
                new ChunkPos(x, z + 1), new ChunkPos(x, z - 1)
        };
    }
}
