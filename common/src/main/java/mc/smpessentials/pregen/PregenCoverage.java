package mc.smpessentials.pregen;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.timelapse.RegionPresence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * How many of an area's chunks already exist on disk, counted from region file headers.
 *
 * This is what makes the panel honest on a world that was played in before pre-generation was ever
 * switched on. Our own cursor only knows about runs we performed, so on an existing world it reads
 * zero and would price an area that is mostly already generated as though it were empty.
 *
 * Only the region files the area actually touches are opened, and only their first 4 KB, so the
 * cost scales with the area rather than with how many files the dimension happens to hold.
 *
 * A location table entry means a chunk is stored, not that it reached FULL status, so a world
 * killed part way through generating counts those partial chunks as present.
 */
final class PregenCoverage {

    private PregenCoverage() {}

    private static final int HEADER_BYTES = 4096;

    /**
     * Most region files one count will probe. A region holds 1024 chunks, so this still covers an
     * area of four million chunks, far past anything a run should be asked to generate. Past it the
     * count is refused rather than attempted: the span grows with the square of the radius, and at
     * the largest distance the field accepts it would be ten billion probes on the request thread.
     */
    private static final long MAX_REGIONS = 4096L;

    /** Chunks of {@code area} already on disk, or -1 when the count could not be established. */
    static long countExisting(ServerLevel level, PregenArea area) {
        Path root = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path regionDir = DimensionType.getStorageFolder(level.dimension(), root).resolve("region");
        if (!Files.isDirectory(regionDir)) return 0L;

        int half = area.sideChunks() / 2;
        int minChunkX = area.centerChunkX() - half;
        int minChunkZ = area.centerChunkZ() - half;
        int maxChunkX = minChunkX + area.sideChunks() - 1;
        int maxChunkZ = minChunkZ + area.sideChunks() - 1;

        long regionsX = (maxChunkX >> 5) - (minChunkX >> 5) + 1L;
        long regionsZ = (maxChunkZ >> 5) - (minChunkZ >> 5) + 1L;
        if (regionsX * regionsZ > MAX_REGIONS) return -1L;

        long found = 0L;
        try {
            for (int rx = minChunkX >> 5; rx <= (maxChunkX >> 5); rx++) {
                for (int rz = minChunkZ >> 5; rz <= (maxChunkZ >> 5); rz++) {
                    byte[] header = readHeader(regionDir.resolve("r." + rx + "." + rz + ".mca"));
                    if (header == null) continue;
                    RegionPresence region = RegionPresence.fromHeader(rx, rz, header);

                    // Clip the region to the area, so chunks outside it are not counted.
                    int fromX = Math.max(minChunkX, rx * RegionPresence.SIDE);
                    int toX = Math.min(maxChunkX, rx * RegionPresence.SIDE + RegionPresence.SIDE - 1);
                    int fromZ = Math.max(minChunkZ, rz * RegionPresence.SIDE);
                    int toZ = Math.min(maxChunkZ, rz * RegionPresence.SIDE + RegionPresence.SIDE - 1);
                    for (int cx = fromX; cx <= toX; cx++) {
                        for (int cz = fromZ; cz <= toZ; cz++) {
                            if (region.present(cx - rx * RegionPresence.SIDE, cz - rz * RegionPresence.SIDE)) {
                                found++;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            SmpUtilsMod.LOGGER.warn("[Pregen] Could not read chunk coverage for {}: {}",
                    level.dimension().identifier(), e.getMessage());
            return -1L;
        }
        return found;
    }

    private static byte[] readHeader(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) < HEADER_BYTES) return null;
            byte[] buf = new byte[HEADER_BYTES];
            try (InputStream in = Files.newInputStream(file)) {
                return in.readNBytes(buf, 0, HEADER_BYTES) == HEADER_BYTES ? buf : null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
