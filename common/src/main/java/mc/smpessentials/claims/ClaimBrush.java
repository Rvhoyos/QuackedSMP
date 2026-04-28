package mc.smpessentials.claims;

import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// In-memory per-player claim brush size. Resets to 1 on server restart.
public final class ClaimBrush {

    private static final Map<UUID, Integer> brushSizes = new HashMap<>();

    private ClaimBrush() {}

    public static int get(UUID player) {
        return brushSizes.getOrDefault(player, 1);
    }

    private static final int MAX_BRUSH = 7;

    // Stores the brush size, rounded down to the nearest odd number (min 1, max 7).
    public static void set(UUID player, int raw) {
        int size = Math.clamp(raw, 1, MAX_BRUSH);
        if (size % 2 == 0) size--;
        brushSizes.put(player, size);
    }

    // Returns chunks in spiral order from center outward for an NxN grid (N must be odd).
    public static List<ChunkPos> spiral(ChunkPos center, int size) {
        List<ChunkPos> result = new ArrayList<>(size * size);
        result.add(center);
        if (size <= 1) return result;

        int radius = size / 2;
        for (int ring = 1; ring <= radius; ring++) {
            int x = center.x() - ring;
            int z = center.z() - ring;
            int side = ring * 2;

            // Top edge: left to right
            for (int i = 0; i < side; i++) {
                result.add(new ChunkPos(x + i, z));
            }
            // Right edge: top to bottom
            for (int i = 0; i < side; i++) {
                result.add(new ChunkPos(x + side, z + i));
            }
            // Bottom edge: right to left
            for (int i = side; i > 0; i--) {
                result.add(new ChunkPos(x + i, z + side));
            }
            // Left edge: bottom to top
            for (int i = side; i > 0; i--) {
                result.add(new ChunkPos(x, z + i));
            }
        }
        return result;
    }
}
