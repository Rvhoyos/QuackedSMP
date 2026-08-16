package mc.smpessentials.dashboard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.smpessentials.backup.BackupZipper;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.timelapse.TimelapseFrameStore;
import mc.smpessentials.timelapse.TimelapseService;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static mc.smpessentials.dashboard.AdminHandler.err;
import static mc.smpessentials.dashboard.AdminHandler.jsonEscape;
import static mc.smpessentials.dashboard.AdminHandler.queryParam;

/** HTTP routes for world-map timelapse frames. Mirrors {@link BackupHandler} structure. */
public final class TimelapseHandler {

    private TimelapseHandler() {}

    /** GET /api/admin/timelapse. Lists stored frames plus the enabled/running flags. */
    public static String handleList(String method, Map<String, String> headers, String body) {
        if (!"GET".equals(method))            return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return err(403, "Unauthorized");

        List<TimelapseFrameStore.Frame> frames = TimelapseService.get().store().list();
        StringBuilder sb = new StringBuilder("{\"enabled\":");
        sb.append(SmpConfig.TIMELAPSE_ENABLED);
        sb.append(",\"running\":").append(TimelapseService.get().isRunning());
        sb.append(",\"intervalMinutes\":").append(SmpConfig.TIMELAPSE_INTERVAL_MINUTES);
        sb.append(",\"maxDimension\":").append(SmpConfig.TIMELAPSE_MAX_DIMENSION);
        sb.append(",\"serverMaxHeap\":").append(Runtime.getRuntime().maxMemory());
        sb.append(",\"frames\":[");
        for (int i = 0; i < frames.size(); i++) {
            TimelapseFrameStore.Frame f = frames.get(i);
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.US,
                    "{\"name\":\"%s\",\"capturedAt\":%d,\"sizeBytes\":%d}",
                    jsonEscape(f.name()), f.capturedAt(), f.sizeBytes()));
        }
        sb.append("]}");
        return sb.toString();
    }

    /** POST /api/admin/timelapse/capture. Renders and stores one frame now. */
    public static String handleCapture(String method, Map<String, String> headers, String body,
                                       MinecraftServer server) {
        if (!"POST".equals(method))           return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return err(403, "Unauthorized");
        if (server == null)                   return err(503, "Server not ready");
        if (TimelapseService.get().isRunning()) return err(409, "Capture already in progress");
        TimelapseService.get().capture(server);
        return "{\"ok\":true}";
    }

    /** POST /api/admin/timelapse/delete. Body: {@code {"name":"frame-<millis>.png"}}. */
    public static String handleDelete(String method, Map<String, String> headers, String body) {
        if (!"POST".equals(method))           return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return err(403, "Unauthorized");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            TimelapseService.get().store().delete(req.get("name").getAsString());
            return "{\"ok\":true}";
        } catch (IllegalArgumentException e) {
            return err(400, "Invalid frame name");
        } catch (Exception e) {
            return err(400, "Invalid request");
        }
    }

    /** GET /api/admin/timelapse/frame?name=... Streams one frame PNG. */
    public static DashboardServer.DownloadResult handleFrame(String method, Map<String, String> headers) {
        if (!"GET".equals(method))            return new DashboardServer.DownloadResult.Error(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return new DashboardServer.DownloadResult.Error(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return new DashboardServer.DownloadResult.Error(403, "Unauthorized");
        String name = queryParam(headers.getOrDefault("x-query-string", ""), "name");
        if (name.isEmpty()) return new DashboardServer.DownloadResult.Error(400, "Missing name");
        try {
            Path file = TimelapseService.get().store().pathOf(name);
            if (!Files.exists(file)) return new DashboardServer.DownloadResult.Error(404, "Frame not found");
            return new DashboardServer.DownloadResult.File("image/png", name, file);
        } catch (IllegalArgumentException e) {
            return new DashboardServer.DownloadResult.Error(400, "Invalid frame name");
        }
    }

    /** GET /api/admin/timelapse/export. Zips all frames for offline video assembly. */
    public static DashboardServer.DownloadResult handleExport(String method, Map<String, String> headers) {
        if (!"GET".equals(method))            return new DashboardServer.DownloadResult.Error(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return new DashboardServer.DownloadResult.Error(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return new DashboardServer.DownloadResult.Error(403, "Unauthorized");

        TimelapseFrameStore store = TimelapseService.get().store();
        if (store.list().isEmpty()) return new DashboardServer.DownloadResult.Error(404, "No frames to export");
        try {
            Path zip = Files.createTempFile("timelapse-export-", ".zip");
            BackupZipper.zip(store.dir(), zip);
            return new DashboardServer.DownloadResult.File("application/zip", "timelapse-frames.zip", zip,
                    () -> { try { Files.deleteIfExists(zip); } catch (IOException ignored) {} });
        } catch (IOException e) {
            return new DashboardServer.DownloadResult.Error(500, "Export failed");
        }
    }
}
