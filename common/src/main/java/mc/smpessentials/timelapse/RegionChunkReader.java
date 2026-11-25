package mc.smpessentials.timelapse;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Opens a dimension's {@code region/} folder read-only and yields chunk NBT upgraded to the running
 * game version, the way ChunkMap.readChunk does before parsing. Without the upgrade a chunk saved by
 * an older version fails to decode, and every block in it reads as air. The upgrade happens in memory
 * only: nothing is ever written back.
 */
public final class RegionChunkReader implements AutoCloseable {

    private final SimpleRegionStorage storage;
    private final CompoundTag fixContext;

    private RegionChunkReader(SimpleRegionStorage storage, CompoundTag fixContext) {
        this.storage = storage;
        this.fixContext = fixContext;
    }

    /**
     * @param level     the dimension, for the data fix context
     * @param regionDir the {@code .../region} directory to read
     * @param levelName the world save folder name (bookkeeping only)
     */
    public static RegionChunkReader open(ServerLevel level, Path regionDir, String levelName) {
        RegionStorageInfo info = new RegionStorageInfo(levelName, level.dimension(), "chunk");
        SimpleRegionStorage storage = new SimpleRegionStorage(
                info, regionDir, level.getServer().getFixerUpper(), false, DataFixTypes.CHUNK);
        CompoundTag fixContext = ChunkMap.getChunkDataFixContextTag(
                level.dimension(), level.getChunkSource().getGenerator().getTypeNameForDataFixer());
        return new RegionChunkReader(storage, fixContext);
    }

    /** Reads a chunk's NBT at the current data version, or empty if that chunk was never generated. */
    public Optional<CompoundTag> read(ChunkPos pos) {
        int currentVersion = SharedConstants.getCurrentVersion().dataVersion().version();
        return storage.read(pos).join()
                .map(tag -> storage.upgradeChunkTag(tag, -1, fixContext, currentVersion));
    }

    @Override
    public void close() throws IOException {
        storage.close();
    }
}
