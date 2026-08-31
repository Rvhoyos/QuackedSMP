package mc.smpessentials.dashboard;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.commandblocks.CommandBlockHandler;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.serverlog.ServerLogService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// Manages the dashboard server lifecycle and event broadcasting.
// HTTP and WebSocket share a single port. Spark is optional.
public final class DashboardManager {
    private DashboardManager() {}

    private static volatile DashboardServer server;
    private static volatile ScheduledExecutorService scheduler;
    private static volatile MinecraftServer mcServer;
    private static volatile boolean running = false;
    private static boolean sparkLoaded = false;
    private static volatile long cachedWorldSize = -1;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    // Detects Spark at startup. Call once before onServerStart.
    public static void init() {
        try {
            Class.forName("me.lucko.spark.api.SparkProvider");
            sparkLoaded = true;
            SmpUtilsMod.LOGGER.info("[Dashboard] Spark API detected.");
        } catch (ClassNotFoundException e) {
            SmpUtilsMod.LOGGER.info("[Dashboard] Spark not found. /api/spark/* will return unavailable.");
        }
    }

    public static void onServerStart(MinecraftServer srv) {
        mcServer = srv;
        if (!SmpConfig.DASHBOARD_ENABLED) {
            SmpUtilsMod.LOGGER.info("[Dashboard] Disabled in config.");
            return;
        }
        start(SmpConfig.DASHBOARD_PORT);
    }

    public static void onServerStop() {
        stop();
        mcServer = null;
    }

    // Saves dashboard_enabled=true and starts the server if not already running.
    public static void scheduleEnable() {
        if (running) return;
        SmpConfig.DASHBOARD_ENABLED = true;
        mc.smpessentials.config.ConfigIO.save();
        if (mcServer != null) start(SmpConfig.DASHBOARD_PORT);
    }

    // Saves dashboard_enabled=false, then stops after a short delay so the in-flight
    // HTTP response can be sent first.
    public static void scheduleDisable() {
        SmpConfig.DASHBOARD_ENABLED = false;
        mc.smpessentials.config.ConfigIO.save();
        Thread t = new Thread(() -> {
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            stop();
        }, "Dashboard-Disable");
        t.setDaemon(true);
        t.start();
    }

    // ── Start / stop ───────────────────────────────────────────────────────────

    private static void start(int port) {
        ServerLogService.get().start();
        DashboardServer s = new DashboardServer(port);
        registerRoutes(s);
        s.start();
        server = s;

        if (sparkLoaded) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Dashboard-Scheduler");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(DashboardManager::broadcastTpsUpdate, 10, 10, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(DashboardManager::refreshWorldSize, 0, 5, TimeUnit.MINUTES);
        }

        running = true;
        SmpUtilsMod.LOGGER.info("[Dashboard] Started on port {}", port);
    }

    private static void stop() {
        running = false;
        ServerLogService.get().stop();
        ScheduledExecutorService sc = scheduler;
        if (sc != null) { sc.shutdown(); scheduler = null; }
        DashboardServer s = server;
        if (s != null) { s.shutdown(); server = null; }
        SmpUtilsMod.LOGGER.info("[Dashboard] Stopped.");
    }

    // ── Route registration ─────────────────────────────────────────────────────

    private static void registerRoutes(DashboardServer s) {
        s.addRoute("/api/health", () -> {
            int     online        = mcServer != null ? mcServer.getPlayerList().getPlayerCount() : 0;
            boolean adminOn       = SmpConfig.ADMIN_ENABLED;
            boolean hasPassword   = !SmpConfig.ADMIN_PASSWORD_HASH.isBlank();
            boolean publicBackup  = SmpConfig.backupPublicAvailable();
            String  serverName    = SmpConfig.SERVER_NAME;
            String version = mc.smpessentials.SmpUtilsMod.VERSION;
            // Restore info rides along only when public download is on. The seed is included
            // only when the owner also opts into disclosure; otherwise it stays admin-only.
            String restore = "";
            if (publicBackup) {
                RestoreInfo info = RestoreInfo.capture(mcServer);
                if (info != null) {
                    restore = "," + RestoreInfoHandler.jsonFields(info, SmpConfig.BACKUP_PUBLIC_SEED_DISCLOSURE);
                }
            }
            return String.format(
                    "{\"status\":\"ok\",\"online\":%d,\"adminEnabled\":%b,\"hasPassword\":%b,\"backupPublicEnabled\":%b,\"serverName\":\"%s\",\"version\":\"%s\"%s}",
                    online, adminOn, hasPassword, publicBackup, jsonEscape(serverName), jsonEscape(version), restore);
        });
        s.addRoute("/api/metrics", () -> {
            Runtime rt       = Runtime.getRuntime();
            long heapUsed    = rt.totalMemory() - rt.freeMemory();
            long heapMax     = rt.maxMemory();
            long ramTotal = 0, ramFree = 0;
            try {
                com.sun.management.OperatingSystemMXBean os =
                        (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                ramTotal = os.getTotalMemorySize();
                ramFree  = readPlatformFreeMemory(os);
            } catch (Exception ignored) {}
            long diskTotal   = 0, diskUsable = 0;
            try {
                java.nio.file.FileStore fs = Files.getFileStore(Path.of(".").toAbsolutePath());
                diskTotal  = fs.getTotalSpace();
                diskUsable = fs.getUsableSpace();
            } catch (Exception ignored) {}
            long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
            int  threads  = ManagementFactory.getThreadMXBean().getThreadCount();
            long worldSize = cachedWorldSize;
            return String.format(Locale.US,
                    "{\"heapUsed\":%d,\"heapMax\":%d,\"ramTotal\":%d,\"ramFree\":%d,\"diskTotal\":%d,\"diskUsable\":%d,\"uptimeMs\":%d,\"threads\":%d,\"worldSize\":%d}",
                    heapUsed, heapMax, ramTotal, ramFree, diskTotal, diskUsable, uptimeMs, threads, worldSize);
        });
        // Spark routes: only reference SparkMetrics if Spark is on the classpath.
        // SparkMetrics imports Spark types directly; loading it without Spark present
        // throws NoClassDefFoundError and would crash the dashboard startup.
        if (sparkLoaded) {
            s.addRoute("/api/spark/tps",  SparkMetrics::getTpsJson);
            s.addRoute("/api/spark/cpu",  SparkMetrics::getCpuJson);
            s.addRoute("/api/spark/tick", SparkMetrics::getMsptJson);
        } else {
            s.addRoute("/api/spark/tps",  () -> "{\"error\":\"spark_unavailable\"}");
            s.addRoute("/api/spark/cpu",  () -> "{\"error\":\"spark_unavailable\"}");
            s.addRoute("/api/spark/tick", () -> "{\"error\":\"spark_unavailable\"}");
        }

        // Admin endpoints
        s.addRoute("/api/admin/status", AdminHandler::handleStatus);
        s.addRoute("/api/admin/setup",  AdminHandler::handleSetup);
        s.addRoute("/api/admin/login",  AdminHandler::handleLogin);
        s.addRoute("/api/admin/logout", AdminHandler::handleLogout);
        s.addRoute("/api/admin/players",
                (m, h, b) -> AdminHandler.handlePlayers(m, h, b, mcServer));
        s.addRoute("/api/admin/exec",
                (m, h, b) -> AdminHandler.handleExec(m, h, b, mcServer));
        s.addRoute("/api/admin/logs",   LogHandler::handleLogs);
        s.addRoute("/api/admin/config",
                (m, h, b) -> "GET".equals(m)
                        ? AdminHandler.handleConfigGet(m, h, b, mcServer)
                        : AdminHandler.handleConfigPost(m, h, b, mcServer));
        s.addRoute("/api/admin/setop",
                (m, h, b) -> AdminHandler.handleSetOp(m, h, b, mcServer));
        s.addRoute("/api/admin/players/settier",
                (m, h, b) -> AdminHandler.handleSetTier(m, h, b, mcServer));
        s.addRoute("/api/admin/dims",
                (m, h, b) -> AdminHandler.handleDimsGet(m, h, b, mcServer));
        s.addRoute("/api/admin/dims/etherparams",
                (m, h, b) -> AdminHandler.handleEtherParams(m, h, b, mcServer));
        s.addRoute("/api/admin/dims/create",
                (m, h, b) -> AdminHandler.handleDimCreate(m, h, b, mcServer));
        s.addRoute("/api/admin/dims/delete",
                (m, h, b) -> AdminHandler.handleDimDelete(m, h, b, mcServer));
        s.addRoute("/api/admin/dims/tp",
                (m, h, b) -> AdminHandler.handleDimTp(m, h, b, mcServer));
        s.addRoute("/api/admin/dims/setportal",
                (m, h, b) -> AdminHandler.handleDimSetPortal(m, h, b, mcServer));
        s.addRoute("/api/admin/dims/sky",
                (m, h, b) -> AdminHandler.handleDimSky(m, h, b, mcServer));
        s.addRoute("/api/admin/blocks",
                (m, h, b) -> AdminHandler.handleBlocksGet(m, h, b));
        s.addRoute("/api/admin/restore-info",
                (m, h, b) -> RestoreInfoHandler.handleGet(m, h, b, mcServer));
        s.addRoute("/api/admin/biomes",
                (m, h, b) -> AdminHandler.handleBiomesGet(m, h, b, mcServer));

        // Skills leaderboard (public)
        s.addRoute("/api/skills/leaderboard",
                (m, h, b) -> AdminHandler.handleSkillsLeaderboard(m, h, b, mcServer));

        // Hardcore leaderboard (public)
        s.addRoute("/api/hardcore/leaderboard",
                (m, h, b) -> HardcoreHandler.handleLeaderboard(m, h, b, mcServer));

        // Skills admin
        s.addRoute("/api/admin/skills/players",
                (m, h, b) -> AdminHandler.handleSkillsPlayers(m, h, b, mcServer));
        s.addRoute("/api/admin/skills",
                (m, h, b) -> AdminHandler.handleSkillsGet(m, h, b, mcServer));
        s.addRoute("/api/admin/skills/set",
                (m, h, b) -> AdminHandler.handleSkillsSet(m, h, b, mcServer));

        // Claims overview
        s.addRoute("/api/admin/claims",
                (m, h, b) -> AdminHandler.handleClaimsGet(m, h, b, mcServer));
        s.addRoute("/api/admin/claims/unclaim",
                (m, h, b) -> AdminHandler.handleClaimsUnclaim(m, h, b, mcServer));

        // Dashboard self-disable
        s.addRoute("/api/admin/dashboard/disable",
                (m, h, b) -> AdminHandler.handleDashboardDisable(m, h, b));

        // Mods management
        s.addRoute("/api/admin/mods",
                (m, h, b) -> AdminHandler.handleMods(m, h, b));
        s.addUploadRoute("/api/admin/mods/upload",
                (m, h, stream, len) -> AdminHandler.handleModsUpload(m, h, stream, len));

        // Chat filter management
        s.addRoute("/api/admin/chatfilter",
                (m, h, b) -> AdminHandler.handleChatFilterGet(m, h, b, mcServer));
        s.addRoute("/api/admin/chatfilter/add",
                (m, h, b) -> AdminHandler.handleChatFilterAdd(m, h, b, mcServer));
        s.addRoute("/api/admin/chatfilter/remove",
                (m, h, b) -> AdminHandler.handleChatFilterRemove(m, h, b, mcServer));
        s.addRoute("/api/admin/chatfilter/mutes",
                (m, h, b) -> AdminHandler.handleChatFilterMutes(m, h, b, mcServer));
        s.addRoute("/api/admin/chatfilter/unmute",
                (m, h, b) -> AdminHandler.handleChatFilterUnmute(m, h, b, mcServer));

        // Hardcore session management
        s.addRoute("/api/admin/hardcore",
                (m, h, b) -> AdminHandler.handleHardcoreGet(m, h, b, mcServer));
        s.addRoute("/api/admin/hardcore/end",
                (m, h, b) -> AdminHandler.handleHardcoreEnd(m, h, b, mcServer));

        // Teams management
        s.addRoute("/api/admin/teams",
                (m, h, b) -> AdminHandler.handleTeamsGet(m, h, b, mcServer));
        s.addRoute("/api/admin/teams/create",
                (m, h, b) -> AdminHandler.handleTeamCreate(m, h, b, mcServer));
        s.addRoute("/api/admin/teams/update",
                (m, h, b) -> AdminHandler.handleTeamUpdate(m, h, b, mcServer));
        s.addRoute("/api/admin/teams/delete",
                (m, h, b) -> AdminHandler.handleTeamDelete(m, h, b, mcServer));
        s.addRoute("/api/admin/teams/addplayer",
                (m, h, b) -> AdminHandler.handleTeamAddPlayer(m, h, b, mcServer));
        s.addRoute("/api/admin/teams/removeplayer",
                (m, h, b) -> AdminHandler.handleTeamRemovePlayer(m, h, b, mcServer));
        s.addRoute("/api/admin/playernames",
                (m, h, b) -> AdminHandler.handlePlayerNames(m, h, b, mcServer));
        s.addRoute("/api/admin/dims/all",
                (m, h, b) -> AdminHandler.handleDimsAll(m, h, b, mcServer));
        s.addRoute("/api/admin/teams/autoassign",
                (m, h, b) -> "GET".equals(m)
                        ? AdminHandler.handleAutoAssignGet(m, h, b)
                        : AdminHandler.handleAutoAssignPost(m, h, b, mcServer));

        // Wilderness regen
        s.addRoute("/api/admin/regen",
                (m, h, b) -> AdminHandler.handleRegen(m, h, b, mcServer));

        // Shops management
        s.addRoute("/api/admin/shops",
                (m, h, b) -> AdminHandler.handleShopsGet(m, h, b, mcServer));
        s.addRoute("/api/admin/shops/delete",
                (m, h, b) -> AdminHandler.handleShopsDelete(m, h, b, mcServer));
        s.addRoute("/api/admin/economy",
                (m, h, b) -> AdminHandler.handleEconomyGet(m, h, b, mcServer));

        // Command blocks management
        s.addRoute("/api/admin/commandblocks",
                (m, h, b) -> CommandBlockHandler.handleGet(m, h, b, mcServer));
        s.addRoute("/api/admin/commandblocks/update",
                (m, h, b) -> CommandBlockHandler.handleUpdate(m, h, b, mcServer));
        s.addRoute("/api/admin/commandblocks/delete",
                (m, h, b) -> CommandBlockHandler.handleDelete(m, h, b, mcServer));

        // World backup snapshots
        s.addRoute("/api/admin/backups",
                BackupHandler::handleList);
        s.addRoute("/api/admin/backups/create",
                (m, h, b) -> BackupHandler.handleCreate(m, h, b, mcServer));
        s.addRoute("/api/admin/backups/delete",
                BackupHandler::handleDelete);
        s.addDownloadRoute("/api/admin/backups/download",
                BackupHandler::handleDownload);

        // Public latest-snapshot name and download (both gated by backupPublicAvailable)
        s.addRoute("/api/backups/latest",
                BackupHandler::handleLatestInfo);
        s.addDownloadRoute("/api/backups/latest/download",
                (m, h) -> BackupHandler.handleLatestPublic(m, h, mcServer));

        // World-map timelapse frames
        s.addRoute("/api/admin/timelapse",
                (m, h, b) -> TimelapseHandler.handleList(m, h, b, mcServer));
        s.addRoute("/api/admin/timelapse/capture",
                (m, h, b) -> TimelapseHandler.handleCapture(m, h, b, mcServer));
        s.addRoute("/api/admin/timelapse/delete",
                TimelapseHandler::handleDelete);
        s.addDownloadRoute("/api/admin/timelapse/frame",
                TimelapseHandler::handleFrame);
        s.addDownloadRoute("/api/admin/timelapse/export",
                TimelapseHandler::handleExport);

        // Chunk pre-generation
        s.addRoute("/api/admin/pregen",
                (m, h, b) -> PregenHandler.handleGet(m, h, b, mcServer));
        s.addRoute("/api/admin/pregen/save",
                (m, h, b) -> PregenHandler.handleSave(m, h, b, mcServer));
        s.addRoute("/api/admin/pregen/preview",
                (m, h, b) -> PregenHandler.handlePreview(m, h, b, mcServer));

        s.addRoute("/api/admin/rtp",
                (m, h, b) -> RtpHandler.handleGet(m, h, b, mcServer));
        s.addRoute("/api/admin/rtp/save",
                (m, h, b) -> RtpHandler.handleSave(m, h, b, mcServer));

        s.addRoute("/api/admin/welcomebook",
                (m, h, b) -> WelcomeBookHandler.handleGet(m, h, b, mcServer));
        s.addRoute("/api/admin/welcomebook/save",
                (m, h, b) -> WelcomeBookHandler.handleSave(m, h, b, mcServer));

        s.addRoute("/api/admin/kits",
                (m, h, b) -> KitHandler.handleGet(m, h, b, mcServer));
        s.addRoute("/api/admin/kits/save",
                (m, h, b) -> KitHandler.handleSave(m, h, b, mcServer));

        // Shared by every editor that stores an item or an effect, not just RTP.
        s.addRoute("/api/admin/registry",
                (m, h, b) -> ItemHandler.handleRegistry(m, h, b, mcServer));
    }

    // ── Scheduled ─────────────────────────────────────────────────────────────

    private static void broadcastTpsUpdate() {
        DashboardServer s = server;
        if (s == null) return;
        try {
            s.broadcast(String.format(
                    "{\"type\":\"tps_update\",\"data\":%s,\"timestamp\":%d}",
                    SparkMetrics.getTpsJson(), System.currentTimeMillis()));
        } catch (Exception ignored) {}
    }

    private static void refreshWorldSize() {
        MinecraftServer srv = mcServer;
        if (srv == null) return;
        try {
            Path worldRoot = srv.getWorldPath(LevelResource.ROOT);
            long total = 0;
            try (var walk = Files.walk(worldRoot)) {
                for (var it = walk.iterator(); it.hasNext(); ) {
                    Path p = it.next();
                    if (Files.isRegularFile(p)) {
                        total += Files.size(p);
                    }
                }
            }
            cachedWorldSize = total;
        } catch (IOException ignored) {}
    }

    // ── Event broadcast ────────────────────────────────────────────────────────

    // Broadcasts to WebSocket clients and forwards to Discord.
    public static void broadcastPlayerJoin(String playerName) {
        broadcast(String.format(Locale.US,
                "{\"type\":\"player_join\",\"player\":\"%s\",\"timestamp\":%d}",
                jsonEscape(playerName), System.currentTimeMillis()));
        DiscordWebhook.sendJoin(playerName);
    }

    public static void broadcastPlayerLeave(String playerName) {
        broadcast(String.format(Locale.US,
                "{\"type\":\"player_leave\",\"player\":\"%s\",\"timestamp\":%d}",
                jsonEscape(playerName), System.currentTimeMillis()));
        DiscordWebhook.sendLeave(playerName);
    }

    public static void broadcastChat(String playerName, String message) {
        broadcast(String.format(Locale.US,
                "{\"type\":\"chat\",\"player\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}",
                jsonEscape(playerName), jsonEscape(message), System.currentTimeMillis()));
        DiscordWebhook.sendChat(playerName, message);
    }

    private static void broadcast(String json) {
        if (!running) return;
        DashboardServer s = server;
        if (s != null) s.broadcast(json);
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    // Returns best available free-memory figure.
    // Linux: /proc/meminfo MemAvailable. macOS: vm_stat. Fallback: MXBean.
    private static long readPlatformFreeMemory(com.sun.management.OperatingSystemMXBean os) {
        long memAvail = readMemAvailable();
        if (memAvail > 0) return memAvail;
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            long vmFree = readVmStat();
            if (vmFree > 0) return vmFree;
        }
        return os.getFreeMemorySize();
    }

    // Reads MemAvailable from /proc/meminfo (Linux only). Returns -1 if unavailable.
    private static long readMemAvailable() {
        try {
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of("/proc/meminfo"))) {
                if (line.startsWith("MemAvailable:")) {
                    String[] parts = line.trim().split("\\s+");
                    return Long.parseLong(parts[1]) * 1024; // kB to bytes
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    // macOS: (free + inactive + speculative) * page_size. Returns -1 on failure.
    private static long readVmStat() {
        try {
            Process p = Runtime.getRuntime().exec("vm_stat");
            p.waitFor(2, TimeUnit.SECONDS);
            String output = new String(p.getInputStream().readAllBytes());
            long pageSize = 4096;
            long pagesFree = 0, pagesInactive = 0, pagesSpeculative = 0;
            for (String line : output.split("\n")) {
                if (line.contains("page size of ")) {
                    String[] parts = line.split("page size of ");
                    if (parts.length > 1)
                        pageSize = Long.parseLong(parts[1].trim().split(" ")[0]);
                } else if (line.startsWith("Pages free:")) {
                    pagesFree = vmStatLong(line);
                } else if (line.startsWith("Pages inactive:")) {
                    pagesInactive = vmStatLong(line);
                } else if (line.startsWith("Pages speculative:")) {
                    pagesSpeculative = vmStatLong(line);
                }
            }
            return (pagesFree + pagesInactive + pagesSpeculative) * pageSize;
        } catch (Exception ignored) {}
        return -1;
    }

    private static long vmStatLong(String line) {
        String num = line.substring(line.lastIndexOf(' ') + 1).replace(".", "").trim();
        try { return Long.parseLong(num); } catch (Exception e) { return 0; }
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
