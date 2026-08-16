package mc.smpessentials.timelapse;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Opens a dimension's {@code region/} folder read-only and yields raw chunk NBT.
 * There is no public API to construct a standalone region handle: both the
 * {@link RegionFileStorage} and IOWorker constructors are package-private, and
 * MC 26.1.2 has no public ChunkStorage class. We reflect on the constructor.
 * The {@code .class} literals are remapped by the loader at build time, so this
 * stays correct on both Fabric and NeoForge.
 */
public final class RegionChunkReader implements AutoCloseable {

    private final RegionFileStorage storage;

    private RegionChunkReader(RegionFileStorage storage) {
        this.storage = storage;
    }

    /**
     * @param dimension the level key (used only for storage bookkeeping/logging)
     * @param regionDir the {@code .../region} directory to read
     * @param levelName the world save folder name (bookkeeping only)
     */
    public static RegionChunkReader open(ResourceKey<Level> dimension, Path regionDir, String levelName) {
        try {
            RegionStorageInfo info = new RegionStorageInfo(levelName, dimension, "chunk");
            Constructor<RegionFileStorage> ctor =
                    RegionFileStorage.class.getDeclaredConstructor(RegionStorageInfo.class, Path.class, boolean.class);
            ctor.setAccessible(true);
            // third arg is "sync" write flag; irrelevant for our read-only use
            return new RegionChunkReader(ctor.newInstance(info, regionDir, false));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not open region storage at " + regionDir, e);
        }
    }

    /** Reads a chunk's serialized NBT, or empty if that chunk was never generated. */
    public Optional<CompoundTag> read(ChunkPos pos) throws IOException {
        return Optional.ofNullable(storage.read(pos));
    }

    @Override
    public void close() throws IOException {
        storage.close();
    }
}
