package mc.smpessentials.timelapse;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns a raw chunk NBT tag into the biome-tinted surface color of every column.
 * Uses vanilla's own {@link SerializableChunkData} deserializer (no hand-rolled
 * NBT parsing) and finds the surface by scanning sections top-down for the first
 * non-air block, which matches WORLD_SURFACE closely enough for a map without
 * decoding the packed heightmap.
 */
final class ChunkColumnSampler {

    /** One surface column: its packed {@code 0xRRGGBB} base color and world Y (for shading). */
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
        int remaining = 16 * 16;

        for (SerializableChunkData.SectionData sd : sections) {
            if (remaining == 0) break;
            LevelChunkSection section = sd.chunkSection();
            if (section.hasOnlyAir()) continue;

            int baseY = sd.y() * 16;
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    if (out[lx][lz] != null) continue;
                    for (int ly = 15; ly >= 0; ly--) {
                        BlockState state = section.getBlockState(lx, ly, lz);
                        if (state.isAir()) continue;
                        out[lx][lz] = toColumn(section, state, pos, lx, ly, lz, baseY + ly);
                        remaining--;
                        break;
                    }
                }
            }
        }
        return out;
    }

    private Column toColumn(LevelChunkSection section, BlockState state, ChunkPos pos,
                            int lx, int ly, int lz, int worldY) {
        int worldX = pos.getMinBlockX() + lx;
        int worldZ = pos.getMinBlockZ() + lz;
        // Biomes are stored at 4x4x4 (quart) resolution within a section.
        Biome biome = section.getNoiseBiome(lx >> 2, ly >> 2, lz >> 2).value();
        return new Column(BiomeTint.baseColor(state, biome, worldX, worldZ), worldY);
    }
}
