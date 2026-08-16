package mc.smpessentials.timelapse;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Resolves the base (unshaded) map color of a surface block as a packed
 * {@code 0xRRGGBB} value. Blocks whose color is biome-dependent in vanilla
 * (grass, foliage, water) are tinted with the biome's own colors so different
 * biomes read distinctly; everything else keeps its flat {@link MapColor}.
 */
final class BiomeTint {

    private BiomeTint() {}

    /** Sentinel for "no color" (transparent), never a real block color. */
    static final int NONE = 0;

    static int baseColor(BlockState state, Biome biome, int worldX, int worldZ) {
        // The base getMapColor ignores the getter and position, so a zero pos is fine.
        MapColor mapColor = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (mapColor == MapColor.NONE)  return NONE;
        if (mapColor == MapColor.WATER) return biome.getWaterColor();
        if (mapColor == MapColor.GRASS) return biome.getGrassColor(worldX, worldZ);
        if (mapColor == MapColor.PLANT) return biome.getFoliageColor();
        return mapColor.col;
    }
}
