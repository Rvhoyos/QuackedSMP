package mc.smpessentials.timelapse;

import mc.smpessentials.SmpUtilsMod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the bounding box of all generated chunks in a dimension's {@code region/}
 * folder. Reads only each region file's 4 KB location-table header, so it never
 * decompresses chunk data.
 */
final class RegionExtentScanner {

    private static final Pattern REGION_RX = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$");
    private static final int HEADER_BYTES  = 4096;
    private static final int CHUNKS_PER_REGION_SIDE = 32;

    private final Path regionDir;

    RegionExtentScanner(Path regionDir) {
        this.regionDir = regionDir;
    }

    /** Returns the populated-chunk bounds, or empty if the dimension has no generated chunks. */
    Optional<ChunkBounds> scan() throws IOException {
        if (!Files.isDirectory(regionDir)) return Optional.empty();

        Accumulator acc = new Accumulator();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(regionDir, "*.mca")) {
            for (Path file : files) {
                Matcher m = REGION_RX.matcher(file.getFileName().toString());
                if (m.matches()) {
                    scanRegion(file, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), acc);
                }
            }
        }
        return acc.result();
    }

    private void scanRegion(Path file, int regionX, int regionZ, Accumulator acc) {
        byte[] header = readHeader(file);
        if (header == null) return;
        for (int i = 0; i < CHUNKS_PER_REGION_SIDE * CHUNKS_PER_REGION_SIDE; i++) {
            if (isPresent(header, i)) {
                acc.add(regionX * CHUNKS_PER_REGION_SIDE + (i % CHUNKS_PER_REGION_SIDE),
                        regionZ * CHUNKS_PER_REGION_SIDE + (i / CHUNKS_PER_REGION_SIDE));
            }
        }
    }

    // A chunk is stored when its 4-byte location-table entry is nonzero.
    private static boolean isPresent(byte[] header, int index) {
        int off = index * 4;
        return (header[off] | header[off + 1] | header[off + 2] | header[off + 3]) != 0;
    }

    private byte[] readHeader(Path file) {
        try {
            if (Files.size(file) < HEADER_BYTES) return null;
            byte[] buf = new byte[HEADER_BYTES];
            try (InputStream in = Files.newInputStream(file)) {
                return in.readNBytes(buf, 0, HEADER_BYTES) == HEADER_BYTES ? buf : null;
            }
        } catch (IOException e) {
            SmpUtilsMod.LOGGER.warn("[Timelapse] Skipping unreadable region {}: {}", file.getFileName(), e.getMessage());
            return null;
        }
    }

    /** Mutable min/max tracker that yields a {@link ChunkBounds} once populated. */
    private static final class Accumulator {
        private int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        private boolean any = false;

        void add(int chunkX, int chunkZ) {
            any = true;
            minX = Math.min(minX, chunkX);
            minZ = Math.min(minZ, chunkZ);
            maxX = Math.max(maxX, chunkX);
            maxZ = Math.max(maxZ, chunkZ);
        }

        Optional<ChunkBounds> result() {
            return any ? Optional.of(new ChunkBounds(minX, minZ, maxX, maxZ)) : Optional.empty();
        }
    }
}
