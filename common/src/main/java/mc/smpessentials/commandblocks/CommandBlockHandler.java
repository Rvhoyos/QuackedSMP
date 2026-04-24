package mc.smpessentials.commandblocks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.dashboard.AdminAuth;
import mc.smpessentials.dashboard.AdminHandler;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * HTTP handlers for {@code /api/admin/commandblocks/*} routes.
 * Delegates game logic to {@link CommandBlockService}.
 */
public final class CommandBlockHandler {
    private CommandBlockHandler() {}

    // GET /api/admin/commandblocks
    public static String handleGet(String method, Map<String, String> headers, String body,
                                   MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)         return AdminHandler.err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))  return AdminHandler.err(403, "Unauthorized");
        if (server == null)                    return AdminHandler.err(503, "Server not ready");

        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                boolean enabled = CommandBlockService.isEnabled(server);
                List<CommandBlockInfo> blocks = CommandBlockService.findAll(server);
                StringBuilder sb = new StringBuilder("{\"enabled\":");
                sb.append(enabled).append(",\"blocks\":[");
                for (int i = 0; i < blocks.size(); i++) {
                    if (i > 0) sb.append(',');
                    appendJson(sb, blocks.get(i));
                }
                sb.append("]}");
                future.complete(sb.toString());
            } catch (Exception ex) {
                future.complete(AdminHandler.err(500, AdminHandler.jsonEscape(ex.getMessage())));
            }
        });
        try { return future.get(5, TimeUnit.SECONDS); }
        catch (Exception e) { return AdminHandler.err(500, "Timeout"); }
    }

    // POST /api/admin/commandblocks/update
    public static String handleUpdate(String method, Map<String, String> headers, String body,
                                      MinecraftServer server) {
        if (!"POST".equals(method))            return AdminHandler.err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)          return AdminHandler.err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return AdminHandler.err(403, "Unauthorized");
        if (server == null)                     return AdminHandler.err(503, "Server not ready");

        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String dim = req.get("dim").getAsString();
            int x = req.get("x").getAsInt();
            int y = req.get("y").getAsInt();
            int z = req.get("z").getAsInt();
            String command = req.has("command") ? req.get("command").getAsString() : null;
            String mode = req.has("mode") ? req.get("mode").getAsString() : null;
            Boolean auto = req.has("auto") ? req.get("auto").getAsBoolean() : null;
            Boolean conditional = req.has("conditional") ? req.get("conditional").getAsBoolean() : null;
            Boolean trackOutput = req.has("trackOutput") ? req.get("trackOutput").getAsBoolean() : null;

            CompletableFuture<String> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    boolean ok = CommandBlockService.update(server, dim, x, y, z,
                            command, mode, auto, conditional, trackOutput);
                    future.complete(ok
                            ? "{\"ok\":true}"
                            : AdminHandler.err(404, "No command block at that position"));
                } catch (Exception ex) {
                    future.complete(AdminHandler.err(500, AdminHandler.jsonEscape(ex.getMessage())));
                }
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return AdminHandler.err(400, "Invalid request: " + AdminHandler.jsonEscape(e.getMessage()));
        }
    }

    // POST /api/admin/commandblocks/delete
    public static String handleDelete(String method, Map<String, String> headers, String body,
                                      MinecraftServer server) {
        if (!"POST".equals(method))            return AdminHandler.err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)          return AdminHandler.err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return AdminHandler.err(403, "Unauthorized");
        if (server == null)                     return AdminHandler.err(503, "Server not ready");

        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String dim = req.get("dim").getAsString();
            int x = req.get("x").getAsInt();
            int y = req.get("y").getAsInt();
            int z = req.get("z").getAsInt();

            CompletableFuture<String> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    boolean ok = CommandBlockService.delete(server, dim, x, y, z);
                    future.complete(ok
                            ? "{\"ok\":true}"
                            : AdminHandler.err(404, "No command block at that position"));
                } catch (Exception ex) {
                    future.complete(AdminHandler.err(500, AdminHandler.jsonEscape(ex.getMessage())));
                }
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return AdminHandler.err(400, "Invalid request: " + AdminHandler.jsonEscape(e.getMessage()));
        }
    }

    private static void appendJson(StringBuilder sb, CommandBlockInfo b) {
        sb.append(String.format(
                "{\"x\":%d,\"y\":%d,\"z\":%d,\"dim\":\"%s\",\"command\":\"%s\","
                        + "\"mode\":\"%s\",\"conditional\":%b,\"auto\":%b,\"powered\":%b,"
                        + "\"customName\":\"%s\",\"trackOutput\":%b,\"successCount\":%d}",
                b.x(), b.y(), b.z(),
                AdminHandler.jsonEscape(b.dimension()),
                AdminHandler.jsonEscape(b.command()),
                b.mode(), b.conditional(), b.auto(), b.powered(),
                AdminHandler.jsonEscape(b.customName()),
                b.trackOutput(), b.successCount()));
    }
}
