package mc.smpessentials.timelapse;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.dims.DimManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the periodic capture of world-map timelapse frames. Ticks on a daemon
 * thread; when a capture is due it flushes the world and pauses autosave (so
 * region files on disk are current and stable), then renders each configured
 * dimension in sequence from disk via {@link WorldMapRenderer}, storing a PNG
 * frame per dimension in its own parallel folder. Full frames are rendered each
 * interval and stored independently, so playback assembly is pure client-side
 * frame flipping.
 */
public final class TimelapseService {

    private static final long TICK_SECONDS = 60;

    private static final TimelapseService INSTANCE = new TimelapseService();

    private final WorldMapRenderer renderer   = new WorldMapRenderer();
    private final CaptureProgress  progress   = new CaptureProgress();
    private final AtomicBoolean    inProgress = new AtomicBoolean(false);
    // True once at least one player has been seen online since the last capture.
    // Keeps an idle server from filling the timelapse with identical frames: after
    // everyone leaves, one final frame still captures their work, then captures
    // pause until someone returns.
    private volatile boolean activitySinceCapture = false;

    private volatile MinecraftServer          server;
    private volatile ScheduledExecutorService scheduler;
    private volatile long                     startedAt;

    private TimelapseService() {}

    public static TimelapseService get() { return INSTANCE; }

    public boolean isRunning() { return inProgress.get(); }

    /** Compact JSON of the current capture's progress, for the dashboard poll. */
    public String progressJson() { return progress.toJson(); }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public void start(MinecraftServer srv) {
        if (srv == null || scheduler != null) return;
        server    = srv;
        startedAt = System.currentTimeMillis();
        ScheduledExecutorService sc = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Timelapse-Scheduler");
            t.setDaemon(true);
            return t;
        });
        sc.scheduleAtFixedRate(this::tick, TICK_SECONDS, TICK_SECONDS, TimeUnit.SECONDS);
        scheduler = sc;
    }

    public void stop() {
        ScheduledExecutorService sc = scheduler;
        if (sc != null) { sc.shutdown(); scheduler = null; }
        server = null;
    }

    // ── Scheduling ───────────────────────────────────────────────────────────

    private void tick() {
        try {
            MinecraftServer srv = server;
            if (srv == null || inProgress.get()) return;

            boolean playersOnline = srv.getPlayerList().getPlayerCount() > 0;
            if (playersOnline) activitySinceCapture = true;

            // Redundancy gate: only capture when someone has been on since the
            // last frame, so a dormant server does not accrue duplicate frames.
            if (!activitySinceCapture) return;
            if (System.currentTimeMillis() - lastCaptureOrStart() < intervalMs()) return;

            // Prefer an idle server (free heap, full 1:1). While players are on,
            // defer and hold up to TIMELAPSE_MAX_SKIPS overdue intervals, then
            // force the capture (its heap budget tightens to the configured cap).
            if (!playersOnline || intervalsOverdue() > SmpConfig.TIMELAPSE_MAX_SKIPS) {
                capture(srv);
            }
        } catch (Exception e) {
            SmpUtilsMod.LOGGER.warn("[Timelapse] Scheduler tick failed: {}", e.getMessage());
        }
    }

    private long intervalMs() {
        return Math.max(1, SmpConfig.TIMELAPSE_INTERVAL_MINUTES) * 60_000L;
    }

    // How many full intervals have elapsed since the last capture; 1 the moment
    // a capture becomes due, so it exceeds MAX_SKIPS only after that many held.
    private long intervalsOverdue() {
        return (System.currentTimeMillis() - lastCaptureOrStart()) / intervalMs();
    }

    // Newest frame across all opted-in dimensions, so scheduling is driven by the
    // most recent capture of any dimension. startedAt until the first frame exists.
    private long lastCaptureOrStart() {
        long latest = 0;
        for (String dimId : SmpConfig.TIMELAPSE_DIMENSIONS) {
            List<TimelapseFrameStore.Frame> frames = TimelapseFrameStore.forDimension(dimId).list();
            if (!frames.isEmpty()) latest = Math.max(latest, frames.get(0).capturedAt());
        }
        return latest == 0 ? startedAt : latest;
    }

    // ── Capture ──────────────────────────────────────────────────────────────

    /**
     * Renders and stores one frame now on a worker thread. No-op if the feature is off or a capture
     * is already running. The enabled check lives here rather than in the scheduler so the command
     * and the panel button cannot capture while the feature is switched off.
     */
    public void capture(MinecraftServer srv) {
        if (!SmpConfig.TIMELAPSE_ENABLED) return;
        if (!inProgress.compareAndSet(false, true)) return;
        activitySinceCapture = false;
        Thread worker = new Thread(() -> {
            try {
                progress.phase("saving");
                boolean[] playersOnline = {false};
                runOnServer(srv, () -> {
                    playersOnline[0] = srv.getPlayerList().getPlayerCount() > 0;
                    srv.saveEverything(true, true, true);
                    for (ServerLevel l : srv.getAllLevels()) l.noSave = true;
                });
                List<ResourceKey<Level>> dims = dimensionsFromConfig(srv);
                try {
                    int index = 0;
                    for (ResourceKey<Level> dim : dims) {
                        index++;
                        String dimId = dim.identifier().toString();
                        progress.beginDim(dimId, index, dims.size());
                        // Recomputed per dimension: heap frees between renders as the
                        // prior frame is released, so each gets its own fresh budget.
                        long budget = HeapBudget.forRender(SmpConfig.TIMELAPSE_MAX_RENDER_MB, playersOnline[0]);
                        WorldMapRenderer.RenderResult result = renderer.render(srv, dim, budget, progress);
                        if (result == null) {
                            SmpUtilsMod.LOGGER.info("[Timelapse] Skipped {}: no generated chunks.", dimId);
                            continue;
                        }
                        BufferedImage frame = result.image();
                        progress.phase("writing");
                        TimelapseFrameStore.forDimension(dimId)
                                .add(frame, System.currentTimeMillis(), result.blocksPerPixel());
                        SmpUtilsMod.LOGGER.info("[Timelapse] Captured {} ({}x{}, {} block(s)/pixel).",
                                dimId, frame.getWidth(), frame.getHeight(), result.blocksPerPixel());
                    }
                } finally {
                    runOnServer(srv, () -> {
                        for (ServerLevel l : srv.getAllLevels()) l.noSave = false;
                    });
                }
            } catch (Exception e) {
                SmpUtilsMod.LOGGER.error("[Timelapse] Capture failed: {}", e.getMessage(), e);
            } finally {
                progress.idle();
                inProgress.set(false);
            }
        }, "Timelapse-Capture");
        worker.setDaemon(true);
        worker.start();
    }

    // Opted-in dimensions that actually exist on the running server, in config
    // order. Unknown or malformed ids are logged and skipped rather than aborting
    // the whole batch.
    private static List<ResourceKey<Level>> dimensionsFromConfig(MinecraftServer srv) {
        Set<ResourceKey<Level>> present = new HashSet<>(DimManager.listAll(srv));
        List<ResourceKey<Level>> dims = new ArrayList<>();
        for (String id : SmpConfig.TIMELAPSE_DIMENSIONS) {
            ResourceKey<Level> key;
            try {
                key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
            } catch (Exception e) {
                SmpUtilsMod.LOGGER.warn("[Timelapse] Invalid dimension id '{}'; skipping.", id);
                continue;
            }
            if (present.contains(key)) dims.add(key);
            else SmpUtilsMod.LOGGER.warn("[Timelapse] Dimension {} not present on server; skipping.", id);
        }
        return dims;
    }

    private static void runOnServer(MinecraftServer server, Runnable task) throws Exception {
        CompletableFuture<Void> fut = new CompletableFuture<>();
        server.execute(() -> {
            try { task.run(); fut.complete(null); }
            catch (Throwable t) { fut.completeExceptionally(t); }
        });
        fut.get();
    }
}
