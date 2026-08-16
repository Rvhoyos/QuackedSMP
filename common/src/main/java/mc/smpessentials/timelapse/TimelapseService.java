package mc.smpessentials.timelapse;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the periodic capture of world-map timelapse frames. Ticks on a daemon
 * thread; when a capture is due it flushes the world and pauses autosave (so
 * region files on disk are current and stable), renders the configured dimension
 * from disk via {@link WorldMapRenderer}, and stores a PNG frame. Full frames
 * are rendered each interval and stored independently, so playback assembly is
 * pure client-side frame flipping.
 */
public final class TimelapseService {

    private static final long TICK_SECONDS = 60;

    private static final TimelapseService INSTANCE = new TimelapseService();

    private final WorldMapRenderer renderer   = new WorldMapRenderer();
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

    public TimelapseFrameStore store() { return TimelapseFrameStore.fromConfig(); }

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
            if (srv == null || !SmpConfig.TIMELAPSE_ENABLED || inProgress.get()) return;

            if (srv.getPlayerList().getPlayerCount() > 0) activitySinceCapture = true;

            // Skip idle periods: only capture when someone has been on since the
            // last frame, so a dormant server does not accrue duplicate frames.
            if (!activitySinceCapture) return;
            if (System.currentTimeMillis() - lastCaptureOrStart() >= intervalMs()) {
                capture(srv);
            }
        } catch (Exception e) {
            SmpUtilsMod.LOGGER.warn("[Timelapse] Scheduler tick failed: {}", e.getMessage());
        }
    }

    private long intervalMs() {
        return Math.max(1, SmpConfig.TIMELAPSE_INTERVAL_MINUTES) * 60_000L;
    }

    private long lastCaptureOrStart() {
        List<TimelapseFrameStore.Frame> all = store().list();
        return all.isEmpty() ? startedAt : all.get(0).capturedAt();
    }

    // ── Capture ──────────────────────────────────────────────────────────────

    /** Renders and stores one frame now on a worker thread. No-op if already capturing. */
    public void capture(MinecraftServer srv) {
        if (!inProgress.compareAndSet(false, true)) return;
        activitySinceCapture = false;
        Thread worker = new Thread(() -> {
            try {
                runOnServer(srv, () -> {
                    srv.saveEverything(true, true, true);
                    for (ServerLevel l : srv.getAllLevels()) l.noSave = true;
                });
                try {
                    BufferedImage frame = renderer.render(srv, dimensionFromConfig());
                    if (frame == null) {
                        SmpUtilsMod.LOGGER.info("[Timelapse] Skipped capture: dimension has no generated chunks.");
                    } else {
                        store().add(frame, System.currentTimeMillis());
                        SmpUtilsMod.LOGGER.info("[Timelapse] Captured frame ({}x{}).", frame.getWidth(), frame.getHeight());
                    }
                } finally {
                    runOnServer(srv, () -> {
                        for (ServerLevel l : srv.getAllLevels()) l.noSave = false;
                    });
                }
            } catch (Exception e) {
                SmpUtilsMod.LOGGER.error("[Timelapse] Capture failed: {}", e.getMessage(), e);
            } finally {
                inProgress.set(false);
            }
        }, "Timelapse-Capture");
        worker.setDaemon(true);
        worker.start();
    }

    private static ResourceKey<Level> dimensionFromConfig() {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(SmpConfig.TIMELAPSE_DIMENSION));
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
