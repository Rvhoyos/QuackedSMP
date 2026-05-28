package mc.smpessentials.dashboard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.smpessentials.backup.BackupService;
import mc.smpessentials.backup.PublicDownloadLimiter;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static mc.smpessentials.dashboard.AdminHandler.err;
import static mc.smpessentials.dashboard.AdminHandler.jsonEscape;
import static mc.smpessentials.dashboard.AdminHandler.queryParam;

/** HTTP routes for world snapshot management. Mirrors {@link AdminHandler} structure. */
public final class BackupHandler {

    private BackupHandler() {}

    /** GET /api/admin/backups. Returns the list of stored snapshots and the in-progress flag. */
    public static String handleList(String method, Map<String, String> headers, String body) {
        if (!"GET".equals(method))            return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return err(403, "Unauthorized");

        List<BackupService.Snapshot> snapshots = BackupService.get().list();
        StringBuilder sb = new StringBuilder("{\"running\":");
        sb.append(BackupService.get().isRunning());
        sb.append(",\"snapshots\":[");
        for (int i = 0; i < snapshots.size(); i++) {
            BackupService.Snapshot s = snapshots.get(i);
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.US,
                    "{\"name\":\"%s\",\"sizeBytes\":%d,\"createdAt\":%d}",
                    jsonEscape(s.name()), s.sizeBytes(), s.createdAt()));
        }
        sb.append("]}");
        return sb.toString();
    }

    /** POST /api/admin/backups/create. Kicks off a snapshot; returns immediately. */
    public static String handleCreate(String method, Map<String, String> headers, String body,
                                       MinecraftServer server) {
        if (!"POST".equals(method))           return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return err(403, "Unauthorized");
        if (server == null)                   return err(503, "Server not ready");
        try {
            BackupService.get().create(server);
            return "{\"ok\":true}";
        } catch (BackupService.BusyException e) {
            return err(409, "Backup already in progress");
        } catch (Exception e) {
            return err(500, jsonEscape(e.getMessage()));
        }
    }

    /** POST /api/admin/backups/delete. Body: {@code {"name":"world-YYYYMMDD-HHmmss.zip"}}. */
    public static String handleDelete(String method, Map<String, String> headers, String body) {
        if (!"POST".equals(method))           return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return err(403, "Unauthorized");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String name = req.get("name").getAsString();
            BackupService.get().delete(name);
            return "{\"ok\":true}";
        } catch (BackupService.InvalidNameException e) {
            return err(400, "Invalid backup name");
        } catch (java.nio.file.NoSuchFileException e) {
            return err(404, "Snapshot not found");
        } catch (Exception e) {
            return err(400, "Invalid request");
        }
    }

    /**
     * GET /api/backups/latest/download. Public route gated by
     * {@link SmpConfig#BACKUP_PUBLIC_DOWNLOAD}. Streams the newest snapshot,
     * with per-IP and global concurrency caps enforced by {@link PublicDownloadLimiter}.
     */
    public static DashboardServer.DownloadResult handleLatestPublic(
            String method, Map<String, String> headers, MinecraftServer server) {
        if (!"GET".equals(method))             return new DashboardServer.DownloadResult.Error(405, "Method not allowed");
        if (!SmpConfig.BACKUP_PUBLIC_DOWNLOAD) return new DashboardServer.DownloadResult.Error(403, "Public downloads disabled");

        List<BackupService.Snapshot> all = BackupService.get().list();
        if (all.isEmpty()) return new DashboardServer.DownloadResult.Error(404, "No snapshots available");
        String name = all.get(0).name();

        Path file;
        try {
            file = BackupService.get().pathOf(name);
        } catch (IOException e) {
            return new DashboardServer.DownloadResult.Error(500, "Read failed");
        }

        String ip       = resolveIp(headers);
        int globalCap   = resolveGlobalCap(server);
        int perIpCap    = Math.max(1, SmpConfig.BACKUP_PUBLIC_MAX_PER_IP);

        if (!PublicDownloadLimiter.get().tryAcquire(ip, perIpCap, globalCap)) {
            return new DashboardServer.DownloadResult.Error(429, "Too many concurrent downloads, try again shortly");
        }

        return new DashboardServer.DownloadResult.File(
                "application/zip", name, file,
                () -> PublicDownloadLimiter.get().release(ip));
    }

    /** Prefer the proxy-supplied client IP; fall back to the direct socket peer. */
    private static String resolveIp(Map<String, String> headers) {
        String xff = headers.getOrDefault("x-forwarded-for", "");
        String first = xff.isEmpty() ? "" : xff.split(",")[0].trim();
        if (!first.isEmpty()) return first;
        return headers.getOrDefault("x-remote-ip", "unknown");
    }

    // MinecraftServer.getMaxPlayers() is the vanilla base-class method backed by
    // `max-players` in server.properties (delegates to playerList.getMaxPlayers()).
    // Identical on Fabric and NeoForge; same category as the other vanilla
    // MinecraftServer.* calls the common module already makes.
    private static int resolveGlobalCap(MinecraftServer server) {
        int cfg = SmpConfig.BACKUP_PUBLIC_MAX_CONCURRENT;
        if (cfg > 0) return cfg;
        return server != null ? server.getMaxPlayers() : 20;
    }

    /** GET /api/admin/backups/download?name=... Streams the zip body to the client. */
    public static DashboardServer.DownloadResult handleDownload(String method, Map<String, String> headers) {
        if (!"GET".equals(method))            return new DashboardServer.DownloadResult.Error(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return new DashboardServer.DownloadResult.Error(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return new DashboardServer.DownloadResult.Error(403, "Unauthorized");
        String name = queryParam(headers.getOrDefault("x-query-string", ""), "name");
        if (name.isEmpty()) return new DashboardServer.DownloadResult.Error(400, "Missing name");
        try {
            Path file = BackupService.get().pathOf(name);
            return new DashboardServer.DownloadResult.File("application/zip", name, file);
        } catch (BackupService.InvalidNameException e) {
            return new DashboardServer.DownloadResult.Error(400, "Invalid backup name");
        } catch (java.nio.file.NoSuchFileException e) {
            return new DashboardServer.DownloadResult.Error(404, "Snapshot not found");
        } catch (IOException e) {
            return new DashboardServer.DownloadResult.Error(500, "Read failed");
        }
    }
}
