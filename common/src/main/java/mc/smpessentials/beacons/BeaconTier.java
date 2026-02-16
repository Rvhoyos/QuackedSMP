package mc.smpessentials.beacons;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public enum BeaconTier {
    NONE(0, 0),
    IRON(1, 24), // 3x3 Chunks (~24 blocks radius)
    GOLD(1, 24), // Same as Iron
    EMERALD(2, 40), // 5x5 Chunks
    DIAMOND(3, 56), // 7x7 Chunks
    NETHERITE(4, 72); // 9x9 Chunks

    public final int level;
    public final int rangeBlocks; // Approximate radius for range checking

    BeaconTier(int level, int rangeBlocks) {
        this.level = level;
        this.rangeBlocks = rangeBlocks;
    }

    public static BeaconTier fromBlock(BlockState state) {
        if (state.is(Blocks.NETHERITE_BLOCK))
            return NETHERITE;
        if (state.is(Blocks.DIAMOND_BLOCK))
            return DIAMOND;
        if (state.is(Blocks.EMERALD_BLOCK))
            return EMERALD;
        if (state.is(Blocks.GOLD_BLOCK))
            return GOLD;
        if (state.is(Blocks.IRON_BLOCK))
            return IRON;
        return NONE;
    }

    public int getChunkRadius() {
        // 1 -> 3x3 (radius 1)
        // 2 -> 5x5 (radius 2)
        // 3 -> 7x7 (radius 3)
        // 4 -> 9x9 (radius 4)
        // Read from config. Default to hardcoded values if missing (safe fallback)
        int size = mc.smpessentials.config.SmpConfig.BEACON_SIZES.getOrDefault(this.name().toLowerCase(), 3);
        // Radius = (Size - 1) / 2. e.g. Size 3 -> Radius 1. Size 9 -> Radius 4.
        return Math.max(0, (size - 1) / 2);
    }
}
