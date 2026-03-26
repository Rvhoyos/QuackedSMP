package mc.smpessentials.dashboard;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.ConfigIO;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import mc.smpessentials.bluemap.BlueMapIntegration;
import mc.smpessentials.skills.SkillType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Handles all {@code /api/admin/*} HTTP routes.
 *
 * <p>Registered into {@link DashboardServer} by {@link DashboardManager}.
 * Every protected endpoint calls {@link AdminAuth#isAuthorized} before executing.
 */
public final class AdminHandler {

    private AdminHandler() {}

    // ── Route handlers (called by DashboardServer) ────────────────────────────

    /**
     * GET /api/admin/status — public, no auth.
     * Returns whether admin is enabled, whether a password has been set, and the
     * server name so the gate modal can greet the user.
     */
    public static String handleStatus(String method, Map<String, String> headers, String body) {
        boolean enabled     = SmpConfig.ADMIN_ENABLED;
        boolean hasPassword = !SmpConfig.ADMIN_PASSWORD_HASH.isBlank();
        return String.format("{\"enabled\":%b,\"hasPassword\":%b}", enabled, hasPassword);
    }

    /**
     * POST /api/admin/setup — sets initial password.
     * Only works when no password is currently stored (first-time or after manual clear).
     */
    public static String handleSetup(String method, Map<String, String> headers, String body) {
        if (!"POST".equals(method)) return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)               return err(403, "Admin panel disabled");
        if (!AdminAuth.noPasswordSet())             return err(403, "Password already set — use login");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String password = req.get("password").getAsString();
            if (password.length() < 8) return err(400, "Password must be at least 8 characters");
            AdminAuth.setPassword(password);
            String token = AdminAuth.createSession();
            return String.format("{\"ok\":true,\"token\":\"%s\"}", token);
        } catch (Exception e) {
            return err(400, "Invalid request");
        }
    }

    /**
     * POST /api/admin/login — verifies password, returns session token.
     */
    public static String handleLogin(String method, Map<String, String> headers, String body) {
        if (!"POST".equals(method))     return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)   return err(403, "Admin panel disabled");
        if (AdminAuth.noPasswordSet())  return err(400, "No password set — use /api/admin/setup");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String password = req.get("password").getAsString();
            if (!AdminAuth.verifyPassword(password)) {
                return err(401, "Invalid password");
            }
            String token = AdminAuth.createSession();
            return String.format("{\"ok\":true,\"token\":\"%s\"}", token);
        } catch (Exception e) {
            return err(401, "Invalid request");
        }
    }

    /**
     * GET /api/admin/players — list of online players.
     */
    public static String handlePlayers(String method, Map<String, String> headers, String body,
                                       MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)                   return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))           return err(403, "Unauthorized");
        if (server == null)                             return err(503, "Server not ready");

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            boolean isOp  = server.getPlayerList().isOp(p.nameAndId());
            String  dim   = p.level().dimension().identifier().toString();
            String  name  = jsonEscape(p.getGameProfile().name());
            String  uuid  = p.getUUID().toString();
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.US,
                    "{\"name\":\"%s\",\"uuid\":\"%s\",\"dimension\":\"%s\",\"isOp\":%b}",
                    name, uuid, dim, isOp));
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * POST /api/admin/exec — executes a server command.
     * Runs asynchronously on the server thread; always returns ok immediately.
     */
    public static String handleExec(String method, Map<String, String> headers, String body,
                                    MinecraftServer server) {
        if (!"POST".equals(method))                     return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)                   return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))           return err(403, "Unauthorized");
        if (server == null)                             return err(503, "Server not ready");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String command = req.get("command").getAsString().strip();
            if (command.isBlank()) return err(400, "Empty command");
            SmpUtilsMod.LOGGER.info("[AdminPanel] exec: /{}", command);
            server.execute(() -> {
                try {
                    server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack(), command);
                } catch (Exception e) {
                    SmpUtilsMod.LOGGER.warn("[AdminPanel] exec failed: {}", e.getMessage(), e);
                }
            });
            return String.format("{\"ok\":true,\"command\":\"%s\"}", jsonEscape(command));
        } catch (Exception e) {
            SmpUtilsMod.LOGGER.warn("[AdminPanel] exec parse error: {}", e.getMessage());
            return err(400, "Invalid request");
        }
    }

    /**
     * GET /api/admin/config — returns editable config fields.
     */
    public static String handleConfigGet(String method, Map<String, String> headers, String body) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");

        StringBuilder sb = new StringBuilder("{");
        // General
        sb.append(String.format("\"max_claims\":%d,", SmpConfig.MAX_CLAIMS));
        sb.append(String.format("\"tp_warmup\":%d,", SmpConfig.TP_WARMUP));
        sb.append(String.format("\"welcome_message\":\"%s\",", jsonEscape(SmpConfig.WELCOME_MESSAGE)));
        sb.append(String.format("\"message_interval\":%d,", SmpConfig.MESSAGE_INTERVAL));
        sb.append(String.format("\"vip_bonus_claims\":%d,", SmpConfig.VIP_BONUS_CLAIMS));
        sb.append(String.format("\"allow_lava_wilderness\":%b,", SmpConfig.ALLOW_LAVA_WILDERNESS));
        // Feature toggles
        sb.append(String.format("\"claims_enabled\":%b,", SmpConfig.CLAIMS_ENABLED));
        sb.append(String.format("\"skills_enabled\":%b,", SmpConfig.SKILLS_ENABLED));
        sb.append(String.format("\"chatfilter_enabled\":%b,", SmpConfig.CHATFILTER_ENABLED));
        // Admin
        sb.append(String.format("\"admin_enabled\":%b,", SmpConfig.ADMIN_ENABLED));
        sb.append(String.format("\"dashboard_port\":%d,", SmpConfig.DASHBOARD_PORT));
        // Votifier
        sb.append(String.format("\"votifier_enabled\":%b,", SmpConfig.VOTIFIER_ENABLED));
        sb.append(String.format("\"votifier_port\":%d,", SmpConfig.VOTIFIER_PORT));
        sb.append(String.format("\"votifier_token\":\"%s\",", jsonEscape(SmpConfig.VOTIFIER_TOKEN)));
        sb.append(String.format("\"vote_broadcast\":\"%s\",", jsonEscape(SmpConfig.VOTE_BROADCAST)));
        sb.append("\"vote_rewards\":").append(jsonStrArr(SmpConfig.VOTE_REWARDS)).append(",");
        // Discord
        sb.append(String.format("\"discord_enabled\":%b,", !SmpConfig.DISCORD_WEBHOOK_URL.isBlank()));
        sb.append(String.format("\"discord_webhook_url\":\"%s\",", jsonEscape(SmpConfig.DISCORD_WEBHOOK_URL)));
        sb.append(String.format("\"discord_join_leave\":%b,", SmpConfig.DISCORD_JOIN_LEAVE));
        sb.append(String.format("\"discord_chat\":%b,", SmpConfig.DISCORD_CHAT));
        // Voice
        sb.append(String.format("\"voicechat_enable\":%b,", SmpConfig.VOICECHAT_ENABLE));
        // BlueMap
        sb.append(String.format("\"bluemap_enabled\":%b,", SmpConfig.BLUEMAP_ENABLE));
        sb.append(String.format("\"bluemap_show_homes\":%b,", SmpConfig.BLUEMAP_SHOW_HOMES));
        sb.append(String.format("\"bluemap_show_claims\":%b,", SmpConfig.BLUEMAP_SHOW_CLAIMS));
        sb.append(String.format("\"bluemap_show_worldborder\":%b,", SmpConfig.BLUEMAP_SHOW_WORLDBORDER));
        sb.append(String.format("\"bluemap_claim_color\":\"%s\",", jsonEscape(SmpConfig.BLUEMAP_CLAIM_COLOR)));
        sb.append(String.format("\"bluemap_op_claim_color\":\"%s\",", jsonEscape(SmpConfig.BLUEMAP_OP_CLAIM_COLOR)));
        sb.append(String.format("\"bluemap_vip_claim_color\":\"%s\",", jsonEscape(SmpConfig.BLUEMAP_VIP_CLAIM_COLOR)));
        sb.append(String.format("\"bluemap_worldborder_color\":\"%s\",", jsonEscape(SmpConfig.BLUEMAP_WORLDBORDER_COLOR)));
        // Lists
        sb.append("\"vips\":").append(jsonStrArr(SmpConfig.VIPS)).append(",");
        sb.append("\"periodic_messages\":").append(jsonStrArr(SmpConfig.PERIODIC_MESSAGES)).append(",");
        sb.append("\"rules\":").append(jsonStrArr(SmpConfig.RULES)).append(",");
        sb.append("\"mute_levels_minutes\":").append(jsonIntArr(SmpConfig.MUTE_LEVELS_MINUTES)).append(",");
        // Skills
        sb.append(String.format("\"skill_xp_exponent\":%.2f,", SmpConfig.SKILL_XP_EXPONENT));
        for (SkillType sk : SkillType.values()) {
            sb.append(String.format("\"skill_cooldown_%s\":%d,", sk.name().toLowerCase(), SmpConfig.getSkillCooldown(sk)));
        }
        for (SkillType sk : SkillType.values()) {
            sb.append(String.format("\"skill_unlock_%s\":%d,", sk.name().toLowerCase(), SmpConfig.getAbilityUnlockLevel(sk)));
        }
        // Caps (last entry — no trailing comma)
        sb.append(String.format("\"cap_industrial_speed\":%.2f,", SmpConfig.CAP_INDUSTRIAL_SPEED));
        sb.append(String.format("\"cap_nature_health\":%.2f,", SmpConfig.CAP_NATURE_HEALTH));
        sb.append(String.format("\"cap_combat_damage\":%.2f,", SmpConfig.CAP_COMBAT_DAMAGE));
        sb.append(String.format("\"cap_knowledge_xp\":%.2f,", SmpConfig.CAP_KNOWLEDGE_XP));
        sb.append(String.format("\"cap_double_drop\":%.2f,", SmpConfig.CAP_DOUBLE_DROP));
        sb.append(String.format("\"cap_defense_armor\":%.2f,", SmpConfig.CAP_DEFENSE_ARMOR));
        sb.append(String.format("\"cap_safe_landing\":%.2f", SmpConfig.CAP_SAFE_LANDING));
        sb.append("}");
        return sb.toString();
    }

    /**
     * POST /api/admin/config — applies a partial config patch and hot-reloads.
     * Only whitelisted keys are accepted; port changes are saved but require restart.
     */
    public static String handleConfigPost(String method, Map<String, String> headers, String body) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        try {
            JsonObject patch = JsonParser.parseString(body).getAsJsonObject();
            int changed = 0;

            // General
            if (patch.has("max_claims"))            { SmpConfig.MAX_CLAIMS            = patch.get("max_claims").getAsInt();               changed++; }
            if (patch.has("tp_warmup"))             { SmpConfig.TP_WARMUP             = patch.get("tp_warmup").getAsInt();                changed++; }
            if (patch.has("welcome_message"))       { SmpConfig.WELCOME_MESSAGE       = patch.get("welcome_message").getAsString();       changed++; }
            if (patch.has("message_interval"))      { SmpConfig.MESSAGE_INTERVAL      = patch.get("message_interval").getAsInt();         changed++; }
            if (patch.has("vip_bonus_claims"))      { SmpConfig.VIP_BONUS_CLAIMS      = patch.get("vip_bonus_claims").getAsInt();         changed++; }
            if (patch.has("allow_lava_wilderness")) { SmpConfig.ALLOW_LAVA_WILDERNESS = patch.get("allow_lava_wilderness").getAsBoolean(); changed++; }
            // Feature toggles
            if (patch.has("claims_enabled"))     { SmpConfig.CLAIMS_ENABLED     = patch.get("claims_enabled").getAsBoolean();     changed++; }
            if (patch.has("skills_enabled"))     { SmpConfig.SKILLS_ENABLED     = patch.get("skills_enabled").getAsBoolean();     changed++; }
            if (patch.has("chatfilter_enabled")) { SmpConfig.CHATFILTER_ENABLED = patch.get("chatfilter_enabled").getAsBoolean(); changed++; }
            // Admin
            if (patch.has("admin_enabled"))         { SmpConfig.ADMIN_ENABLED         = patch.get("admin_enabled").getAsBoolean();        changed++; }
            if (patch.has("dashboard_port"))        { SmpConfig.DASHBOARD_PORT        = patch.get("dashboard_port").getAsInt();           changed++; }
            // Votifier
            if (patch.has("votifier_enabled"))      { SmpConfig.VOTIFIER_ENABLED      = patch.get("votifier_enabled").getAsBoolean();     changed++; }
            if (patch.has("votifier_port"))         { SmpConfig.VOTIFIER_PORT         = patch.get("votifier_port").getAsInt();            changed++; }
            if (patch.has("votifier_token"))        { SmpConfig.VOTIFIER_TOKEN        = patch.get("votifier_token").getAsString();        changed++; }
            if (patch.has("vote_broadcast"))        { SmpConfig.VOTE_BROADCAST        = patch.get("vote_broadcast").getAsString();        changed++; }
            // Discord
            if (patch.has("discord_webhook_url"))   { SmpConfig.DISCORD_WEBHOOK_URL   = patch.get("discord_webhook_url").getAsString();   changed++; }
            if (patch.has("discord_join_leave"))    { SmpConfig.DISCORD_JOIN_LEAVE    = patch.get("discord_join_leave").getAsBoolean();   changed++; }
            if (patch.has("discord_chat"))          { SmpConfig.DISCORD_CHAT          = patch.get("discord_chat").getAsBoolean();         changed++; }
            if (patch.has("voicechat_enable"))      { SmpConfig.VOICECHAT_ENABLE      = patch.get("voicechat_enable").getAsBoolean();     changed++; }
            // BlueMap
            boolean blueMapChanged = false;
            if (patch.has("bluemap_enabled"))           { SmpConfig.BLUEMAP_ENABLE           = patch.get("bluemap_enabled").getAsBoolean();           changed++; blueMapChanged = true; }
            if (patch.has("bluemap_show_homes"))        { SmpConfig.BLUEMAP_SHOW_HOMES        = patch.get("bluemap_show_homes").getAsBoolean();        changed++; blueMapChanged = true; }
            if (patch.has("bluemap_show_claims"))       { SmpConfig.BLUEMAP_SHOW_CLAIMS       = patch.get("bluemap_show_claims").getAsBoolean();       changed++; blueMapChanged = true; }
            if (patch.has("bluemap_show_worldborder"))  { SmpConfig.BLUEMAP_SHOW_WORLDBORDER  = patch.get("bluemap_show_worldborder").getAsBoolean();  changed++; blueMapChanged = true; }
            if (patch.has("bluemap_claim_color"))       { SmpConfig.BLUEMAP_CLAIM_COLOR       = patch.get("bluemap_claim_color").getAsString();        changed++; blueMapChanged = true; }
            if (patch.has("bluemap_op_claim_color"))    { SmpConfig.BLUEMAP_OP_CLAIM_COLOR    = patch.get("bluemap_op_claim_color").getAsString();     changed++; blueMapChanged = true; }
            if (patch.has("bluemap_vip_claim_color"))   { SmpConfig.BLUEMAP_VIP_CLAIM_COLOR   = patch.get("bluemap_vip_claim_color").getAsString();    changed++; blueMapChanged = true; }
            if (patch.has("bluemap_worldborder_color")) { SmpConfig.BLUEMAP_WORLDBORDER_COLOR = patch.get("bluemap_worldborder_color").getAsString();  changed++; blueMapChanged = true; }
            // Lists
            if (patch.has("vote_rewards") && patch.get("vote_rewards").isJsonArray())          { loadStrArr(patch.getAsJsonArray("vote_rewards"),         SmpConfig.VOTE_REWARDS);       changed++; }
            if (patch.has("vips") && patch.get("vips").isJsonArray())                          { loadStrArr(patch.getAsJsonArray("vips"),                  SmpConfig.VIPS);               changed++; }
            if (patch.has("periodic_messages") && patch.get("periodic_messages").isJsonArray()) { loadStrArr(patch.getAsJsonArray("periodic_messages"),     SmpConfig.PERIODIC_MESSAGES);  changed++; }
            if (patch.has("rules") && patch.get("rules").isJsonArray())                        { loadStrArr(patch.getAsJsonArray("rules"),                 SmpConfig.RULES);              changed++; }
            if (patch.has("mute_levels_minutes") && patch.get("mute_levels_minutes").isJsonArray()) { loadIntArr(patch.getAsJsonArray("mute_levels_minutes"), SmpConfig.MUTE_LEVELS_MINUTES); changed++; }
            // Skills
            if (patch.has("skill_xp_exponent")) { SmpConfig.SKILL_XP_EXPONENT = patch.get("skill_xp_exponent").getAsDouble(); changed++; }
            for (SkillType sk : SkillType.values()) {
                String cdKey = "skill_cooldown_" + sk.name().toLowerCase();
                if (patch.has(cdKey)) { SmpConfig.SKILL_COOLDOWNS.put(sk.name().toLowerCase(), patch.get(cdKey).getAsLong()); changed++; }
                String ulKey = "skill_unlock_" + sk.name().toLowerCase();
                if (patch.has(ulKey)) { SmpConfig.SKILL_UNLOCK_LEVELS.put(sk.name().toLowerCase(), patch.get(ulKey).getAsInt()); changed++; }
            }
            // Caps
            if (patch.has("cap_industrial_speed")) { SmpConfig.CAP_INDUSTRIAL_SPEED = patch.get("cap_industrial_speed").getAsDouble(); changed++; }
            if (patch.has("cap_nature_health"))    { SmpConfig.CAP_NATURE_HEALTH    = patch.get("cap_nature_health").getAsDouble();    changed++; }
            if (patch.has("cap_combat_damage"))    { SmpConfig.CAP_COMBAT_DAMAGE    = patch.get("cap_combat_damage").getAsDouble();    changed++; }
            if (patch.has("cap_knowledge_xp"))     { SmpConfig.CAP_KNOWLEDGE_XP     = patch.get("cap_knowledge_xp").getAsDouble();     changed++; }
            if (patch.has("cap_double_drop"))      { SmpConfig.CAP_DOUBLE_DROP      = patch.get("cap_double_drop").getAsDouble();      changed++; }
            if (patch.has("cap_defense_armor"))    { SmpConfig.CAP_DEFENSE_ARMOR    = patch.get("cap_defense_armor").getAsDouble();    changed++; }
            if (patch.has("cap_safe_landing"))     { SmpConfig.CAP_SAFE_LANDING     = patch.get("cap_safe_landing").getAsDouble();     changed++; }

            if (changed > 0) {
                ConfigIO.save();
                // Votifier: restart the TCP listener so the new enabled/port/token values take effect immediately.
                if (patch.has("votifier_enabled") || patch.has("votifier_port") || patch.has("votifier_token")) {
                    mc.smpessentials.votifier.VotifierListener.restart();
                }
                if (blueMapChanged) {
                    var mm = BlueMapIntegration.getMarkerManager();
                    var sv = BlueMapIntegration.getServer();
                    if (mm != null && sv != null) sv.execute(mm::updateAll);
                }
            }
            return String.format("{\"ok\":true,\"changed\":%d}", changed);
        } catch (Exception e) {
            return err(400, "Invalid config patch: " + jsonEscape(e.getMessage()));
        }
    }

    /**
     * POST /api/admin/setop — sets a player's operator level by editing ops.json directly.
     * Levels 1–3 have no vanilla command so require direct file editing.
     * ops.json is written synchronously on the HTTP thread; only the reload+sync
     * is dispatched to the server thread.
     */
    public static String handleSetOp(String method, Map<String, String> headers, String body,
                                     MinecraftServer server) {
        if (!"POST".equals(method))                 return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)               return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))       return err(403, "Unauthorized");
        if (server == null)                         return err(503, "Server not ready");

        try {
            JsonObject req  = JsonParser.parseString(body).getAsJsonObject();
            String     name = req.get("name").getAsString().strip();
            String     uuid = req.get("uuid").getAsString().strip();
            int        lvl  = req.get("level").getAsInt();
            if (lvl < 1 || lvl > 4) return err(400, "Level must be 1–4");

            // Write ops.json synchronously on the HTTP thread
            Path opsPath = server.getFile("ops.json");
            SmpUtilsMod.LOGGER.info("[AdminPanel] setop: editing {} for {} ({}) → level {}", opsPath, name, uuid, lvl);

            JsonArray ops;
            if (Files.exists(opsPath)) {
                String raw = Files.readString(opsPath);
                ops = raw.isBlank() ? new JsonArray()
                                    : JsonParser.parseString(raw).getAsJsonArray();
            } else {
                ops = new JsonArray();
            }

            boolean found = false;
            for (int i = 0; i < ops.size(); i++) {
                JsonObject entry = ops.get(i).getAsJsonObject();
                if (entry.has("uuid") && uuid.equalsIgnoreCase(entry.get("uuid").getAsString())) {
                    entry.addProperty("level", lvl);
                    found = true;
                    break;
                }
            }
            if (!found) {
                JsonObject entry = new JsonObject();
                entry.addProperty("uuid",                uuid);
                entry.addProperty("name",                name);
                entry.addProperty("level",               lvl);
                entry.addProperty("bypassesPlayerLimit", false);
                ops.add(entry);
            }

            Files.writeString(opsPath, new GsonBuilder().setPrettyPrinting().create().toJson(ops));
            SmpUtilsMod.LOGGER.info("[AdminPanel] ops.json written ok");

            // Reload ops list and notify the online player on the server thread
            server.execute(() -> {
                try {
                    server.getPlayerList().getOps().load();
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        if (p.getUUID().toString().equalsIgnoreCase(uuid)) {
                            server.getPlayerList().sendPlayerPermissionLevel(p);
                            SmpUtilsMod.LOGGER.info("[AdminPanel] setop synced to {}", name);
                            break;
                        }
                    }
                } catch (Exception e) {
                    SmpUtilsMod.LOGGER.error("[AdminPanel] setop reload failed: {}", e.getMessage(), e);
                }
            });

            return String.format("{\"ok\":true,\"name\":\"%s\",\"level\":%d}", jsonEscape(name), lvl);
        } catch (Exception e) {
            SmpUtilsMod.LOGGER.warn("[AdminPanel] setop parse error: {}", e.getMessage(), e);
            return err(400, "Invalid request: " + jsonEscape(e.getMessage()));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Builds a JSON error string. The status code is embedded so DashboardServer can read it. */
    static String err(int status, String message) {
        return "__HTTP_" + status + "__" + String.format("{\"error\":\"%s\"}", jsonEscape(message));
    }

    static boolean isErr(String result) {
        return result != null && result.startsWith("__HTTP_");
    }

    static int errStatus(String result) {
        return Integer.parseInt(result.substring(7, 10));
    }

    static String errBody(String result) {
        return result.substring(13);
    }

    private static String jsonStrArr(java.util.List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(jsonEscape(list.get(i))).append('"');
        }
        return sb.append(']').toString();
    }

    private static String jsonIntArr(java.util.List<Integer> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(list.get(i));
        }
        return sb.append(']').toString();
    }

    private static void loadStrArr(com.google.gson.JsonArray arr, java.util.List<String> list) {
        list.clear();
        for (var el : arr) list.add(el.getAsString());
    }

    private static void loadIntArr(com.google.gson.JsonArray arr, java.util.List<Integer> list) {
        list.clear();
        for (var el : arr) list.add(el.getAsInt());
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
