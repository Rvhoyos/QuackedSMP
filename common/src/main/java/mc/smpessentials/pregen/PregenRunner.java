package mc.smpessentials.pregen;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.mixin.MinecraftServerMixin;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Generates the configured area at startup, before the server takes players.
 *
 * It runs on the server thread on purpose: the alternative, trickling chunks out during normal
 * ticks, is the lag this feature exists to remove. Blocking has one hard constraint, which is that
 * the watchdog forcibly shuts the server down once a tick looks like it has taken longer than
 * max-tick-time (one minute by default). Vanilla hits the same wall loading its own initial chunks
 * and answers it in MinecraftServer.prepareLevels by pushing the tick deadline forward and calling
 * waitUntilNextTick between rounds. This does the same.
 */
public final class PregenRunner {

    private PregenRunner() {}

    // Chunks requested before waiting. Enough to keep the generation workers busy; small enough
    // that this many chunks sitting at FULL is not a memory problem.
    private static final int BATCH = 64;

    // Matches vanilla's own prepareLevels pacing between waits.
    private static final long TICK_DELAY_NANOS = 10L * 1_000_000L;

    // One region file's worth of chunks between durable checkpoints. Flushing every batch would
    // rewrite the progress file 16 times per region for no gain, and losing at most this many
    // means re-reading chunks that already exist, never regenerating them.
    private static final long CHECKPOINT_CHUNKS = 1024L;

    // Ceiling on how many region files the disk-cost measurement will stat before giving up.
    // A 10,000 file dimension is already ~10 million chunks, well past anything pregen writes.
    private static final long MAX_MEASURED_FILES = 20_000L;

    // Room for the region writes already in flight plus the level save that follows. Not a tuned
    // number: it is the point below which stopping is safer than continuing to write.
    private static final long DISK_FLOOR_BYTES = 512L * 1024L * 1024L;

    /**
     * Runs every configured dimension to completion. Called last in the server-started hook, so
     * custom dimensions already exist and the dashboard is already serving its log feed.
     */
    public static void onServerStarted(MinecraftServer server) {
        if (!SmpConfig.PREGEN_ENABLED) return;

        int distance = SmpConfig.PREGEN_DISTANCE;
        PregenProgress progress = PregenProgress.get(server);
        List<ServerLevel> pending = new ArrayList<>();
        for (String id : SmpConfig.PREGEN_DIMENSIONS) {
            ServerLevel level = levelOf(server, id);
            if (level == null) {
                SmpUtilsMod.LOGGER.warn("[Pregen] Skipping {}, not a dimension on this server.", id);
            } else if (!progress.isComplete(id, distance)) {
                pending.add(level);
            }
        }

        if (pending.isEmpty()) return;

        SmpUtilsMod.LOGGER.info("[Pregen] Starting. The server does not accept joins until this finishes.");
        for (ServerLevel level : pending) {
            try {
                runDimension(server, level, distance, progress);
            } catch (Exception e) {
                // A failed dimension keeps its cursor, so the next boot resumes rather than
                // restarting, and the server still finishes booting.
                SmpUtilsMod.LOGGER.error("[Pregen] {} stopped early", level.dimension().identifier(), e);
            }
        }
        SmpUtilsMod.LOGGER.info("[Pregen] Done.");
    }

    private static void runDimension(MinecraftServer server, ServerLevel level, int distance,
                                     PregenProgress progress) {
        Optional<PregenArea> resolved = PregenArea.resolve(level, distance);
        if (resolved.isEmpty()) return;
        PregenArea area = resolved.get();

        long total = area.chunkCount();
        long cursor = progress.cursor(area.dimension(), distance);
        if (cursor >= total) {
            progress.advance(area.dimension(), distance, total, true,
                    progress.kbPerChunk(area.dimension()), progress.chunksPerSecond(area.dimension()));
            return;
        }

        if (cursor > 0L) {
            SmpUtilsMod.LOGGER.info("[Pregen] {}: radius {} blocks, {} chunks, resuming at {}.",
                    area.dimension(), area.radiusBlocks(), total, cursor);
        } else {
            SmpUtilsMod.LOGGER.info("[Pregen] {}: radius {} blocks, {} chunks.",
                    area.dimension(), area.radiusBlocks(), total);
        }

        ServerChunkCache chunks = level.getChunkSource();
        long startNanos = Util.getNanos();
        long startCursor = cursor;
        long lastLogged = cursor;
        long startBytes = dimensionBytes(level);

        long sinceCheckpoint = 0L;
        while (cursor < total) {
            long batchEnd = Math.min(cursor + BATCH, total);
            List<CompletableFuture<?>> batch = new ArrayList<>(BATCH);
            for (long i = cursor; i < batchEnd; i++) {
                ChunkPos pos = area.chunkAt(i);
                // SPAWN_SEARCH loads without simulating and without persisting a ticket, and
                // radius 0 stops at FULL, so the chunk generates and saves but never ticks.
                batch.add(chunks.addTicketAndLoadWithRadius(TicketType.SPAWN_SEARCH, pos, 0));
            }
            awaitBatch(server, batch);

            // Expires those tickets and runs the unload and save pass. waitUntilNextTick only
            // drains tasks, so without this the batch's chunks stay resident and accumulate.
            chunks.tick(() -> true, false);

            sinceCheckpoint += batchEnd - cursor;
            cursor = batchEnd;

            boolean lowDisk = freeSpaceBytes(server) < DISK_FLOOR_BYTES;
            boolean last = cursor >= total || lowDisk;
            if (sinceCheckpoint >= CHECKPOINT_CHUNKS || last) {
                checkpoint(server, level, area, distance, cursor, cursor >= total, progress,
                        startBytes, cursor - startCursor, startNanos, last);
                sinceCheckpoint = 0L;
            }

            if (cursor - lastLogged >= CHECKPOINT_CHUNKS || cursor >= total) {
                SmpUtilsMod.LOGGER.info("[Pregen] {}: {}/{} chunks ({}%), {} chunks/s.",
                        area.dimension(), cursor, total, cursor * 100 / total,
                        Math.round(observedRate(startNanos, cursor - startCursor)));
                lastLogged = cursor;
            }

            if (lowDisk) {
                SmpUtilsMod.LOGGER.error("[Pregen] {}: stopping at {}/{} chunks, free disk is below {}.",
                        area.dimension(), cursor, total, PregenEstimate.formatBytes(DISK_FLOOR_BYTES));
                return;
            }
        }
    }

    /**
     * Makes progress durable, in the one order that survives a kill: chunks to disk first, then
     * the cursor, then the cursor to disk. A cursor on disk therefore always implies the chunks it
     * claims are on disk too, so a resumed run can never skip a chunk it never generated. The
     * reverse, re-visiting chunks a lost checkpoint already made, only costs a read.
     *
     * @param measure whether to re-measure bytes written. Only true on the last checkpoint of a
     *                dimension, because measuring walks every region file the dimension has and
     *                doing that every checkpoint would scale the run with the world's file count
     *                for a number that is only read once the run is over.
     */
    private static void checkpoint(MinecraftServer server, ServerLevel level, PregenArea area,
                                   int distance, long cursor, boolean complete,
                                   PregenProgress progress, long startBytes, long generated,
                                   long startNanos, boolean measure) {
        level.getChunkSource().save(true);
        double previous = progress.kbPerChunk(area.dimension());
        progress.advance(area.dimension(), distance, cursor, complete,
                measure ? writtenKbPerChunk(level, startBytes, generated, previous) : previous,
                observedRate(startNanos, generated));
        server.overworld().getDataStorage().saveAndJoin();
    }

    /**
     * Waits for one batch the way vanilla waits for its initial chunks: push the tick deadline out
     * a little, then park until it passes, running main-thread tasks the whole time. Chunk
     * generation completes through those tasks, and the watchdog sees a tick that keeps ending.
     */
    private static void awaitBatch(MinecraftServer server, List<CompletableFuture<?>> batch) {
        MinecraftServerMixin access = (MinecraftServerMixin) server;
        while (!batch.stream().allMatch(CompletableFuture::isDone)) {
            access.setNextTickTimeNanos(Util.getNanos() + TICK_DELAY_NANOS);
            access.invokeWaitUntilNextTick();
        }
        // Leave the deadline at now so the first real tick after the run is not judged late.
        access.setNextTickTimeNanos(Util.getNanos());
    }

    private static double observedRate(long startNanos, long chunksDone) {
        double seconds = (Util.getNanos() - startNanos) / 1.0E9;
        return seconds > 0.0 ? chunksDone / seconds : PregenEstimate.SEED_CHUNKS_PER_SECOND;
    }

    /**
     * What this run actually cost per chunk, from how much the dimension's region files grew.
     * Measuring the growth rather than the total keeps chunks that already existed before the run
     * out of the figure. Falls back to the previous number when the growth is not measurable, for
     * instance on a resumed run that generated nothing.
     */
    private static double writtenKbPerChunk(ServerLevel level, long startBytes, long generated,
                                            double previous) {
        long endBytes = dimensionBytes(level);
        // Either end unmeasurable means the difference is not a growth figure at all, so keep what
        // the last run learned rather than inventing one from a half-measurement.
        if (startBytes <= 0L || endBytes <= 0L || generated <= 0L) return previous;
        long grew = endBytes - startBytes;
        return grew > 0L ? grew / 1024.0 / generated : previous;
    }

    /**
     * Total size of one dimension's chunk storage. Reads file sizes only, never chunk contents.
     * Returns 0 when the size cannot be established, which the caller reads as "keep the previous
     * figure" rather than as an empty dimension.
     *
     * Gives up past MAX_MEASURED_FILES because this runs on the server thread and a directory can
     * hold far more entries than a world's chunks account for. One dev world here carries three
     * million files in its region directory, enough that merely listing it takes minutes.
     */
    private static long dimensionBytes(ServerLevel level) {
        Path root = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path dimension = DimensionType.getStorageFolder(level.dimension(), root);
        long bytes = 0L;
        long seen = 0L;
        for (String sub : new String[]{"region", "entities", "poi"}) {
            Path dir = dimension.resolve(sub);
            if (!Files.isDirectory(dir)) continue;
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.mca")) {
                for (Path file : files) {
                    if (++seen > MAX_MEASURED_FILES) return 0L;
                    bytes += Files.size(file);
                }
            } catch (Exception e) {
                return 0L;
            }
        }
        return bytes;
    }

    public static long freeSpaceBytes(MinecraftServer server) {
        try {
            FileStore store = Files.getFileStore(
                    server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
            return store.getUsableSpace();
        } catch (Exception e) {
            return 0L;
        }
    }

    public static ServerLevel levelOf(MinecraftServer server, String dimensionId) {
        try {
            return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimensionId)));
        } catch (Exception e) {
            return null;
        }
    }
}
