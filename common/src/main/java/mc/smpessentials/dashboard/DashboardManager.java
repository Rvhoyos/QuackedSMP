package mc.smpessentials.dashboard;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the dashboard server lifecycle and event broadcasting.
 *
 * <p>HTTP and WebSocket share a single port via {@link DashboardServer}.
 * The dashboard starts if {@code dashboard.enabled = true} in config.
 * Spark is optional — detected at startup, metrics endpoints degrade gracefully if absent.
 */
public final class DashboardManager {
    private DashboardManager() {}

    private static volatile DashboardServer server;
    private static volatile ScheduledExecutorService scheduler;
    private static volatile MinecraftServer mcServer;
    private static volatile boolean running = false;
    private static boolean sparkLoaded = false;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    public static void init() {
        try {
            Class.forName("me.lucko.spark.api.SparkProvider");
            sparkLoaded = true;
            SmpUtilsMod.LOGGER.info("[Dashboard] Spark API detected.");
        } catch (ClassNotFoundException e) {
            SmpUtilsMod.LOGGER.info("[Dashboard] Spark not found — /api/spark/* will return unavailable.");
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
            return String.format(
                    "{\"status\":\"ok\",\"online\":%d,\"adminEnabled\":%b,\"hasPassword\":%b}",
                    online, adminOn, hasPassword);
        });
        s.addRoute("/api/spark/tps",  SparkMetrics::getTpsJson);
        s.addRoute("/api/spark/cpu",  SparkMetrics::getCpuJson);
        s.addRoute("/api/spark/tick", SparkMetrics::getMsptJson);

        // Admin endpoints
        s.addRoute("/api/admin/status", AdminHandler::handleStatus);
        s.addRoute("/api/admin/setup",  AdminHandler::handleSetup);
        s.addRoute("/api/admin/login",  AdminHandler::handleLogin);
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

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
