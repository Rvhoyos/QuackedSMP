package mc.smpessentials.dashboard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.smpessentials.backup.BackupZipper;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.dims.DimManager;
import mc.smpessentials.timelapse.TimelapseFrameStore;
import mc.smpessentials.timelapse.TimelapseService;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static mc.smpessentials.dashboard.AdminHandler.err;
import static mc.smpessentials.dashboard.AdminHandler.jsonEscape;
import static mc.smpessentials.dashboard.AdminHandler.queryParam;

/**
 * HTTP routes for world-map timelapse frames. Frames are stored per dimension in
 * parallel folders, so most routes take a {@code dim} id (query param or body
 * field) to pick the folder; {@link #handleList} reports every dimension that
 * has frames plus the full set of dimensions available to opt into.
 */
public final class TimelapseHandler {

    private TimelapseHandler() {}

    /** GET /api/admin/timelapse. Per-dimension frame lists, opt-in universe, sizes, flags. */
    public static String handleList(String method, Map<String, String> headers, String body,
                                    MinecraftServer server) {
        if (!"GET".equals(method))            return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return err(403, "Unauthorized");
        if (server == null)                   return err(503, "Server not ready");

        List<ResourceKey<Level>> all = DimManager.listAll(server);

        StringBuilder sb = new StringBuilder("{\"enabled\":");
        sb.append(SmpConfig.TIMELAPSE_ENABLED);
        sb.append(",\"running\":").append(TimelapseService.get().isRunning());
        sb.append(",\"intervalMinutes\":").append(SmpConfig.TIMELAPSE_INTERVAL_MINUTES);
        sb.append(",\"serverMaxHeap\":").append(Runtime.getRuntime().maxMemory());
        sb.append(",\"progress\":").append(TimelapseService.get().progressJson());

        // The opt-in universe: every dimension the server currently has loaded.
        sb.append(",\"available\":[");
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(jsonEscape(all.get(i).identifier().toString())).append('"');
        }
        sb.append(']');

        // Dimensions that actually have frames on disk, each with its frames and size.
        long total = 0;
        StringBuilder dims = new StringBuilder();
        int written = 0;
        for (ResourceKey<Level> key : all) {
            String id = key.identifier().toString();
            List<TimelapseFrameStore.Frame> frames = TimelapseFrameStore.forDimension(id).list();
            if (frames.isEmpty()) continue;
            long size = 0;
            for (TimelapseFrameStore.Frame f : frames) size += f.sizeBytes();
            total += size;
            if (written++ > 0) dims.append(',');
            dims.append("{\"id\":\"").append(jsonEscape(id))
                .append("\",\"sizeBytes\":").append(size).append(",\"frames\":[");
            for (int i = 0; i < frames.size(); i++) {
                if (i > 0) dims.append(',');
                appendFrame(dims, frames.get(i));
            }
            dims.append("]}");
        }
        sb.append(",\"dims\":[").append(dims).append(']');
        sb.append(",\"totalSizeBytes\":").append(total);
        sb.append('}');
        return sb.toString();
    }

    /** POST /api/admin/timelapse/capture. Renders and stores one frame per opted-in dimension. */
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

    /** POST /api/admin/timelapse/delete. Body: {@code {"dim":"...","name":"frame-<millis>.png"}}. */
    public static String handleDelete(String method, Map<String, String> headers, String body) {
        if (!"POST".equals(method))           return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return err(403, "Unauthorized");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String dim = req.get("dim").getAsString();
            if (!validDim(dim)) return err(400, "Invalid dimension");
            TimelapseFrameStore.forDimension(dim).delete(req.get("name").getAsString());
            return "{\"ok\":true}";
        } catch (IllegalArgumentException e) {
            return err(400, "Invalid frame name");
        } catch (Exception e) {
            return err(400, "Invalid request");
        }
    }

    /** GET /api/admin/timelapse/frame?dim=...&name=... Streams one frame PNG. */
    public static DashboardServer.DownloadResult handleFrame(String method, Map<String, String> headers) {
        if (!"GET".equals(method))            return new DashboardServer.DownloadResult.Error(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return new DashboardServer.DownloadResult.Error(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return new DashboardServer.DownloadResult.Error(403, "Unauthorized");
        String qs   = headers.getOrDefault("x-query-string", "");
        String dim  = queryParam(qs, "dim");
        String name = queryParam(qs, "name");
        if (name.isEmpty())  return new DashboardServer.DownloadResult.Error(400, "Missing name");
        if (!validDim(dim))  return new DashboardServer.DownloadResult.Error(400, "Invalid dimension");
        try {
            Path file = TimelapseFrameStore.forDimension(dim).pathOf(name);
            if (!Files.exists(file)) return new DashboardServer.DownloadResult.Error(404, "Frame not found");
            return new DashboardServer.DownloadResult.File("image/png", name, file);
        } catch (IllegalArgumentException e) {
            return new DashboardServer.DownloadResult.Error(400, "Invalid frame name");
        }
    }

    /** GET /api/admin/timelapse/export?dim=... Zips one dimension's frames for offline assembly. */
    public static DashboardServer.DownloadResult handleExport(String method, Map<String, String> headers) {
        if (!"GET".equals(method))            return new DashboardServer.DownloadResult.Error(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return new DashboardServer.DownloadResult.Error(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return new DashboardServer.DownloadResult.Error(403, "Unauthorized");
        String dim = queryParam(headers.getOrDefault("x-query-string", ""), "dim");
        if (!validDim(dim)) return new DashboardServer.DownloadResult.Error(400, "Invalid dimension");

        TimelapseFrameStore store = TimelapseFrameStore.forDimension(dim);
        if (store.list().isEmpty()) return new DashboardServer.DownloadResult.Error(404, "No frames to export");
        try {
            Path zip = Files.createTempFile("timelapse-export-", ".zip");
            BackupZipper.zip(store.dir(), zip);
            String fileName = "timelapse-" + store.dir().getFileName() + ".zip";
            return new DashboardServer.DownloadResult.File("application/zip", fileName, zip,
                    () -> { try { Files.deleteIfExists(zip); } catch (IOException ignored) {} });
        } catch (IOException e) {
            return new DashboardServer.DownloadResult.Error(500, "Export failed");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void appendFrame(StringBuilder sb, TimelapseFrameStore.Frame f) {
        sb.append(String.format(Locale.US,
                "{\"name\":\"%s\",\"capturedAt\":%d,\"sizeBytes\":%d,\"blocksPerPixel\":%d}",
                jsonEscape(f.name()), f.capturedAt(), f.sizeBytes(), f.blocksPerPixel()));
    }

    // Accepts only a well-formed dimension id (namespace:path). The store also
    // sanitizes the folder name, so this is a clean-error guard, not the only defense.
    private static boolean validDim(String dim) {
        return dim != null && dim.matches("[a-z0-9_.-]+:[a-z0-9/._-]+");
    }
}
