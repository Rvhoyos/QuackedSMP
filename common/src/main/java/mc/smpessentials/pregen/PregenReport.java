package mc.smpessentials.pregen;

import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where one dimension's pregen stands and what finishing it would cost. Built once and read by
 * both the command and the panel, so the two never disagree about the numbers.
 *
 * A dimension that is configured but not present on this server appears with a zero area rather
 * than being dropped, so the operator can see that the id they typed matched nothing.
 */
public record PregenReport(String dimension, boolean present, int radiusBlocks,
                    long chunks, long done, boolean complete, PregenEstimate estimate) {

    public static List<PregenReport> forServer(MinecraftServer server) {
        return forSettings(server, SmpConfig.PREGEN_DISTANCE, SmpConfig.PREGEN_DIMENSIONS);
    }

    /** The same picture for settings the operator is still considering, without saving them. */
    public static List<PregenReport> forSettings(MinecraftServer server, int distance,
                                                 List<String> dimensions) {
        PregenProgress progress = PregenProgress.get(server);
        long free = PregenRunner.freeSpaceBytes(server);

        List<PregenReport> reports = new ArrayList<>();
        for (String id : dimensions) {
            ServerLevel level = PregenRunner.levelOf(server, id);
            if (level == null) {
                reports.add(new PregenReport(id, false, 0, 0L, 0L, false,
                        PregenEstimate.of(0L, 0.0, 0.0, free)));
                continue;
            }

            Optional<PregenArea> area = PregenArea.resolve(level, distance);
            long total = area.map(PregenArea::chunkCount).orElse(0L);

            // Chunks on disk, not just the ones this mod generated. A world that was played in
            // before pre-generation was switched on has no cursor, and reporting 0 there would
            // price an already-generated area as if it were empty. The cursor still wins when it
            // is ahead, since a run in progress has written chunks the header count agrees with
            // anyway and the cursor is the resumable truth.
            long onDisk = area.map(a -> PregenCoverage.countExisting(level, a)).orElse(0L);
            long done = Math.min(Math.max(progress.cursor(id, distance), Math.max(onDisk, 0L)), total);
            // Only the chunks still to generate cost anything, so the estimate is for the work
            // left rather than for the whole square.
            reports.add(new PregenReport(id, true, area.map(PregenArea::radiusBlocks).orElse(0),
                    total, done, progress.isComplete(id, distance) || (total > 0L && done >= total),
                    PregenEstimate.of(total - done, progress.kbPerChunk(id),
                            progress.chunksPerSecond(id), free)));
        }
        return reports;
    }

    /**
     * Whether a restart is what stands between the current config and a finished area. True as
     * soon as the feature is on with work outstanding, which covers both a freshly enabled server
     * and a run that stopped early, since a restart is the answer to both.
     */
    public static boolean restartRequired(List<PregenReport> reports) {
        if (!SmpConfig.PREGEN_ENABLED) return false;
        return reports.stream().anyMatch(r -> r.present() && !r.complete() && r.chunks() > 0L);
    }

    public long remaining() {
        return Math.max(0L, chunks - done);
    }
}
