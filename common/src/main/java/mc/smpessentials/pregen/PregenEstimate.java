package mc.smpessentials.pregen;

import java.util.Locale;

/**
 * What a pregen run is about to cost: how many chunks, how much disk, how long, and whether the
 * volume has room. Pure arithmetic with no Minecraft types, so the command and the panel report
 * the same numbers instead of each deriving their own.
 *
 * An estimate is never a limit. Nothing here refuses a run.
 */
public record PregenEstimate(long chunks, long bytes, long seconds, long freeBytes) {

    /**
     * Seed figures for a server that has never run a pregen, measured off a played-in world:
     * a full 1024-chunk overworld region is 9.33 MB of terrain plus 0.38 MB of entities plus
     * 0.02 MB of poi. Sparse dimensions come in far lower, so the first real run replaces this
     * with what that dimension actually wrote.
     */
    public static final double SEED_KB_PER_CHUNK = 9.7;

    /**
     * Generation rate for a server that has never run one. Replaced by the observed rate as soon
     * as the first batch finishes, so it only ever colours the very first estimate.
     */
    public static final double SEED_CHUNKS_PER_SECOND = 40.0;

    public static PregenEstimate of(long chunks, double kbPerChunk, double chunksPerSecond, long freeBytes) {
        long bytes = (long) (chunks * Math.max(0.0, kbPerChunk) * 1024.0);
        double rate = chunksPerSecond > 0.0 ? chunksPerSecond : SEED_CHUNKS_PER_SECOND;
        return new PregenEstimate(chunks, bytes, (long) Math.ceil(chunks / rate), freeBytes);
    }

    /** False when the run cannot physically fit, which the panel shows and the operator decides on. */
    public boolean fitsInFreeSpace() {
        return freeBytes <= 0L || bytes < freeBytes;
    }

    public String describe() {
        return String.format(Locale.US, "%,d chunks, ~%s, ~%s", chunks, formatBytes(bytes), formatDuration(seconds));
    }

    public static String formatBytes(long bytes) {
        if (bytes >= 1L << 30) return String.format(Locale.US, "%.1f GB", bytes / (double) (1L << 30));
        if (bytes >= 1L << 20) return String.format(Locale.US, "%.0f MB", bytes / (double) (1L << 20));
        return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
    }

    public static String formatDuration(long seconds) {
        if (seconds >= 3600) return String.format(Locale.US, "%dh %dm", seconds / 3600, (seconds % 3600) / 60);
        if (seconds >= 60) return String.format(Locale.US, "%dm %ds", seconds / 60, seconds % 60);
        return seconds + "s";
    }
}
