package mc.smpessentials.dashboard;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.smpessentials.config.ConfigIO;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.pregen.PregenLimits;
import mc.smpessentials.pregen.PregenReport;
import mc.smpessentials.pregen.PregenRunner;
import mc.smpessentials.regen.ChunkRegenManager;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static mc.smpessentials.dashboard.AdminHandler.err;
import static mc.smpessentials.dashboard.AdminHandler.jsonEscape;

/**
 * HTTP routes for chunk pre-generation. Unlike the flat config payload, this one carries what only
 * the running server knows: the radius each dimension actually resolved to after the spawn radius,
 * its coordinate scale and its world border, plus progress and free disk.
 */
public final class PregenHandler {

    private PregenHandler() {}

    // Largest coordinate vanilla will accept, so nothing beyond this can describe a real area.
    // Read as a long and range-checked rather than through getAsInt, which silently overflows an
    // oversized number into a small or negative one and would save a distance nobody asked for.
    private static final long MAX_DISTANCE = 29_999_984L;

    /** GET /api/admin/pregen. Config plus the live per-dimension picture. */
    public static String handleGet(String method, Map<String, String> headers, String body,
                                   MinecraftServer server) {
        String denied = ItemHandler.deny(method, "GET", headers, server);
        if (denied != null) return denied;
        return snapshot(server, SmpConfig.PREGEN_DISTANCE, SmpConfig.PREGEN_DIMENSIONS, null);
    }

    /** POST /api/admin/pregen/preview. The same picture for a distance, without saving it. */
    public static String handlePreview(String method, Map<String, String> headers, String body,
                                       MinecraftServer server) {
        String denied = ItemHandler.deny(method, "POST", headers, server);
        if (denied != null) return denied;

        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            List<String> dims = readDimensions(req);
            long distance = readDistance(req);
            // A number being out of range is a verdict to show, not a failed request: the operator
            // is still typing and blanking the panel mid-keystroke helps nobody.
            if (distance < 1L || distance > MAX_DISTANCE) {
                return snapshot(server, 0, dims, distanceProblem(distance));
            }
            return snapshot(server, (int) distance, dims,
                    PregenLimits.reject(server, (int) distance, dims).orElse(null));
        } catch (RuntimeException e) {
            return err(400, "Invalid pregen settings: " + e.getMessage());
        }
    }

    /** POST /api/admin/pregen/save. Writes the area, then quackedsmp.json. */
    public static String handleSave(String method, Map<String, String> headers, String body,
                                    MinecraftServer server) {
        String denied = ItemHandler.deny(method, "POST", headers, server);
        if (denied != null) return denied;

        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();

            boolean enabled = req.has("enabled")
                    ? req.get("enabled").getAsBoolean()
                    : SmpConfig.PREGEN_ENABLED;
            List<String> dims = readDimensions(req);
            long distance = readDistance(req);

            // Every check runs before the first assignment, so a rejected save leaves the running
            // config exactly as it was rather than half applied and out of step with the file.
            //
            // A queued regen deletes wilderness on the next shutdown that the startup after it
            // would immediately rebuild, so the two are never on together.
            if (enabled && !SmpConfig.PREGEN_ENABLED && ChunkRegenManager.isPending(server)) {
                return err(409, "A wilderness regen is queued. Cancel it before enabling pre-generation.");
            }
            if (distance < 1L || distance > MAX_DISTANCE) return err(400, distanceProblem(distance));

            // Checked against the border and against free disk too, so a rejected number is never
            // silently reinterpreted into a smaller one.
            Optional<String> refusal = PregenLimits.reject(server, (int) distance, dims);
            if (refusal.isPresent()) return err(409, refusal.get());

            SmpConfig.PREGEN_ENABLED = enabled;
            SmpConfig.PREGEN_DISTANCE = (int) distance;
            SmpConfig.PREGEN_DIMENSIONS = dims;
            ConfigIO.save();
            return snapshot(server, SmpConfig.PREGEN_DISTANCE, SmpConfig.PREGEN_DIMENSIONS, null);
        } catch (RuntimeException e) {
            return err(400, "Invalid pregen settings: " + e.getMessage());
        }
    }

    private static List<String> readDimensions(JsonObject req) {
        if (!req.has("dimensions") || !req.get("dimensions").isJsonArray()) {
            return SmpConfig.PREGEN_DIMENSIONS;
        }
        JsonArray arr = req.getAsJsonArray("dimensions");
        List<String> dims = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            String id = arr.get(i).getAsString().trim();
            if (!id.isEmpty() && !dims.contains(id)) dims.add(id);
        }
        return dims;
    }

    /** The requested distance as a long, so an oversized number stays oversized instead of wrapping. */
    private static long readDistance(JsonObject req) {
        if (!req.has("distance")) return SmpConfig.PREGEN_DISTANCE;
        try {
            return req.get("distance").getAsLong();
        } catch (RuntimeException e) {
            return Long.MIN_VALUE;
        }
    }

    private static String distanceProblem(long distance) {
        if (distance > MAX_DISTANCE) {
            return String.format(Locale.US, "Distance must be %,d or less.", MAX_DISTANCE);
        }
        return "Distance must be at least 1 block.";
    }

    /**
     * The whole panel payload. Radius, chunk counts and the estimate are resolved server-side so
     * the panel never has to know about spawn radii, coordinate scales or world borders.
     *
     * @param refusal why these settings would be rejected, or null when they are acceptable. The
     *                panel shows it while the operator is still typing, so a number is turned down
     *                on screen rather than only when they press Save.
     */
    private static String snapshot(MinecraftServer server, int distance, List<String> dimensions,
                                   String refusal) {
        List<PregenReport> reports = PregenReport.forSettings(server, distance, dimensions);

        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append(String.format("\"enabled\":%b,", SmpConfig.PREGEN_ENABLED));
        sb.append(String.format("\"distance\":%d,", distance));
        sb.append(String.format("\"refusal\":%s,",
                refusal == null ? "null" : "\"" + jsonEscape(refusal) + "\""));
        sb.append(String.format("\"restart_required\":%b,", PregenReport.restartRequired(reports)));
        sb.append(String.format("\"regen_pending\":%b,", ChunkRegenManager.isPending(server)));
        long free = PregenRunner.freeSpaceBytes(server);
        long needed = 0L;
        for (PregenReport r : reports) needed += r.estimate().bytes();
        sb.append(String.format(Locale.US, "\"free_bytes\":%d,", free));
        sb.append(String.format("\"disk_warning\":%b,", PregenLimits.warns(needed, free)));

        // Coordinate scale of every dimension on the server, not just the configured ones, so the
        // picker can say what a scaled dimension will actually do before it is added.
        sb.append("\"scales\":{");
        boolean firstScale = true;
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            if (!firstScale) sb.append(',');
            firstScale = false;
            sb.append(String.format(Locale.US, "\"%s\":%s",
                    jsonEscape(level.dimension().identifier().toString()),
                    Double.toString(level.dimensionType().coordinateScale())));
        }
        sb.append("},");

        sb.append("\"dimensions\":[");
        for (int i = 0; i < reports.size(); i++) {
            PregenReport r = reports.get(i);
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.US,
                    "{\"id\":\"%s\",\"present\":%b,\"radius_blocks\":%d,\"chunks\":%d,\"done\":%d,"
                            + "\"complete\":%b,\"bytes\":%d,\"seconds\":%d}",
                    jsonEscape(r.dimension()), r.present(), r.radiusBlocks(), r.chunks(), r.done(),
                    r.complete(), r.estimate().bytes(), r.estimate().seconds()));
        }
        sb.append("]}");
        return sb.toString();
    }
}
