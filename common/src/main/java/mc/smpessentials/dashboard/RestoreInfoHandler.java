package mc.smpessentials.dashboard;

import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;

import java.util.Locale;
import java.util.Map;

import static mc.smpessentials.dashboard.AdminHandler.err;
import static mc.smpessentials.dashboard.AdminHandler.jsonEscape;

// HTTP surface for RestoreInfo. Owns the wire shape (jsonFields) so the admin route and
// the public /api/health route serialize identically. Mirrors BackupHandler structure.
public final class RestoreInfoHandler {

    private RestoreInfoHandler() {}

    /**
     * Builds the restore-info JSON fields (no surrounding braces) for embedding in a
     * response object. The seed is emitted as a quoted string to avoid JavaScript
     * number-precision loss, and only when {@code includeSeed} is true.
     */
    public static String jsonFields(RestoreInfo info, boolean includeSeed) {
        StringBuilder sb = new StringBuilder();
        if (includeSeed) {
            sb.append(String.format(Locale.US, "\"seed\":\"%d\",", info.seed()));
        }
        sb.append(String.format(Locale.US,
                "\"mcVersion\":\"%s\",\"loader\":\"%s\",\"loaderVersion\":\"%s\","
                        + "\"modVersion\":\"%s\",\"worldName\":\"%s\"",
                jsonEscape(info.mcVersion()), jsonEscape(info.loader()),
                jsonEscape(info.loaderVersion()), jsonEscape(info.modVersion()),
                jsonEscape(info.worldName())));
        return sb.toString();
    }

    // GET /api/admin/restore-info. Admin-only. Always includes the seed.
    public static String handleGet(String method, Map<String, String> headers, String body,
                                   MinecraftServer server) {
        if (!"GET".equals(method))            return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return err(403, "Unauthorized");
        RestoreInfo info = RestoreInfo.capture(server);
        if (info == null)                     return err(503, "Server not ready");
        return "{" + jsonFields(info, true) + "}";
    }
}
