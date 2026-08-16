package mc.smpessentials.timelapse;

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

    /** A finished render plus the blocks-per-pixel it settled on (1 = full 1:1). */
    public record RenderResult(BufferedImage image, int blocksPerPixel) {}

    /**
     * Renders the full generated extent at 1 block = 1 pixel, downsampling only
     * as far as {@code budgetBytes} of heap requires. Returns null if nothing is
     * generated.
     */
    public RenderResult render(MinecraftServer server, ResourceKey<Level> dimension, long budgetBytes,
                               CaptureProgress progress) throws Exception {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) throw new IllegalArgumentException("Dimension not loaded: " + dimension.identifier());

        Path regionDir = DimensionType
                .getStorageFolder(dimension, server.getWorldPath(LevelResource.ROOT))
                .resolve("region");

        progress.phase("scanning");
        Optional<ChunkBounds> bounds = new RegionExtentScanner(regionDir).scan();
        if (bounds.isEmpty()) return null;
        ChunkBounds extent = bounds.get();

        ColorMaps.ensureLoaded();
        BlockColorPalette.ensureLoaded();

        MapCanvas canvas = MapCanvas.covering(extent, budgetBytes);
        progress.begin(extent);
        progress.phase("painting");
        paint(server, level, dimension, regionDir, extent, canvas, progress);
        progress.phase("shading");
        return new RenderResult(canvas.toImage(), canvas.blocksPerPixel());
    }

    private void paint(MinecraftServer server, ServerLevel level, ResourceKey<Level> dimension,
                       Path regionDir, ChunkBounds bounds, MapCanvas canvas, CaptureProgress progress) throws Exception {
        String levelName = server.getWorldPath(LevelResource.ROOT).getFileName().toString();
        ChunkColumnSampler sampler = new ChunkColumnSampler(server.registryAccess(), level);

        try (RegionChunkReader reader = RegionChunkReader.open(dimension, regionDir, levelName)) {
            for (int cx = bounds.minChunkX(); cx <= bounds.maxChunkX(); cx++) {
                for (int cz = bounds.minChunkZ(); cz <= bounds.maxChunkZ(); cz++) {
                    Optional<CompoundTag> tag = reader.read(new ChunkPos(cx, cz));
                    progress.markChunk(cx, cz, tag.isPresent());
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
