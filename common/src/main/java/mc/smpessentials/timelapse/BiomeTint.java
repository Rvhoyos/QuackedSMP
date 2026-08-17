package mc.smpessentials.timelapse;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Resolves the base (unshaded) map colour of a surface block as a packed
 * {@code 0xRRGGBB} value.
 *
 * The colour comes from the block's averaged texture ({@link BlockColorPalette})
 * so different blocks read distinctly, instead of vanilla's 62-entry
 * {@link MapColor} palette. Blocks whose colour is biome-dependent (grass,
 * foliage) are multiplied by the biome's tint the way the game tints those
 * greyscale textures; water is coloured by the biome and darkened by depth.
 * Blocks with no palette entry fall back to their flat {@link MapColor}.
 */
final class BiomeTint {

    private BiomeTint() {}

    /** Sentinel for "no colour" (transparent), never a real block colour. */
    static final int NONE = 0;

    // Water darkens toward this fraction as it deepens, reached at DEPTH_SCALE blocks.
    private static final int DEPTH_SCALE = 24;
    private static final double MAX_DARKEN = 0.55;

    /**
     * @param waterDepth blocks of water above the floor at this column, or 0 when
     *                   the surface block is not water
     */
    static int baseColor(BlockState state, Biome biome, int worldX, int worldZ, int waterDepth) {
        // The base getMapColor ignores the getter and position, so a zero pos is fine.
        MapColor mapColor = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (mapColor == MapColor.NONE)  return NONE;
        if (mapColor == MapColor.WATER) return waterColor(biome, waterDepth);

        int base = BlockColorPalette.rgbOf(state.getBlock());
        if (base == BlockColorPalette.NO_ENTRY) base = mapColor.col;

        if (mapColor == MapColor.GRASS) return multiply(base, biome.getGrassColor(worldX, worldZ));
        if (mapColor == MapColor.PLANT) return multiply(base, biome.getFoliageColor());
        return base;
    }

    // Biome water colour, darkened toward MAX_DARKEN as depth approaches DEPTH_SCALE.
    private static int waterColor(Biome biome, int depth) {
        double t = Math.min(1.0, (double) depth / DEPTH_SCALE);
        return scale(biome.getWaterColor(), 1.0 - MAX_DARKEN * t);
    }

    // Per-channel multiply (out of 255), how MC tints greyscale grass/foliage textures.
    private static int multiply(int base, int tint) {
        int r = ((base >> 16 & 0xFF) * (tint >> 16 & 0xFF)) / 255;
        int g = ((base >> 8  & 0xFF) * (tint >> 8  & 0xFF)) / 255;
        int b = ((base       & 0xFF) * (tint       & 0xFF)) / 255;
        return r << 16 | g << 8 | b;
    }

    private static int scale(int rgb, double factor) {
        int r = (int) ((rgb >> 16 & 0xFF) * factor);
        int g = (int) ((rgb >> 8  & 0xFF) * factor);
        int b = (int) ((rgb       & 0xFF) * factor);
        return r << 16 | g << 8 | b;
    }
}
