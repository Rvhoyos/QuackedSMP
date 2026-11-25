package mc.smpessentials.pregen;

import mc.smpessentials.claims.SpawnProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.border.WorldBorder;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * The square of chunks one dimension pre-generates, already resolved against the live server.
 *
 * The area is decided in overworld blocks (the spawn protection radius plus the configured
 * distance) and then projected into this dimension by its coordinate scale, so a nether-scaled
 * dimension covers the same ground as the overworld area rather than eight times as much.
 *
 * Chunks are addressed by a single index so a run can stop anywhere and resume from one number.
 */
record PregenArea(String dimension, int centerChunkX, int centerChunkZ,
                  int radiusBlocks, int sideChunks) {

    // Kept clear of the border for the same reason /rtp does: a landing or a chunk exactly on the
    // line is inside a wall the player cannot cross.
    private static final int BORDER_PADDING = 128;

    // Vanilla's default border is effectively unbounded, so anything at or above this is "not set".
    // Same threshold the BlueMap world border layer uses.
    private static final double UNBOUNDED = 5.9E7;

    /**
     * Resolves the area for one level, or empty when nothing is left to generate.
     *
     * @param distance blocks beyond the spawn protection radius, in overworld scale
     */
    static Optional<PregenArea> resolve(ServerLevel level, int distance) {
        if (distance <= 0) return Optional.empty();

        // Spawn protection only exists in the respawn dimension on a dedicated server, so every
        // other dimension contributes 0 here and pregens exactly the configured distance.
        int requested = SpawnProtection.radius(level) + distance;

        double scale = level.dimensionType().coordinateScale();
        int centerX = centerX(level, scale);
        int centerZ = centerZ(level, scale);

        int radius = (int) Math.round(requested / scale);
        radius = clampToBorder(level.getWorldBorder(), centerX, centerZ, radius);
        if (radius <= 0) return Optional.empty();

        int side = ((radius >> 4) * 2) + 1;
        return Optional.of(new PregenArea(level.dimension().identifier().toString(),
                centerX >> 4, centerZ >> 4, radius, side));
    }

    private static int centerX(ServerLevel level, double scale) {
        return (int) Math.round(level.getServer().overworld().getRespawnData().pos().getX() / scale);
    }

    private static int centerZ(ServerLevel level, double scale) {
        return (int) Math.round(level.getServer().overworld().getRespawnData().pos().getZ() / scale);
    }

    /**
     * The largest distance this dimension's world border leaves room for, or empty when it has no
     * border. The inverse of the clamp below, so a refusal can name the number that would fit
     * instead of quietly shrinking what the operator typed.
     */
    static OptionalInt maxDistanceWithinBorder(ServerLevel level) {
        WorldBorder border = level.getWorldBorder();
        if (border.getSize() >= UNBOUNDED) return OptionalInt.empty();

        double scale = level.dimensionType().coordinateScale();
        int maxRadius = clampToBorder(border, centerX(level, scale), centerZ(level, scale),
                Integer.MAX_VALUE);
        return OptionalInt.of(Math.max(0, (int) (maxRadius * scale) - SpawnProtection.radius(level)));
    }

    /**
     * Shrinks the radius until the whole square fits inside the border. Measured per side rather
     * than from the border's own size, because the area is centred on spawn and spawn is not
     * necessarily the border's centre.
     */
    private static int clampToBorder(WorldBorder border, int centerX, int centerZ, int radius) {
        if (border.getSize() >= UNBOUNDED) return radius;
        double room = Math.min(
                Math.min(centerX - border.getMinX(), border.getMaxX() - centerX),
                Math.min(centerZ - border.getMinZ(), border.getMaxZ() - centerZ));
        return (int) Math.min(radius, Math.max(0.0, room - BORDER_PADDING));
    }

    long chunkCount() {
        return (long) sideChunks * sideChunks;
    }

    /** The chunk at this position in the run, counting rows from the north-west corner. */
    ChunkPos chunkAt(long index) {
        long row = index / sideChunks;
        long col = index % sideChunks;
        int half = sideChunks / 2;
        return new ChunkPos(centerChunkX - half + (int) col, centerChunkZ - half + (int) row);
    }
}
