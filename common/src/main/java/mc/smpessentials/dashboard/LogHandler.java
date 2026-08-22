package mc.smpessentials.dashboard;

import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.serverlog.LogLine;
import mc.smpessentials.serverlog.ServerLogService;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static mc.smpessentials.dashboard.AdminHandler.err;
import static mc.smpessentials.dashboard.AdminHandler.jsonEscape;
import static mc.smpessentials.dashboard.AdminHandler.queryParam;

/**
 * HTTP route for the live server log behind the admin panel terminal. Clients
 * poll with the sequence of the newest line they hold, so each response carries
 * only what is new plus the current head, letting a client that fell behind see
 * the gap rather than assume it is up to date.
 */
public final class LogHandler {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT     = 500;

    private LogHandler() {}

    /** GET /api/admin/logs, params since and limit. Lines newer than since, oldest first. */
    public static String handleLogs(String method, Map<String, String> headers, String body) {
        if (!"GET".equals(method))            return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return err(403, "Unauthorized");

        String qs    = headers.getOrDefault("x-query-string", "");
        long   since = parseLong(queryParam(qs, "since"), 0);
        int    limit = Math.clamp(parseLong(queryParam(qs, "limit"), DEFAULT_LIMIT), 1, MAX_LIMIT);

        ServerLogService log = ServerLogService.get();
        List<LogLine> lines = log.since(since, limit);

        StringBuilder sb = new StringBuilder("{\"head\":").append(log.head()).append(",\"lines\":[");
        for (int i = 0; i < lines.size(); i++) {
            LogLine l = lines.get(i);
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.US,
                    "{\"seq\":%d,\"t\":%d,\"level\":\"%s\",\"logger\":\"%s\",\"thread\":\"%s\",\"msg\":\"%s\"}",
                    l.seq(), l.timeMillis(), jsonEscape(l.level()), jsonEscape(l.logger()),
                    jsonEscape(l.thread()), jsonEscape(l.msg())));
        }
        return sb.append("]}").toString();
    }

    private static long parseLong(String raw, long fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
