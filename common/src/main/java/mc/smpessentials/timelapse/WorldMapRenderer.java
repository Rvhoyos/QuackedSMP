package mc.smpessentials.timelapse;

import mc.smpessentials.SmpUtilsMod;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Renders a dimension into a top-down image sized to the chunks it frames, so a
 * snapshot always fills its pixels with real terrain. {@link ChunkClusterFinder}
 * chooses what is framed and {@link RegionPresenceScanner} says which chunks
 * exist. Reads region files directly and only reads: callers must flush and
 * pause world autosave first so the files on disk are current and stable.
 */
public final class WorldMapRenderer {

    /** A finished render plus the blocks-per-pixel it settled on (1 = full 1:1). */
    public record RenderResult(BufferedImage image, int blocksPerPixel) {}

    /**
     * Renders the main body of generated chunks at 1 block = 1 pixel, downsampling
     * only as far as {@code budgetBytes} of heap requires. Returns null if nothing
     * is generated.
     */
    public RenderResult render(MinecraftServer server, ResourceKey<Level> dimension, long budgetBytes,
                               CaptureProgress progress) throws Exception {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) throw new IllegalArgumentException("Dimension not loaded: " + dimension.identifier());

        Path regionDir = DimensionType
                .getStorageFolder(dimension, server.getWorldPath(LevelResource.ROOT))
                .resolve("region");

        progress.phase("scanning");
        ChunkClusterFinder.Cluster cluster = ChunkClusterFinder.largest(new RegionPresenceScanner(regionDir).scan());
        if (cluster == null) return null;
        if (cluster.excludedChunks() > 0) {
            SmpUtilsMod.LOGGER.info("[Timelapse] {}: framing {} chunks, leaving out {} that sit far from them",
                    dimension.identifier(), cluster.chunkCount(), cluster.excludedChunks());
        }

        ColorMaps.ensureLoaded();
        BlockColorPalette.ensureLoaded();

        MapCanvas canvas = MapCanvas.covering(cluster.bounds(), budgetBytes);
        progress.begin(cluster.bounds(), cluster.chunkCount());
        progress.phase("painting");
        paint(server, level, dimension, regionDir, cluster, canvas, progress);
        progress.phase("shading");
        return new RenderResult(canvas.toImage(), canvas.blocksPerPixel());
    }

    // Visits only chunks the region headers report as stored: reading a chunk that
    // was never generated would make vanilla create an empty region file for it.
    private void paint(MinecraftServer server, ServerLevel level, ResourceKey<Level> dimension,
                       Path regionDir, ChunkClusterFinder.Cluster cluster, MapCanvas canvas,
                       CaptureProgress progress) throws Exception {
        // getWorldPath(ROOT) ends in "." so it must be normalised before the folder name is readable.
        String levelName = server.getWorldPath(LevelResource.ROOT)
                .toAbsolutePath().normalize().getFileName().toString();
        ChunkColumnSampler sampler = new ChunkColumnSampler(
                server.registryAccess(), level, level.dimensionType().hasCeiling());

        // West to east, so the dashboard's live map fills in as a left-to-right sweep.
        List<RegionPresence> regions = new ArrayList<>(cluster.regions());
        regions.sort(Comparator.comparingInt(RegionPresence::regionX).thenComparingInt(RegionPresence::regionZ));

        try (RegionChunkReader reader = RegionChunkReader.open(dimension, regionDir, levelName)) {
            for (RegionPresence region : regions) {
                for (int lx = 0; lx < RegionPresence.SIDE; lx++) {
                    for (int lz = 0; lz < RegionPresence.SIDE; lz++) {
                        if (!region.present(lx, lz)) continue;
                        int chunkX = region.chunkX(lx), chunkZ = region.chunkZ(lz);
                        Optional<CompoundTag> tag = reader.read(new ChunkPos(chunkX, chunkZ));
                        progress.markChunk(chunkX, chunkZ);
                        if (tag.isPresent()) {
                            paintChunk(sampler.sample(tag.get()), chunkX, chunkZ, canvas);
                        }
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
