package mc.smpessentials.timelapse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
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
    // Roofed dimensions (the Nether) are solid bedrock at the top, so a plain
    // top-down scan renders the ceiling. When set, the scan first descends past
    // the roof into open air before it starts looking for the surface.
    private final boolean hasCeiling;

    ChunkColumnSampler(RegistryAccess registryAccess, LevelHeightAccessor heightAccessor, boolean hasCeiling) {
        this.factory = PalettedContainerFactory.create(registryAccess);
        this.heightAccessor = heightAccessor;
        this.hasCeiling = hasCeiling;
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
    // On roofed dimensions the scan first passes the ceiling: skip the leading
    // air above the roof (roofState 0), skip the solid roof (roofState 1), and
    // only start the surface search once open air below the roof is reached
    // (roofState 2), landing on the cavern floor rather than the bedrock roof.
    // A column of terrain fused floor-to-ceiling never opens into a cavern; for
    // it the first non-bedrock block below the roof is kept as a fallback, so it
    // renders as that terrain instead of a transparent hole. The cavern floor is
    // always preferred; the fallback is used only when no floor is found.
    private Column sampleColumn(List<SerializableChunkData.SectionData> sections, ChunkPos pos, int lx, int lz) {
        BlockState surface = null;
        int surfaceY = 0;
        Biome biome = null;
        int waterDepth = 0;
        BlockState roofFloor = null;   // fallback: first terrain block below the roof
        int roofFloorY = 0;
        Biome roofFloorBiome = null;
        int roofState = hasCeiling ? 0 : 2;

        for (SerializableChunkData.SectionData sd : sections) {
            LevelChunkSection section = sd.chunkSection();
            // Vanilla leaves chunkSection null for light-only slices outside the
            // dimension height (SerializableChunkData.parse). Treat as air.
            if (section == null || section.hasOnlyAir()) {
                if (surface != null) break;   // air below the surface = floor reached
                if (roofState == 1) roofState = 2;  // open air below the roof
                continue;                      // still above the surface
            }
            int baseY = sd.y() * 16;
            for (int ly = 15; ly >= 0; ly--) {
                BlockState state = section.getBlockState(lx, ly, lz);
                if (roofState == 0) {          // above the roof, skip air until the roof
                    if (!state.isAir()) roofState = 1;
                    continue;
                }
                if (roofState == 1) {          // inside the roof, skip solid until air
                    if (state.isAir()) { roofState = 2; continue; }
                    if (roofFloor == null && !state.is(Blocks.BEDROCK)) {
                        roofFloor = state;
                        roofFloorY = baseY + ly;
                        roofFloorBiome = section.getNoiseBiome(lx >> 2, ly >> 2, lz >> 2).value();
                    }
                    continue;
                }
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
        if (surface != null) return toColumn(surface, biome, pos, lx, lz, surfaceY, waterDepth);
        if (roofFloor != null) return toColumn(roofFloor, roofFloorBiome, pos, lx, lz, roofFloorY, 0);
        return null;
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
