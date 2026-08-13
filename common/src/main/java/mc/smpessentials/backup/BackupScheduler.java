package mc.smpessentials.backup;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Drives periodic world snapshots. Ticks on a background thread and delegates the actual
 * save-flush and zip to {@link BackupService}; this class only decides when a backup is due.
 * The schedule is anchored on the newest existing snapshot, so restarts do not reset it and
 * no extra persistence is needed.
 */
public final class BackupScheduler {

    private static final long TICK_SECONDS   = 60;
    private static final long MILLIS_PER_HOUR = 3_600_000L;

    private static final BackupScheduler INSTANCE = new BackupScheduler();

    private volatile MinecraftServer          server;
    private volatile ScheduledExecutorService scheduler;
    private volatile long                     startedAt;
    // True once we have logged that the current due window is waiting on players to leave.
    // Reset when a backup actually runs so the next window can log again.
    private boolean deferLogged = false;

    private BackupScheduler() {}

    public static BackupScheduler get() { return INSTANCE; }

    public void start(MinecraftServer srv) {
        if (srv == null || scheduler != null) return;
        server    = srv;
        startedAt = System.currentTimeMillis();
        ScheduledExecutorService sc = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Backup-Scheduler");
            t.setDaemon(true);
            return t;
        });
        sc.scheduleAtFixedRate(this::tick, TICK_SECONDS, TICK_SECONDS, TimeUnit.SECONDS);
        scheduler = sc;
    }

    public void stop() {
        ScheduledExecutorService sc = scheduler;
        if (sc != null) { sc.shutdown(); scheduler = null; }
        server      = null;
        deferLogged = false;
    }

    // ── Policy ─────────────────────────────────────────────────────────────────

    enum Decision { NOT_DUE, DEFER, RUN }

    /**
     * Pure scheduling decision. A backup runs once the interval has elapsed since the anchor,
     * as soon as the server is empty; if it stays overdue past a second full interval it runs
     * anyway so a permanently populated server still gets backed up.
     */
    static Decision decide(long now, long anchorMillis, int players, long intervalMs) {
        long nextDue = anchorMillis + intervalMs;
        if (now < nextDue)              return Decision.NOT_DUE;
        if (players == 0)               return Decision.RUN;
        if (now - nextDue > intervalMs) return Decision.RUN;
        return Decision.DEFER;
    }

    // ── Mechanism ──────────────────────────────────────────────────────────────

    private void tick() {
        try {
            MinecraftServer srv = server;
            if (srv == null || !SmpConfig.BACKUP_PERIODIC_ENABLED) return;
            if (BackupService.get().isRunning())                   return;

            int players = srv.getPlayerList().getPlayerCount();
            switch (decide(System.currentTimeMillis(), anchorMillis(), players, intervalMs())) {
                case RUN -> {
                    deferLogged = false;
                    try {
                        BackupService.get().create(srv);
                        SmpUtilsMod.LOGGER.info("[Backup] Periodic snapshot triggered.");
                    } catch (BackupService.BusyException ignored) {}
                }
                case DEFER -> {
                    if (!deferLogged) {
                        SmpUtilsMod.LOGGER.info("[Backup] Periodic snapshot due; deferring until no players are online ({} online).", players);
                        deferLogged = true;
                    }
                }
                case NOT_DUE -> {}
            }
        } catch (Exception e) {
            SmpUtilsMod.LOGGER.warn("[Backup] Scheduler tick failed: {}", e.getMessage());
        }
    }

    private long intervalMs() {
        return Math.max(1, SmpConfig.BACKUP_INTERVAL_HOURS) * MILLIS_PER_HOUR;
    }

    private long anchorMillis() {
        List<BackupService.Snapshot> all = BackupService.get().list();
        return all.isEmpty() ? startedAt : all.get(0).createdAt();
    }
}
