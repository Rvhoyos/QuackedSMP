package mc.smpessentials.timelapse;

import mc.smpessentials.SmpUtilsMod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads which chunks a dimension has generated. Touches only each region file's
 * 4 KB location-table header, so it never decompresses chunk data, and keeps
 * only the region files that hold at least one chunk.
 */
final class RegionPresenceScanner {

    private static final Pattern REGION_RX = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$");
    private static final int HEADER_BYTES  = 4096;

    private final Path regionDir;

    RegionPresenceScanner(Path regionDir) {
        this.regionDir = regionDir;
    }

    /** Returns one entry per populated region file, empty if nothing is generated. */
    List<RegionPresence> scan() throws IOException {
        List<RegionPresence> found = new ArrayList<>();
        if (!Files.isDirectory(regionDir)) return found;

        try (DirectoryStream<Path> files = Files.newDirectoryStream(regionDir, "*.mca")) {
            for (Path file : files) {
                Matcher m = REGION_RX.matcher(file.getFileName().toString());
                if (!m.matches()) continue;
                byte[] header = readHeader(file);
                if (header == null) continue;
                RegionPresence region = RegionPresence.fromHeader(
                        Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), header);
                if (region.chunkCount() > 0) found.add(region);
            }
        }
        return found;
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
}
