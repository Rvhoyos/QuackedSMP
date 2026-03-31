package mc.smpessentials.dashboard;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the dashboard server lifecycle and event broadcasting.
 * HTTP and WebSocket share a single port via {@link DashboardServer}.
 * Spark is optional; metrics endpoints degrade gracefully if absent.
 */
public final class DashboardManager {
    private DashboardManager() {}

    private static volatile DashboardServer server;
    private static volatile ScheduledExecutorService scheduler;
    private static volatile MinecraftServer mcServer;
    private static volatile boolean running = false;
    private static boolean sparkLoaded = false;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /** Detects Spark at startup. Call once before {@link #onServerStart}. */
    public static void init() {
        try {
            Class.forName("me.lucko.spark.api.SparkProvider");
            sparkLoaded = true;
            SmpUtilsMod.LOGGER.info("[Dashboard] Spark API detected.");
        } catch (ClassNotFoundException e) {
            SmpUtilsMod.LOGGER.info("[Dashboard] Spark not found — /api/spark/* will return unavailable.");
        }
    }

    /** Starts the dashboard HTTP/WebSocket server if enabled in config. */
    public static void onServerStart(MinecraftServer srv) {
        mcServer = srv;
        // Migrate any legacy player_tiers from JSON config into NBT SavedData
        mc.smpessentials.tier.PlayerTierData.get(srv)
                .loadPendingMigration(mc.smpessentials.config.ConfigIO.pendingTierMigration);
        if (!SmpConfig.DASHBOARD_ENABLED) {
            SmpUtilsMod.LOGGER.info("[Dashboard] Disabled in config.");
            return;
        }
        start(SmpConfig.DASHBOARD_PORT);
    }

    /** Stops the dashboard server and clears the server reference. */
    public static void onServerStop() {
        stop();
        mcServer = null;
    }

    /**
     * Enables the dashboard: saves {@code dashboard_enabled=true} to config and
     * starts the webserver if it is not already running.
     * Called from {@code /smp admin setpassword}.
     */
    public static void scheduleEnable() {
        if (running) return;
        SmpConfig.DASHBOARD_ENABLED = true;
        mc.smpessentials.config.ConfigIO.save();
        if (mcServer != null) start(SmpConfig.DASHBOARD_PORT);
    }

    /**
     * Disables the dashboard: saves {@code dashboard_enabled=false} to config,
     * then stops the server on a daemon thread after a short delay so the
     * in-flight HTTP response can be sent first.
     */
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
        }

        running = true;
        SmpUtilsMod.LOGGER.info("[Dashboard] Started on port {}", port);
    }

    private static void stop() {
        running = false;
        ScheduledExecutorService sc = scheduler;
        if (sc != null) { sc.shutdown(); scheduler = null; }
        DashboardServer s = server;
        if (s != null) { s.shutdown(); server = null; }
        SmpUtilsMod.LOGGER.info("[Dashboard] Stopped.");
    }

    // ── Route registration ─────────────────────────────────────────────────────

    private static void registerRoutes(DashboardServer s) {
        s.addRoute("/api/health", () -> {
            int     online      = mcServer != null ? mcServer.getPlayerList().getPlayerCount() : 0;
            boolean adminOn     = SmpConfig.ADMIN_ENABLED;
            boolean hasPassword = !SmpConfig.ADMIN_PASSWORD_HASH.isBlank();
            String  serverName  = SmpConfig.SERVER_NAME;
            return String.format(
                    "{\"status\":\"ok\",\"online\":%d,\"adminEnabled\":%b,\"hasPassword\":%b,\"serverName\":\"%s\"}",
                    online, adminOn, hasPassword, jsonEscape(serverName));
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
            return String.format(Locale.US,
                    "{\"heapUsed\":%d,\"heapMax\":%d,\"ramTotal\":%d,\"ramFree\":%d,\"diskTotal\":%d,\"diskUsable\":%d,\"uptimeMs\":%d,\"threads\":%d}",
                    heapUsed, heapMax, ramTotal, ramFree, diskTotal, diskUsable, uptimeMs, threads);
        });
        // Spark routes — only reference SparkMetrics if Spark is on the classpath.
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
        s.addRoute("/api/admin/config",
                (m, h, b) -> "GET".equals(m)
                        ? AdminHandler.handleConfigGet(m, h, b)
                        : AdminHandler.handleConfigPost(m, h, b));
        s.addRoute("/api/admin/setop",
                (m, h, b) -> AdminHandler.handleSetOp(m, h, b, mcServer));
        s.addRoute("/api/admin/players/settier",
                (m, h, b) -> AdminHandler.handleSetTier(m, h, b, mcServer));
        s.addRoute("/api/admin/dims",
                (m, h, b) -> AdminHandler.handleDimsGet(m, h, b, mcServer));
        s.addRoute("/api/admin/dims/create",
                (m, h, b) -> AdminHandler.handleDimCreate(m, h, b, mcServer));
        s.addRoute("/api/admin/dims/delete",
                (m, h, b) -> AdminHandler.handleDimDelete(m, h, b, mcServer));
        s.addRoute("/api/admin/dims/setportal",
                (m, h, b) -> AdminHandler.handleDimSetPortal(m, h, b, mcServer));
        s.addRoute("/api/admin/blocks",
                (m, h, b) -> AdminHandler.handleBlocksGet(m, h, b));
        s.addRoute("/api/admin/biomes",
                (m, h, b) -> AdminHandler.handleBiomesGet(m, h, b, mcServer));

        // Skills leaderboard (public)
        s.addRoute("/api/skills/leaderboard",
                (m, h, b) -> AdminHandler.handleSkillsLeaderboard(m, h, b, mcServer));

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

    // ── Event broadcast ────────────────────────────────────────────────────────

    /** Broadcasts a player_join event to WebSocket clients and forwards to Discord. */
    public static void broadcastPlayerJoin(String playerName) {
        broadcast(String.format(Locale.US,
                "{\"type\":\"player_join\",\"player\":\"%s\",\"timestamp\":%d}",
                jsonEscape(playerName), System.currentTimeMillis()));
        DiscordWebhook.sendJoin(playerName);
    }

    /** Broadcasts a player_leave event to WebSocket clients and forwards to Discord. */
    public static void broadcastPlayerLeave(String playerName) {
        broadcast(String.format(Locale.US,
                "{\"type\":\"player_leave\",\"player\":\"%s\",\"timestamp\":%d}",
                jsonEscape(playerName), System.currentTimeMillis()));
        DiscordWebhook.sendLeave(playerName);
    }

    /** Broadcasts a chat event to WebSocket clients and forwards to Discord. */
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

    /**
     * Returns the best available free-memory figure for the current platform.
     * Linux:  reads MemAvailable from /proc/meminfo (includes reclaimable page cache).
     * macOS:  parses vm_stat output (free + inactive + speculative pages).
     * Other:  falls back to OperatingSystemMXBean.getFreeMemorySize().
     */
    private static long readPlatformFreeMemory(com.sun.management.OperatingSystemMXBean os) {
        long memAvail = readMemAvailable();
        if (memAvail > 0) return memAvail;
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            long vmFree = readVmStat();
            if (vmFree > 0) return vmFree;
        }
        return os.getFreeMemorySize();
    }

    /** Reads MemAvailable from /proc/meminfo (Linux only). Returns -1 if unavailable. */
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

    /**
     * Parses vm_stat output on macOS to get available memory.
     * Available = (free + inactive + speculative) * page_size.
     * Returns -1 if parsing fails.
     */
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

    /** Extracts trailing integer from a vm_stat line like "Pages free:  1234." */
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
