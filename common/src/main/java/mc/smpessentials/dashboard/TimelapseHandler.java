package mc.smpessentials.dashboard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.smpessentials.SmpUtilsMod;
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
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
            // The panel renders frames in <img> tags, which cannot carry an auth
            // header, so each dimension ships a token its frame URLs can use. Minted
            // here rather than on its own route so selecting a dimension costs no
            // extra request; a list refresh renews it.
            dims.append("{\"id\":\"").append(jsonEscape(id))
                .append("\",\"sizeBytes\":").append(size)
                .append(",\"frameToken\":\"").append(AdminAuth.mintScopeToken(frameScope(id)))
                .append("\",\"frames\":[");
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
        if (!SmpConfig.TIMELAPSE_ENABLED)     return err(409, "Timelapse is disabled");
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

    /**
     * GET /api/admin/timelapse/frame?dim=...&amp;name=...&amp;t=... Streams one frame PNG.
     * Authorised by the usual bearer header or by the dimension's frame token, since
     * the panel loads these through {@code <img>}. Served cacheable and inline: a
     * frame's name already identifies its bytes, so it can never go stale.
     */
    public static DashboardServer.DownloadResult handleFrame(String method, Map<String, String> headers) {
        if (!"GET".equals(method))    return new DashboardServer.DownloadResult.Error(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED) return new DashboardServer.DownloadResult.Error(403, "Admin panel disabled");
        String qs   = headers.getOrDefault("x-query-string", "");
        String dim  = queryParam(qs, "dim");
        String name = queryParam(qs, "name");
        if (name.isEmpty()) return new DashboardServer.DownloadResult.Error(400, "Missing name");
        if (!validDim(dim)) return new DashboardServer.DownloadResult.Error(400, "Invalid dimension");
        if (!AdminAuth.isAuthorized(headers)
                && !AdminAuth.isValidScopeToken(frameScope(dim), queryParam(qs, "t"))) {
            return new DashboardServer.DownloadResult.Error(403, "Unauthorized");
        }
        try {
            Path file = TimelapseFrameStore.forDimension(dim).pathOf(name);
            if (!Files.exists(file)) return new DashboardServer.DownloadResult.Error(404, "Frame not found");
            String etag = "\"" + name + "-" + Files.size(file) + "\"";
            return new DashboardServer.DownloadResult.File("image/png", name, file,
                    DashboardServer.Delivery.cacheableInline(etag), () -> {});
        } catch (IllegalArgumentException e) {
            return new DashboardServer.DownloadResult.Error(400, "Invalid frame name");
        } catch (IOException e) {
            return new DashboardServer.DownloadResult.Error(500, "Could not read frame");
        }
    }

    /**
     * GET /api/admin/timelapse/export?dim=... Streams one dimension's frames as a zip.
     * Written straight to the socket: building the archive up front made the browser
     * wait for the whole zip before receiving a single byte, which read as a hang.
     */
    public static DashboardServer.DownloadResult handleExport(String method, Map<String, String> headers) {
        if (!"GET".equals(method))            return new DashboardServer.DownloadResult.Error(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return new DashboardServer.DownloadResult.Error(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return new DashboardServer.DownloadResult.Error(403, "Unauthorized");
        String dim = queryParam(headers.getOrDefault("x-query-string", ""), "dim");
        if (!validDim(dim)) return new DashboardServer.DownloadResult.Error(400, "Invalid dimension");

        TimelapseFrameStore store = TimelapseFrameStore.forDimension(dim);
        List<TimelapseFrameStore.Frame> frames = store.list();
        if (frames.isEmpty()) return new DashboardServer.DownloadResult.Error(404, "No frames to export");

        String folder = store.dir().getFileName().toString();
        return new DashboardServer.DownloadResult.Streamed("application/zip",
                "timelapse-" + folder + ".zip",
                out -> writeFrameZip(store, frames, folder, out));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    // Frames are PNG, so their bytes are already DEFLATE compressed: re-compressing
    // them measured 1.9% smaller for roughly six times the CPU. Level 0 stores them
    // as-is without the second pass over every file that a STORED entry's precomputed
    // CRC would cost.
    private static void writeFrameZip(TimelapseFrameStore store, List<TimelapseFrameStore.Frame> frames,
                                      String folder, java.io.OutputStream out) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.setLevel(Deflater.NO_COMPRESSION);
            for (TimelapseFrameStore.Frame frame : frames) {
                Path file = store.pathOf(frame.name());
                // A frame deleted between listing and reading is skipped, not fatal.
                if (!Files.exists(file)) {
                    SmpUtilsMod.LOGGER.warn("[Timelapse] Export skipped {}, no longer on disk.", frame.name());
                    continue;
                }
                ZipEntry entry = new ZipEntry(folder + "/" + frame.name());
                entry.setTime(frame.capturedAt());
                zip.putNextEntry(entry);
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
    }

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

    // Scope a frame token to one dimension, so a leaked frame URL reaches only that
    // dimension's frames and nothing else on the panel.
    private static String frameScope(String dimId) {
        return "timelapse-frames:" + dimId;
    }
}
