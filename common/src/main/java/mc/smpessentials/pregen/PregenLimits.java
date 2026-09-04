package mc.smpessentials.pregen;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What a distance is allowed to be. Two bounds, both refusals rather than silent corrections,
 * because a number the operator typed should either take effect or be rejected by name.
 *
 * The world border is the operator's own rule. The disk bound exists because a world with no
 * border has nothing else holding it back, and an unbounded distance is accepted arithmetic that
 * turns into a run which never ends and a partition which fills.
 */
public final class PregenLimits {

    private PregenLimits() {}

    /**
     * A run may claim at most this share of free space. Not a taste call: {@code BackupService}
     * zips the world onto the same filesystem, so a server needs roughly its own world size still
     * free afterwards or its next snapshot fails. Leaving as much as the run consumes is the
     * cheapest rule that keeps that true.
     */
    private static final long REFUSE_DIVISOR = 2L;

    /** Warned about well before the refusal, so the number is a surprise on screen, not on save. */
    private static final long WARN_DIVISOR = 4L;

    /** The refusal message for this distance, or empty when it is allowed. */
    public static Optional<String> reject(MinecraftServer server, int distance, List<String> dimensions) {
        for (String id : dimensions) {
            ServerLevel level = PregenRunner.levelOf(server, id);
            if (level == null) continue;
            OptionalInt border = PregenArea.maxDistanceWithinBorder(level);
            if (border.isPresent() && distance > border.getAsInt()) {
                return Optional.of(String.format(Locale.US,
                        "%s allows at most %,d blocks past spawn protection inside its world border.",
                        id, border.getAsInt()));
            }
        }

        long free = PregenRunner.freeSpaceBytes(server);
        if (free <= 0L) return Optional.empty();

        long needed = estimatedBytes(server, distance, dimensions);
        long allowed = free / REFUSE_DIVISOR;
        if (needed <= allowed) return Optional.empty();

        return Optional.of(String.format(Locale.US,
                "That area needs about %s and only %s is free. Keep it under %s to leave room for "
                        + "backups, which are written to the same drive.",
                PregenEstimate.formatBytes(needed), PregenEstimate.formatBytes(free),
                PregenEstimate.formatBytes(allowed)));
    }

    /** Whether the configured area is close enough to filling the disk to say so on screen. */
    public static boolean warns(long neededBytes, long freeBytes) {
        return freeBytes > 0L && neededBytes > freeBytes / WARN_DIVISOR;
    }

    /**
     * Disk cost of the chunks a restart would still have to write at this distance. Counts what is
     * already on disk, so raising the distance on a generated world is priced on the new ring only.
     */
    private static long estimatedBytes(MinecraftServer server, int distance, List<String> dimensions) {
        PregenProgress progress = PregenProgress.get(server);
        long total = 0L;
        for (String id : dimensions) {
            ServerLevel level = PregenRunner.levelOf(server, id);
            if (level == null) continue;
            Optional<PregenArea> area = PregenArea.resolve(level, distance);
            if (area.isEmpty()) continue;

            long chunks = area.get().chunkCount();
            long existing = Math.max(0L, PregenCoverage.countExisting(level, area.get()));
            long remaining = Math.max(0L, chunks - Math.min(existing, chunks));
            total += PregenEstimate.of(remaining, progress.kbPerChunk(id), 0.0, 0L).bytes();
        }
        return total;
    }
}
