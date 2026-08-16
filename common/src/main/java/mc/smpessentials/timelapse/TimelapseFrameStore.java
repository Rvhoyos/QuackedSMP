package mc.smpessentials.timelapse;

import mc.smpessentials.config.SmpConfig;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * On-disk store of timelapse frames. Each frame is a PNG named
 * {@code frame-<epochMillis>-b<blocksPerPixel>.png}; the timestamp and the
 * render's blocks-per-pixel live in the filename so no separate manifest needs
 * to be kept in sync. Legacy {@code frame-<epochMillis>.png} names (no bpp) are
 * still read and treated as full resolution. Retention thins evenly across the
 * whole timeline (dropping frames spread through time) so a capped timelapse
 * loses smoothness rather than its beginning or end.
 */
public final class TimelapseFrameStore {

    private static final Pattern NAME_RX = Pattern.compile("^frame-(\\d+)(?:-b(\\d+))?\\.png$");

    /**
     * A stored frame. {@code capturedAt} is epoch millis and {@code blocksPerPixel}
     * (1 = full 1:1) are parsed from the name.
     */
    public record Frame(String name, long capturedAt, long sizeBytes, int blocksPerPixel) {}

    private final Path dir;

    public TimelapseFrameStore(Path dir) {
        this.dir = dir;
    }

    /** Root timelapse directory holding one subfolder per captured dimension. */
    public static Path rootDir() {
        return Path.of(SmpConfig.TIMELAPSE_DIR).toAbsolutePath().normalize();
    }

    /** Store for one dimension's frames, under its own subfolder of {@link #rootDir()}. */
    public static TimelapseFrameStore forDimension(String dimId) {
        return new TimelapseFrameStore(rootDir().resolve(folderName(dimId)));
    }

    /**
     * Folder name for a dimension id: {@code namespace:path} becomes
     * {@code namespace_path}. Any remaining separator or unusual character is
     * replaced with {@code _}, so the id can never escape {@link #rootDir()}.
     */
    static String folderName(String dimId) {
        return dimId.replace(':', '_').replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    public Path dir() { return dir; }

    /** Frames newest-first. */
    public List<Frame> list() {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .map(p -> toFrame(p))
                    .filter(f -> f != null)
                    .sorted(Comparator.comparingLong(Frame::capturedAt).reversed())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Encodes and stores a frame captured at {@code capturedAt}, then applies retention. */
    public void add(BufferedImage image, long capturedAt, int blocksPerPixel) throws IOException {
        Files.createDirectories(dir);
        Path out = dir.resolve("frame-" + capturedAt + "-b" + blocksPerPixel + ".png");
        // PNG keeps the alpha channel, so ungenerated void stays transparent
        // rather than a black fill, and its lossless encoding avoids the
        // compression artifacts JPEG produces on flat-color, hard-edged maps.
        ImageIO.write(image, "png", out.toFile());
        prune();
    }

    public Path pathOf(String name) {
        if (!NAME_RX.matcher(name).matches()) throw new IllegalArgumentException("Invalid frame name: " + name);
        return dir.resolve(name);
    }

    public void delete(String name) throws IOException {
        Files.deleteIfExists(pathOf(name));
    }

    // ── Retention ────────────────────────────────────────────────────────────

    private void prune() {
        int max = SmpConfig.TIMELAPSE_MAX_FRAMES;
        if (max <= 0) return;
        List<Frame> all = list();
        if (all.size() <= max) return;
        for (String name : evenlySpacedToRemove(all, all.size() - max)) {
            try { delete(name); } catch (IOException ignored) {}
        }
    }

    /**
     * Picks {@code toRemove} frames spread evenly through the timeline, never the
     * first or last, so thinning is uniform and the endpoints are preserved.
     */
    static List<String> evenlySpacedToRemove(List<Frame> newestFirst, int toRemove) {
        List<Frame> oldestFirst = new ArrayList<>(newestFirst);
        oldestFirst.sort(Comparator.comparingLong(Frame::capturedAt));
        List<String> victims = new ArrayList<>();
        int n = oldestFirst.size();
        if (toRemove <= 0 || n <= 2) return victims;
        double step = (double) (n - 1) / (toRemove + 1);
        for (int i = 1; i <= toRemove; i++) {
            int idx = (int) Math.round(i * step);
            if (idx <= 0) idx = 1;
            if (idx >= n - 1) idx = n - 2;
            victims.add(oldestFirst.get(idx).name());
        }
        return victims;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Frame toFrame(Path p) {
        var m = NAME_RX.matcher(p.getFileName().toString());
        if (!m.matches()) return null;
        try {
            int bpp = m.group(2) != null ? Integer.parseInt(m.group(2)) : 1;
            return new Frame(p.getFileName().toString(), Long.parseLong(m.group(1)), Files.size(p), bpp);
        } catch (IOException e) {
            return null;
        }
    }
}
