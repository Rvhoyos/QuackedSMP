package mc.smpessentials.timelapse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns a raw chunk NBT tag into the biome-tinted surface colour of every column.
 * Uses vanilla's own {@link SerializableChunkData} deserializer (no hand-rolled
 * NBT parsing) and finds the surface by scanning sections top-down for the first
 * non-air block, which matches WORLD_SURFACE closely enough for a map without
 * decoding the packed heightmap. Water columns are walked down to the floor so
 * the colour can be darkened by depth.
 */
final class ChunkColumnSampler {

    /** One surface column: its packed {@code 0xRRGGBB} base colour and world Y (for shading). */
    record Column(int rgb, int y) {}

    private final PalettedContainerFactory factory;
    private final LevelHeightAccessor heightAccessor;

    ChunkColumnSampler(RegistryAccess registryAccess, LevelHeightAccessor heightAccessor) {
        this.factory = PalettedContainerFactory.create(registryAccess);
        this.heightAccessor = heightAccessor;
    }

    /**
     * @return a 16x16 grid indexed {@code [localX][localZ]}; entries are null for
     *         columns that are entirely air (e.g. ungenerated void).
     */
    Column[][] sample(CompoundTag chunkTag) {
        SerializableChunkData data = SerializableChunkData.parse(heightAccessor, factory, chunkTag);
        ChunkPos pos = data.chunkPos();

        List<SerializableChunkData.SectionData> sections = new ArrayList<>(data.sectionData());
        sections.sort(Comparator.comparingInt(SerializableChunkData.SectionData::y).reversed());

        Column[][] out = new Column[16][16];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                out[lx][lz] = sampleColumn(sections, pos, lx, lz);
            }
        }
        return out;
    }

    // Walks one column top-down: first non-air block is the surface; if that
    // block is water, keeps counting water down to the floor for depth shading.
    private Column sampleColumn(List<SerializableChunkData.SectionData> sections, ChunkPos pos, int lx, int lz) {
        BlockState surface = null;
        int surfaceY = 0;
        Biome biome = null;
        int waterDepth = 0;

        for (SerializableChunkData.SectionData sd : sections) {
            LevelChunkSection section = sd.chunkSection();
            if (section.hasOnlyAir()) {
                if (surface != null) break;   // air below the surface = floor reached
                continue;                      // still above the surface
            }
            int baseY = sd.y() * 16;
            for (int ly = 15; ly >= 0; ly--) {
                BlockState state = section.getBlockState(lx, ly, lz);
                if (surface == null) {
                    if (state.isAir()) continue;
                    surface = state;
                    surfaceY = baseY + ly;
                    biome = section.getNoiseBiome(lx >> 2, ly >> 2, lz >> 2).value();
                    if (!isWater(state)) return toColumn(surface, biome, pos, lx, lz, surfaceY, 0);
                    waterDepth = 1;
                } else if (isWater(state)) {
                    waterDepth++;
                } else {
                    return toColumn(surface, biome, pos, lx, lz, surfaceY, waterDepth);
                }
            }
        }
        return surface == null ? null : toColumn(surface, biome, pos, lx, lz, surfaceY, waterDepth);
    }

    private Column toColumn(BlockState state, Biome biome, ChunkPos pos, int lx, int lz,
                            int surfaceY, int waterDepth) {
        int worldX = pos.getMinBlockX() + lx;
        int worldZ = pos.getMinBlockZ() + lz;
        return new Column(BiomeTint.baseColor(state, biome, worldX, worldZ, waterDepth), surfaceY);
    }

    private static boolean isWater(BlockState state) {
        return state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == MapColor.WATER;
    }
}
