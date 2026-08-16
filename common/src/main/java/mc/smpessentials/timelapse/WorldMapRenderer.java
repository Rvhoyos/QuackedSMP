package mc.smpessentials.timelapse;

import mc.smpessentials.config.SmpConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Renders a dimension into a top-down image sized dynamically to the extent of
 * its generated chunks, so a snapshot always frames exactly what exists. Reads
 * region files directly and only reads: callers must flush and pause world
 * autosave first so the files on disk are current and stable.
 */
public final class WorldMapRenderer {

    /** Renders the full generated extent, or returns null if nothing is generated. */
    public BufferedImage render(MinecraftServer server, ResourceKey<Level> dimension) throws Exception {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) throw new IllegalArgumentException("Dimension not loaded: " + dimension.identifier());

        Path regionDir = DimensionType
                .getStorageFolder(dimension, server.getWorldPath(LevelResource.ROOT))
                .resolve("region");

        Optional<ChunkBounds> bounds = new RegionExtentScanner(regionDir).scan();
        if (bounds.isEmpty()) return null;
        ChunkBounds extent = bounds.get();

        ColorMaps.ensureLoaded();

        MapCanvas canvas = MapCanvas.covering(extent, SmpConfig.TIMELAPSE_MAX_DIMENSION);
        paint(server, level, dimension, regionDir, extent, canvas);
        return canvas.toImage();
    }

    private void paint(MinecraftServer server, ServerLevel level, ResourceKey<Level> dimension,
                       Path regionDir, ChunkBounds bounds, MapCanvas canvas) throws Exception {
        String levelName = server.getWorldPath(LevelResource.ROOT).getFileName().toString();
        ChunkColumnSampler sampler = new ChunkColumnSampler(server.registryAccess(), level);

        try (RegionChunkReader reader = RegionChunkReader.open(dimension, regionDir, levelName)) {
            for (int cx = bounds.minChunkX(); cx <= bounds.maxChunkX(); cx++) {
                for (int cz = bounds.minChunkZ(); cz <= bounds.maxChunkZ(); cz++) {
                    Optional<CompoundTag> tag = reader.read(new ChunkPos(cx, cz));
                    if (tag.isPresent()) {
                        paintChunk(sampler.sample(tag.get()), cx, cz, canvas);
                    }
                }
            }
        }
    }

    private void paintChunk(ChunkColumnSampler.Column[][] grid, int chunkX, int chunkZ, MapCanvas canvas) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                ChunkColumnSampler.Column col = grid[lx][lz];
                if (col != null) {
                    canvas.plot((chunkX << 4) + lx, (chunkZ << 4) + lz, col.rgb(), col.y());
                }
            }
        }
    }
}
