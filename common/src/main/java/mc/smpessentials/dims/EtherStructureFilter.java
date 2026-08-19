package mc.smpessentials.dims;

import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Keeps structures out of open sky in ether dimensions.
 *
 * Vanilla structures place themselves without ever asking whether there is ground. Mineshafts are
 * the clearest case: MineshaftStructure.generatePiecesAndAdjust calls moveBelowSeaLevel, so they sit
 * at a fixed offset from sea level no matter what is or is not there. In a dimension that is mostly
 * void that leaves them hanging in mid air.
 *
 * Once the vanilla pass has run, any start whose own bounding box holds no terrain is discarded. A
 * start that does sit in terrain is kept whole, so its corridors are still free to poke out into the
 * void.
 */
public final class EtherStructureFilter {

    private EtherStructureFilter() {}

    // Fractions across a start's bounding box, sampled to decide whether it has terrain to sit in.
    private static final float[][] SAMPLE_POINTS =
            {{0.5f, 0.5f}, {0.15f, 0.15f}, {0.85f, 0.15f}, {0.15f, 0.85f}, {0.85f, 0.85f}};

    public static void discardFloatingStarts(ChunkGenerator generator, ChunkGeneratorStructureState state,
                                             StructureManager structureManager, ChunkAccess centerChunk) {
        SectionPos sectionPos = SectionPos.bottomOf(centerChunk);
        RandomState randomState = state.randomState();
        for (Holder<StructureSet> setHolder : state.possibleStructureSets()) {
            for (StructureSet.StructureSelectionEntry entry : setHolder.value().structures()) {
                Structure structure = entry.structure().value();
                StructureStart start = structureManager.getStartForStructure(sectionPos, structure, centerChunk);
                if (start == null || !start.isValid()) continue;
                if (hasTerrain(generator, start.getBoundingBox(), centerChunk, randomState)) continue;
                structureManager.setStartForStructure(sectionPos, structure, StructureStart.INVALID_START, centerChunk);
            }
        }
    }

    // True if any sampled column holds a block inside the box's own Y range. Terrain only:
    // getBaseColumn is the raw noise column, with no features or other structures in it.
    private static boolean hasTerrain(ChunkGenerator generator, BoundingBox box,
                                      ChunkAccess heightAccessor, RandomState randomState) {
        for (float[] point : SAMPLE_POINTS) {
            int x = box.minX() + Math.round((box.maxX() - box.minX()) * point[0]);
            int z = box.minZ() + Math.round((box.maxZ() - box.minZ()) * point[1]);
            NoiseColumn column = generator.getBaseColumn(x, z, heightAccessor, randomState);
            for (int y = box.minY(); y <= box.maxY(); y++) {
                if (!column.getBlock(y).isAir()) return true;
            }
        }
        return false;
    }
}
