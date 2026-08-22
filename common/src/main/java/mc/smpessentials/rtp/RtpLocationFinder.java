package mc.smpessentials.rtp;

import mc.smpessentials.claims.SpawnProtection;
import mc.smpessentials.config.ConfigData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Picks random landing spots for one profile in one level.
 *
 * Each call to {@link #tryOnce} draws a single candidate and reports whether a player could stand
 * there. Callers are expected to spread repeated calls over several ticks, because testing a
 * candidate can force a chunk to generate.
 *
 * Not thread safe: built, used and dropped on the server thread.
 */
final class RtpLocationFinder {
    private static final int BORDER_PADDING = 128;
    private static final int PLAYER_HEIGHT = 2;
    private static final int NO_FLOOR = Integer.MIN_VALUE;

    private final ServerLevel level;
    private final ConfigData.RtpProfile profile;
    private final boolean roofed;
    private final BlockPos center;
    private final double minRadius;
    private final double maxRadius;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    RtpLocationFinder(ServerLevel level, ConfigData.RtpProfile profile) {
        this.level = level;
        this.profile = profile;
        this.roofed = level.dimensionType().hasCeiling();
        this.center = centerOf(level);

        WorldBorder border = level.getWorldBorder();
        // The world border is the hard ceiling. A profile can sit inside it, but clearing
        // maxDistance (0 or less) hands the whole border to /rtp.
        double borderLimit = Math.max(0.0, (border.getSize() / 2.0) - BORDER_PADDING);
        this.maxRadius = profile.maxDistance > 0
                ? Math.min(profile.maxDistance, borderLimit)
                : borderLimit;
        // Start outside spawn protection, then however much further the profile asks for.
        double floor = SpawnProtection.radius(level) + Math.max(0, profile.minDistance);
        this.minRadius = Math.min(floor, this.maxRadius);
    }

    /**
     * Draws one candidate and returns the position a player should be placed at, or empty when
     * that candidate is unusable. Empty is normal and simply means try again.
     */
    Optional<BlockPos> tryOnce() {
        double radius = drawRadius();
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
        int x = clampToBorder(this.center.getX() + (int) (radius * Math.cos(angle)), true);
        int z = clampToBorder(this.center.getZ() + (int) (radius * Math.sin(angle)), false);
        return landingAt(x, z);
    }

    /** Blocks between a landing spot and the centre distances are measured from. */
    int distanceFromCenter(BlockPos pos) {
        double dx = pos.getX() - this.center.getX();
        double dz = pos.getZ() - this.center.getZ();
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    /** Uniform at bias 1; higher values weight the draw toward the minimum. */
    private double drawRadius() {
        double bias = this.profile.spawnBias > 0 ? this.profile.spawnBias : 1.0;
        double u = ThreadLocalRandom.current().nextDouble();
        return this.minRadius + (this.maxRadius - this.minRadius) * Math.pow(u, bias);
    }

    private Optional<BlockPos> landingAt(int x, int z) {
        // Force the chunk so the heightmap and block states are real. This is the expensive part
        // of a candidate, and the reason callers test one candidate per tick.
        this.level.getChunk(x >> 4, z >> 4);

        int floorY = floorHeight(x, z);
        if (floorY == NO_FLOOR) return Optional.empty();
        if (isUnsafeFloor(this.level.getBlockState(this.cursor.set(x, floorY, z)))) return Optional.empty();
        if (!hasHeadroom(x, floorY, z)) return Optional.empty();

        return Optional.of(new BlockPos(x, floorY + 1, z));
    }

    /**
     * Y of the block a player would stand on, or NO_FLOOR when the column has nothing to land on.
     * Roofed dimensions need a downward walk, because the heightmap reports the bedrock ceiling
     * rather than the cavern floor beneath it.
     */
    private int floorHeight(int x, int z) {
        if (!this.roofed) {
            int surface = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            return surface >= this.level.getMinY() ? surface : NO_FLOOR;
        }

        // Three states walking down: above the ceiling, inside the ceiling, then in open air,
        // where the next solid block is the floor we want.
        boolean reachedCeiling = false;
        boolean belowCeiling = false;
        for (int y = this.level.getMaxY(); y > this.level.getMinY(); y--) {
            boolean solid = this.level.getBlockState(this.cursor.set(x, y, z)).blocksMotion();
            if (!reachedCeiling) {
                reachedCeiling = solid;
            } else if (!belowCeiling) {
                belowCeiling = !solid;
            } else if (solid) {
                return y;
            }
        }
        return NO_FLOOR;
    }

    private boolean hasHeadroom(int x, int floorY, int z) {
        for (int dy = 1; dy <= PLAYER_HEIGHT; dy++) {
            BlockState state = this.level.getBlockState(this.cursor.set(x, floorY + dy, z));
            if (state.blocksMotion() || isDeadly(state)) return false;
        }
        return true;
    }

    private boolean isUnsafeFloor(BlockState floor) {
        if (isDeadly(floor)) return true;
        return floor.is(Blocks.WATER) && !this.profile.allowWater;
    }

    private static boolean isDeadly(BlockState state) {
        return state.is(Blocks.LAVA)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(BlockTags.FIRE);
    }

    /**
     * Where distances are measured from. The overworld uses the server spawn, which is what
     * players understand "distance from spawn" to mean; other dimensions have no spawn point of
     * their own, so they use the centre of their world border.
     */
    private static BlockPos centerOf(ServerLevel level) {
        if (level.dimension() == Level.OVERWORLD) return level.getRespawnData().pos();
        WorldBorder border = level.getWorldBorder();
        return BlockPos.containing(border.getCenterX(), 0.0, border.getCenterZ());
    }

    private int clampToBorder(int value, boolean isX) {
        WorldBorder border = this.level.getWorldBorder();
        double min = (isX ? border.getMinX() : border.getMinZ()) + BORDER_PADDING;
        double max = (isX ? border.getMaxX() : border.getMaxZ()) - BORDER_PADDING;
        return (int) Math.max(min, Math.min(max, value));
    }
}
