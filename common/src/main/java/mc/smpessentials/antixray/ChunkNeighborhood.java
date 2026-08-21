package mc.smpessentials.antixray;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Read-only block access over one chunk plus its four horizontal neighbours.
 *
 * Lookups go straight into the {@link LevelChunkSection} arrays, so they cost an array index
 * rather than a chunk-source lookup. That matters because {@link #isEnclosed} runs six lookups
 * for every candidate block in a chunk, and a chunk holds up to 98,304 blocks.
 *
 * Not thread safe: an instance carries a scratch position and is meant to be built, used and
 * dropped on one thread. {@link #around} only resolves neighbours on the server thread; off it,
 * neighbouring chunks read as absent.
 */
final class ChunkNeighborhood {
    private static final Direction[] FACES = Direction.values();

    private final Level level;
    private final int centerChunkX;
    private final int centerChunkZ;
    private final int minSectionY;
    private final LevelChunkSection[] center;
    private final LevelChunkSection[] negX;
    private final LevelChunkSection[] posX;
    private final LevelChunkSection[] negZ;
    private final LevelChunkSection[] posZ;
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();

    private ChunkNeighborhood(LevelChunk chunk, LevelChunkSection[] negX, LevelChunkSection[] posX,
                              LevelChunkSection[] negZ, LevelChunkSection[] posZ) {
        this.level = chunk.getLevel();
        this.centerChunkX = chunk.getPos().x();
        this.centerChunkZ = chunk.getPos().z();
        this.minSectionY = chunk.getMinSectionY();
        this.center = chunk.getSections();
        this.negX = negX;
        this.posX = posX;
        this.negZ = negZ;
        this.posZ = posZ;
    }

    static ChunkNeighborhood around(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        ChunkSource source = chunk.getLevel().getChunkSource();
        return new ChunkNeighborhood(chunk,
                sectionsOf(source.getChunkNow(pos.x() - 1, pos.z())),
                sectionsOf(source.getChunkNow(pos.x() + 1, pos.z())),
                sectionsOf(source.getChunkNow(pos.x(), pos.z() - 1)),
                sectionsOf(source.getChunkNow(pos.x(), pos.z() + 1)));
    }

    private static LevelChunkSection[] sectionsOf(LevelChunk chunk) {
        return chunk == null ? null : chunk.getSections();
    }

    /**
     * Block state at a world position, or null when the position falls outside this
     * neighbourhood: a chunk that is not loaded, or a Y outside the level's build range. Callers
     * treat null as "cannot be judged", which is what the old level-loaded guard did.
     */
    BlockState stateAt(int x, int y, int z) {
        LevelChunkSection[] sections = sectionsAt(x, z);
        if (sections == null) return null;

        int index = (y >> 4) - this.minSectionY;
        if (index < 0 || index >= sections.length) return null;

        return sections[index].getBlockState(x & 15, y & 15, z & 15);
    }

    /**
     * True when all six neighbours turn a sturdy face towards this block, so no client can see
     * it. Neighbours outside the neighbourhood count as not covering, which keeps the outer shell
     * of a chunk unobfuscated rather than guessing at data we do not hold.
     */
    boolean isEnclosed(int x, int y, int z) {
        for (Direction face : FACES) {
            int nx = x + face.getStepX();
            int ny = y + face.getStepY();
            int nz = z + face.getStepZ();

            BlockState neighbor = stateAt(nx, ny, nz);
            if (neighbor == null || neighbor.isAir()) return false;

            // isFaceSturdy tests the real block shape, so fences, carpets, slabs and stairs
            // correctly count as not covering. For any block without a dynamic shape it reads a
            // state-local cache and ignores the level and position, which is why reusing one
            // scratch position here is safe.
            this.scratch.set(nx, ny, nz);
            if (!neighbor.isFaceSturdy(this.level, this.scratch, face.getOpposite())) return false;
        }
        return true;
    }

    private LevelChunkSection[] sectionsAt(int x, int z) {
        int dx = (x >> 4) - this.centerChunkX;
        int dz = (z >> 4) - this.centerChunkZ;
        if (dz == 0) {
            if (dx == 0) return this.center;
            if (dx == -1) return this.negX;
            if (dx == 1) return this.posX;
        } else if (dx == 0) {
            if (dz == -1) return this.negZ;
            if (dz == 1) return this.posZ;
        }
        return null;
    }
}
