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
import mc.smpessentials.chatfilter.ChatFilter;
import mc.smpessentials.claims.storage.ClaimedSavedData;
import mc.smpessentials.dims.DimManager;
import mc.smpessentials.dims.DimSavedData;
import mc.smpessentials.skills.SkillData;
import mc.smpessentials.skills.SkillManager;
import mc.smpessentials.skills.SkillType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.stream.Collectors;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
     * Returns whether admin is enabled and whether a password has been set.
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
        String ip = headers.getOrDefault("x-forwarded-for", "direct").split(",")[0].trim();
        if (AdminAuth.isRateLimited(ip)) return err(429, "Too many failed attempts — try again later");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String password = req.get("password").getAsString();
            if (!AdminAuth.verifyPassword(password)) {
                AdminAuth.recordFailedLogin(ip);
                return err(401, "Invalid password");
            }
            AdminAuth.clearFailedLogins(ip);
            String token = AdminAuth.createSession();
            return String.format("{\"ok\":true,\"token\":\"%s\"}", token);
        } catch (Exception e) {
            AdminAuth.recordFailedLogin(ip);
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
        sb.append(String.format("\"server_name\":\"%s\",", jsonEscape(SmpConfig.SERVER_NAME)));
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
            if (patch.has("server_name"))           { SmpConfig.SERVER_NAME           = patch.get("server_name").getAsString();           changed++; }
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
     * POST /api/admin/dashboard/disable — saves dashboard_enabled=false to config and
     * shuts down the dashboard server after the response is sent.
     * The caller will lose connection; they must re-enable via /smp admin setpassword.
     */
    public static String handleDashboardDisable(String method, Map<String, String> headers, String body) {
        if (!"POST".equals(method))           return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return err(403, "Unauthorized");
        DashboardManager.scheduleDisable();
        return "{\"ok\":true}";
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

    // ── Dims ─────────────────────────────────────────────────────────────────

    /**
     * GET /api/admin/dims — list all custom dimensions stored in DimSavedData.
     */
    public static String handleDimsGet(String method, Map<String, String> headers, String body,
                                       MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");

        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                List<DimSavedData.DimEntry> entries = DimSavedData.get(server).getEntries();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < entries.size(); i++) {
                    if (i > 0) sb.append(',');
                    DimSavedData.DimEntry e = entries.get(i);
                    sb.append("{\"id\":\"").append(jsonEscape(e.id())).append('"');
                    sb.append(",\"generatorType\":\"").append(jsonEscape(e.generatorType())).append('"');
                    sb.append(",\"generatorConfig\":");
                    e.generatorConfig().ifPresentOrElse(
                        c -> sb.append('"').append(jsonEscape(c)).append('"'),
                        () -> sb.append("null"));
                    sb.append(",\"portalBlock\":");
                    e.portalBlock().ifPresentOrElse(
                        b -> sb.append('"').append(jsonEscape(b)).append('"'),
                        () -> sb.append("null"));
                    sb.append('}');
                }
                sb.append(']');
                future.complete(sb.toString());
            } catch (Exception ex) {
                future.complete(err(500, jsonEscape(ex.getMessage())));
            }
        });
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return err(500, "Timeout reading dimensions");
        }
    }

    /**
     * POST /api/admin/dims/create — create a custom dimension.
     * Body: {"id":"quacksmp:pvp","type":"overworld","config":"biomes minecraft:plains:1"} (config optional)
     */
    public static String handleDimCreate(String method, Map<String, String> headers, String body,
                                         MinecraftServer server) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String id   = req.get("id").getAsString().strip();
            String type = req.get("type").getAsString().strip();
            Optional<String> config = Optional.empty();
            if (req.has("config") && !req.get("config").isJsonNull()) {
                String raw = req.get("config").getAsString().strip();
                if (!raw.isEmpty()) config = Optional.of(raw);
            }
            final Optional<String> finalConfig = config;

            CompletableFuture<String> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    String err = DimManager.create(server, id, type, finalConfig);
                    if (err != null) future.complete(err(400, jsonEscape(err)));
                    else future.complete("{\"ok\":true}");
                } catch (Exception ex) {
                    future.complete(err(500, jsonEscape(ex.getMessage())));
                }
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return err(400, "Invalid request: " + jsonEscape(e.getMessage()));
        }
    }

    /**
     * POST /api/admin/dims/delete — delete a custom dimension.
     * Body: {"id":"quacksmp:pvp"}
     */
    public static String handleDimDelete(String method, Map<String, String> headers, String body,
                                         MinecraftServer server) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String id = req.get("id").getAsString().strip();

            CompletableFuture<String> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    String err = DimManager.destroy(server, id);
                    if (err != null) future.complete(err(400, jsonEscape(err)));
                    else future.complete("{\"ok\":true}");
                } catch (Exception ex) {
                    future.complete(err(500, jsonEscape(ex.getMessage())));
                }
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return err(400, "Invalid request: " + jsonEscape(e.getMessage()));
        }
    }

    /**
     * POST /api/admin/dims/setportal — assign a portal frame block to a custom dimension.
     * Body: {"dimId":"quacksmp:pvp","blockId":"minecraft:glowstone"}
     */
    public static String handleDimSetPortal(String method, Map<String, String> headers, String body,
                                            MinecraftServer server) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");
        try {
            JsonObject req    = JsonParser.parseString(body).getAsJsonObject();
            String dimId      = req.get("dimId").getAsString().strip();
            String blockId    = req.get("blockId").getAsString().strip();

            CompletableFuture<String> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    // Validate block exists (same check as /dim setportal command)
                    Identifier blockLoc;
                    try {
                        blockLoc = Identifier.parse(blockId);
                    } catch (Exception ex) {
                        future.complete(err(400, "Invalid block ID format: " + jsonEscape(blockId)));
                        return;
                    }
                    Block block = BuiltInRegistries.BLOCK.getValue(blockLoc);
                    if (block == null || block == Blocks.AIR) {
                        future.complete(err(400, "Unknown block: " + jsonEscape(blockId)));
                        return;
                    }

                    DimSavedData data = DimSavedData.get(server);
                    // Reassign if block was previously assigned to a different dim
                    data.getDimForPortalBlock(blockId).ifPresent(existing -> {
                        if (!existing.equals(dimId)) data.clearPortalBlock(existing);
                    });
                    boolean ok = data.setPortalBlock(dimId, blockId);
                    if (!ok) future.complete(err(400, jsonEscape(dimId + " is not a custom dimension")));
                    else future.complete("{\"ok\":true}");
                } catch (Exception ex) {
                    future.complete(err(500, jsonEscape(ex.getMessage())));
                }
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return err(400, "Invalid request: " + jsonEscape(e.getMessage()));
        }
    }

    // ── Registry lists ────────────────────────────────────────────────────────

    /**
     * GET /api/admin/blocks — returns sorted JSON array of all registered block IDs.
     * Used by the dashboard to populate portal-block autocomplete suggestions.
     * BuiltInRegistries.BLOCK is static and read-only, safe to query from any thread.
     */
    public static String handleBlocksGet(String method, Map<String, String> headers, String body) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");

        List<String> ids = BuiltInRegistries.BLOCK.keySet().stream()
                .map(Object::toString)
                .sorted()
                .collect(Collectors.toList());
        return jsonStrArr(ids);
    }

    /**
     * GET /api/admin/biomes — returns sorted JSON array of all registered biome IDs.
     * Uses the server's registry access (dynamic registry), requires the server.
     */
    public static String handleBiomesGet(String method, Map<String, String> headers, String body,
                                         MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");

        try {
            List<String> ids = server.registryAccess()
                    .lookupOrThrow(Registries.BIOME)
                    .listElementIds()
                    .map(ResourceKey::identifier)
                    .map(Object::toString)
                    .sorted()
                    .collect(Collectors.toList());
            return jsonStrArr(ids);
        } catch (Exception e) {
            return err(500, "Failed to list biomes: " + jsonEscape(e.getMessage()));
        }
    }

    // ── Skills leaderboard (public) ───────────────────────────────────────────

    /**
     * GET /api/skills/leaderboard — public, no auth.
     * Returns overall top-10 and per-skill top-5 for all 12 skills.
     */
    public static String handleSkillsLeaderboard(String method, Map<String, String> headers, String body,
                                                 MinecraftServer server) {
        if (server == null) return err(503, "Server not ready");

        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                ServerLevel overworld = server.overworld();
                SkillData data = SkillData.get(overworld);

                StringBuilder sb = new StringBuilder("{\"overall\":");
                List<SkillData.LeaderboardEntry> overall = data.getLeaderboard(10, 0);
                sb.append('[');
                for (int i = 0; i < overall.size(); i++) {
                    if (i > 0) sb.append(',');
                    SkillData.LeaderboardEntry e = overall.get(i);
                    sb.append(String.format("{\"name\":\"%s\",\"level\":%d}",
                            jsonEscape(data.getDisplayName(e.uuid())), e.level()));
                }
                sb.append("],\"bySkill\":{");
                SkillType[] skills = SkillType.values();
                for (int s = 0; s < skills.length; s++) {
                    if (s > 0) sb.append(',');
                    SkillType skill = skills[s];
                    sb.append('"').append(skill.name()).append("\":[");
                    List<SkillData.LeaderboardEntry> entries = data.getSkillLeaderboard(skill, 5, 0);
                    for (int i = 0; i < entries.size(); i++) {
                        if (i > 0) sb.append(',');
                        SkillData.LeaderboardEntry e = entries.get(i);
                        sb.append(String.format("{\"name\":\"%s\",\"level\":%d}",
                                jsonEscape(data.getDisplayName(e.uuid())), e.level()));
                    }
                    sb.append(']');
                }
                sb.append("}}");
                future.complete(sb.toString());
            } catch (Exception ex) {
                future.complete(err(500, jsonEscape(ex.getMessage())));
            }
        });
        try { return future.get(5, TimeUnit.SECONDS); } catch (Exception e) { return err(500, "Timeout"); }
    }

    // ── Skills admin ──────────────────────────────────────────────────────────

    /**
     * GET /api/admin/skills/players — list all players who have skill data.
     * Returns [{uuid, name, totalLevel}] sorted by total level desc.
     */
    public static String handleSkillsPlayers(String method, Map<String, String> headers, String body,
                                             MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");

        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                SkillData data = SkillData.get(server.overworld());
                List<SkillData.LeaderboardEntry> all = data.getLeaderboard(9999, 0);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < all.size(); i++) {
                    if (i > 0) sb.append(',');
                    SkillData.LeaderboardEntry e = all.get(i);
                    sb.append(String.format("{\"uuid\":\"%s\",\"name\":\"%s\",\"totalLevel\":%d}",
                            e.uuid(), jsonEscape(data.getDisplayName(e.uuid())), e.level()));
                }
                sb.append(']');
                future.complete(sb.toString());
            } catch (Exception ex) {
                future.complete(err(500, jsonEscape(ex.getMessage())));
            }
        });
        try { return future.get(5, TimeUnit.SECONDS); } catch (Exception e) { return err(500, "Timeout"); }
    }

    /**
     * GET /api/admin/skills?player=<uuid_or_name> — get a single player's full skill data.
     * Returns {uuid, name, skills: {SKILL_NAME: {level, xp}, ...}}.
     */
    public static String handleSkillsGet(String method, Map<String, String> headers, String body,
                                         MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");

        String playerParam = queryParam(headers.getOrDefault("x-query-string", ""), "player");
        if (playerParam.isEmpty()) return err(400, "player param required");

        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                SkillData data = SkillData.get(server.overworld());
                UUID uuid = null;
                try { uuid = UUID.fromString(playerParam); } catch (Exception ignored) {}
                if (uuid == null) {
                    String lp = playerParam.toLowerCase(Locale.ROOT);
                    for (SkillData.LeaderboardEntry e : data.getLeaderboard(9999, 0)) {
                        if (data.getDisplayName(e.uuid()).toLowerCase(Locale.ROOT).equals(lp)) {
                            uuid = e.uuid();
                            break;
                        }
                    }
                }
                if (uuid == null) { future.complete(err(404, "Player not found")); return; }

                UUID finalUuid = uuid;
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("{\"uuid\":\"%s\",\"name\":\"%s\",\"skills\":{",
                        uuid, jsonEscape(data.getDisplayName(uuid))));
                SkillType[] skills = SkillType.values();
                for (int i = 0; i < skills.length; i++) {
                    if (i > 0) sb.append(',');
                    SkillType skill = skills[i];
                    double xp = data.getXp(finalUuid, skill);
                    int level = data.getLevel(finalUuid, skill);
                    sb.append(String.format(Locale.US, "\"%s\":{\"level\":%d,\"xp\":%.2f}", skill.name(), level, xp));
                }
                sb.append("}}");
                future.complete(sb.toString());
            } catch (Exception ex) {
                future.complete(err(500, jsonEscape(ex.getMessage())));
            }
        });
        try { return future.get(5, TimeUnit.SECONDS); } catch (Exception e) { return err(500, "Timeout"); }
    }

    /**
     * POST /api/admin/skills/set — set a player's level for a specific skill.
     * Body: {"uuid":"...","skill":"MINING","level":42}
     */
    public static String handleSkillsSet(String method, Map<String, String> headers, String body,
                                         MinecraftServer server) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");
        try {
            JsonObject req  = JsonParser.parseString(body).getAsJsonObject();
            UUID uuid       = UUID.fromString(req.get("uuid").getAsString());
            SkillType skill = SkillType.valueOf(req.get("skill").getAsString().toUpperCase(Locale.ROOT));
            int level       = req.get("level").getAsInt();
            if (level < 0 || level > SkillManager.MAX_LEVEL)
                return err(400, "Level must be 0–" + SkillManager.MAX_LEVEL);
            long xp = SkillManager.totalXpForLevel(level);

            CompletableFuture<String> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    SkillData data = SkillData.get(server.overworld());
                    data.setXp(uuid, skill, xp);
                    int newLevel = data.getLevel(uuid, skill);
                    future.complete(String.format(Locale.US, "{\"ok\":true,\"level\":%d,\"xp\":%d}", newLevel, xp));
                } catch (Exception ex) {
                    future.complete(err(500, jsonEscape(ex.getMessage())));
                }
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return err(400, "Invalid request: " + jsonEscape(e.getMessage()));
        }
    }

    // ── Claims ────────────────────────────────────────────────────────────────

    /**
     * GET /api/admin/claims — summary of all claims per player.
     * Returns {total, players: [{uuid, name, count}]} sorted by count desc.
     */
    public static String handleClaimsGet(String method, Map<String, String> headers, String body,
                                         MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");

        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                ClaimedSavedData claims = ClaimedSavedData.get(server.overworld());
                SkillData skills = SkillData.get(server.overworld());
                Map<UUID, Integer> counts = claims.claimCountsSnapshot();

                int total = counts.values().stream().mapToInt(Integer::intValue).sum();

                List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(counts.entrySet());
                sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

                StringBuilder sb = new StringBuilder(String.format("{\"total\":%d,\"players\":[", total));
                for (int i = 0; i < sorted.size(); i++) {
                    if (i > 0) sb.append(',');
                    Map.Entry<UUID, Integer> entry = sorted.get(i);
                    sb.append(String.format("{\"uuid\":\"%s\",\"name\":\"%s\",\"count\":%d}",
                            entry.getKey(), jsonEscape(skills.getDisplayName(entry.getKey())), entry.getValue()));
                }
                sb.append("]}");
                future.complete(sb.toString());
            } catch (Exception ex) {
                future.complete(err(500, jsonEscape(ex.getMessage())));
            }
        });
        try { return future.get(5, TimeUnit.SECONDS); } catch (Exception e) { return err(500, "Timeout"); }
    }

    /**
     * POST /api/admin/claims/unclaim — removes all claims owned by a player.
     * Body: {"uuid":"..."}
     */
    public static String handleClaimsUnclaim(String method, Map<String, String> headers, String body,
                                             MinecraftServer server) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            UUID uuid      = UUID.fromString(req.get("uuid").getAsString());

            CompletableFuture<String> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    ClaimedSavedData claims = ClaimedSavedData.get(server.overworld());
                    int removed = claims.removeAllByOwner(uuid);
                    future.complete(String.format("{\"ok\":true,\"removed\":%d}", removed));
                } catch (Exception ex) {
                    future.complete(err(500, jsonEscape(ex.getMessage())));
                }
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return err(400, "Invalid request: " + jsonEscape(e.getMessage()));
        }
    }

    // ── Chat filter ───────────────────────────────────────────────────────────

    /**
     * GET /api/admin/chatfilter — paginated word list.
     * Query params: q (search), page, size, tab (blocked|whitelist)
     */
    public static String handleChatFilterGet(String method, Map<String, String> headers, String body,
                                             MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");

        String qs  = headers.getOrDefault("x-query-string", "");
        String q   = queryParam(qs, "q");
        int page   = 0;
        int size   = 50;
        try { page = Integer.parseInt(queryParam(qs, "page")); } catch (Exception ignored) {}
        try { size = Math.min(200, Math.max(1, Integer.parseInt(queryParam(qs, "size")))); } catch (Exception ignored) {}
        boolean isWhitelist = "whitelist".equals(queryParam(qs, "tab"));

        int finalPage = page, finalSize = size;
        boolean finalWhitelist = isWhitelist;

        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                Set<String> source = finalWhitelist
                        ? ChatFilter.getData(server).whitelistSnapshot()
                        : ChatFilter.getData(server).snapshot();

                List<String> filtered;
                if (q.isEmpty()) {
                    filtered = new ArrayList<>(source);
                } else {
                    String lq = q.toLowerCase(Locale.ROOT);
                    filtered = source.stream().filter(w -> w.contains(lq)).collect(Collectors.toList());
                }

                int total = filtered.size();
                int start = finalPage * finalSize;
                int end   = Math.min(start + finalSize, total);
                List<String> pageWords = (start < total) ? filtered.subList(start, end) : List.of();

                StringBuilder sb = new StringBuilder(String.format(
                        "{\"total\":%d,\"page\":%d,\"size\":%d,\"words\":[", total, finalPage, finalSize));
                for (int i = 0; i < pageWords.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append('"').append(jsonEscape(pageWords.get(i))).append('"');
                }
                sb.append("]}");
                future.complete(sb.toString());
            } catch (Exception ex) {
                future.complete(err(500, jsonEscape(ex.getMessage())));
            }
        });
        try { return future.get(5, TimeUnit.SECONDS); } catch (Exception e) { return err(500, "Timeout"); }
    }

    /**
     * POST /api/admin/chatfilter/add — bulk add words to blocked list or whitelist.
     * Body: {"words":["word1","word2"], "whitelist":false}
     */
    public static String handleChatFilterAdd(String method, Map<String, String> headers, String body,
                                             MinecraftServer server) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            boolean toWhitelist = req.has("whitelist") && req.get("whitelist").getAsBoolean();
            JsonArray arr = req.getAsJsonArray("words");
            List<String> wordList = new ArrayList<>();
            for (var el : arr) { String w = el.getAsString().strip(); if (!w.isEmpty()) wordList.add(w); }
            if (wordList.isEmpty()) return err(400, "No words provided");

            CompletableFuture<String> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    var data = ChatFilter.getData(server);
                    int added = 0;
                    for (String w : wordList) {
                        boolean changed = toWhitelist ? data.addWhitelist(w) : data.add(w);
                        if (changed) added++;
                    }
                    future.complete(String.format("{\"ok\":true,\"added\":%d}", added));
                } catch (Exception ex) {
                    future.complete(err(500, jsonEscape(ex.getMessage())));
                }
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return err(400, "Invalid request: " + jsonEscape(e.getMessage()));
        }
    }

    /**
     * POST /api/admin/chatfilter/remove — bulk remove words from blocked list or whitelist.
     * Body: {"words":["word1","word2"], "whitelist":false}
     */
    public static String handleChatFilterRemove(String method, Map<String, String> headers, String body,
                                                MinecraftServer server) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            boolean fromWhitelist = req.has("whitelist") && req.get("whitelist").getAsBoolean();
            JsonArray arr = req.getAsJsonArray("words");
            List<String> wordList = new ArrayList<>();
            for (var el : arr) wordList.add(el.getAsString());
            if (wordList.isEmpty()) return err(400, "No words provided");

            CompletableFuture<String> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    var data = ChatFilter.getData(server);
                    int removed = 0;
                    for (String w : wordList) {
                        boolean changed = fromWhitelist ? data.removeWhitelist(w) : data.remove(w);
                        if (changed) removed++;
                    }
                    future.complete(String.format("{\"ok\":true,\"removed\":%d}", removed));
                } catch (Exception ex) {
                    future.complete(err(500, jsonEscape(ex.getMessage())));
                }
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return err(400, "Invalid request: " + jsonEscape(e.getMessage()));
        }
    }

    /**
     * GET /api/admin/chatfilter/mutes — list all currently muted players.
     * Returns [{uuid, name, muteEnd}] (expired mutes excluded).
     */
    public static String handleChatFilterMutes(String method, Map<String, String> headers, String body,
                                               MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");

        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                Map<UUID, Long> mutesMap = ChatFilter.getData(server).mutesSnapshot();
                SkillData skillData      = SkillData.get(server.overworld());
                long now                 = System.currentTimeMillis();

                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (Map.Entry<UUID, Long> entry : mutesMap.entrySet()) {
                    if (entry.getValue() <= now) continue;
                    if (!first) sb.append(',');
                    first = false;
                    sb.append(String.format("{\"uuid\":\"%s\",\"name\":\"%s\",\"muteEnd\":%d}",
                            entry.getKey(), jsonEscape(skillData.getDisplayName(entry.getKey())), entry.getValue()));
                }
                sb.append(']');
                future.complete(sb.toString());
            } catch (Exception ex) {
                future.complete(err(500, jsonEscape(ex.getMessage())));
            }
        });
        try { return future.get(5, TimeUnit.SECONDS); } catch (Exception e) { return err(500, "Timeout"); }
    }

    /**
     * POST /api/admin/chatfilter/unmute — unmute a player immediately.
     * Body: {"uuid":"..."}
     */
    public static String handleChatFilterUnmute(String method, Map<String, String> headers, String body,
                                                MinecraftServer server) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            UUID uuid = UUID.fromString(req.get("uuid").getAsString());

            CompletableFuture<String> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    ChatFilter.getData(server).unmute(uuid);
                    future.complete("{\"ok\":true}");
                } catch (Exception ex) {
                    future.complete(err(500, jsonEscape(ex.getMessage())));
                }
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return err(400, "Invalid request: " + jsonEscape(e.getMessage()));
        }
    }

    // ── Mods management ───────────────────────────────────────────────────────

    /**
     * Dispatcher for {@code /api/admin/mods}.
     *
     * <ul>
     *   <li>{@code GET} — list all {@code .jar} files in {@code mods/} with name, size,
     *       last-modified timestamp, and an {@code active} flag for the running JAR.</li>
     *   <li>{@code DELETE} — remove a named JAR. Request body: {@code {"filename":"foo.jar"}}.</li>
     * </ul>
     *
     * <p>Requires admin auth ({@code ADMIN_ENABLED} + valid token).
     */
    public static String handleMods(String method, Map<String, String> headers, String body) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");

        if ("GET".equals(method))    return handleModsList(headers);
        if ("DELETE".equals(method)) return handleModsDelete(body);
        return err(405, "Method not allowed");
    }

    /**
     * Lists all {@code .jar} files in {@code mods/}, sorted alphabetically.
     * Each entry includes {@code name}, {@code size} (bytes), {@code modified} (epoch ms),
     * and {@code active} ({@code true} if this is the currently running QuackedSMP JAR).
     */
    private static String handleModsList(Map<String, String> headers) {
        try {
            Path modsDir = Path.of("mods").toAbsolutePath().normalize();
            if (!Files.isDirectory(modsDir)) return err(503, "Mods directory not found");

            // Determine active JAR filename for badge display in the UI
            String activeJarName = "";
            try {
                Optional<Path> ap = mc.smpessentials.platform.SmpServices.PLATFORM.getActiveModJarPath();
                if (ap.isPresent()) activeJarName = ap.get().getFileName().toString();
            } catch (Exception ignored) {}
            final String activeJar = activeJarName;

            List<Path> jars;
            try (var dirStream = Files.list(modsDir)) {
                jars = dirStream
                        .filter(p -> p.getFileName().toString().endsWith(".jar"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .collect(java.util.stream.Collectors.toList());
            }

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < jars.size(); i++) {
                if (i > 0) sb.append(',');
                Path jar = jars.get(i);
                String name     = jar.getFileName().toString();
                long   size     = Files.size(jar);
                long   modified = Files.getLastModifiedTime(jar).toMillis();
                boolean isActive = !activeJar.isEmpty() && activeJar.equals(name);
                sb.append(String.format(Locale.US,
                        "{\"name\":\"%s\",\"size\":%d,\"modified\":%d,\"active\":%b}",
                        jsonEscape(name), size, modified, isActive));
            }
            sb.append(']');
            return sb.toString();
        } catch (Exception e) {
            return err(500, "Failed to list mods: " + jsonEscape(e.getMessage()));
        }
    }

    /**
     * Deletes a single JAR from {@code mods/}.
     *
     * <p>Security checks (all enforced before touching the filesystem):
     * <ul>
     *   <li>Filename must end with {@code .jar}</li>
     *   <li>Filename must not contain {@code /}, {@code \}, or {@code ..}</li>
     *   <li>Resolved path must be inside {@code mods/} (path-traversal guard)</li>
     * </ul>
     */
    private static String handleModsDelete(String body) {
        try {
            JsonObject req      = JsonParser.parseString(body).getAsJsonObject();
            String     filename = req.get("filename").getAsString();

            if (!filename.endsWith(".jar"))
                return err(400, "Only .jar files can be deleted");
            if (filename.contains("/") || filename.contains("\\") || filename.contains(".."))
                return err(400, "Invalid filename");

            Path modsDir = Path.of("mods").toAbsolutePath().normalize();
            Path target  = modsDir.resolve(filename).normalize();
            if (!target.startsWith(modsDir)) return err(400, "Invalid filename");
            if (!Files.exists(target))       return err(404, "Mod not found: " + jsonEscape(filename));

            // Block deleting the currently running JAR
            try {
                Optional<Path> active = mc.smpessentials.platform.SmpServices.PLATFORM.getActiveModJarPath();
                if (active.isPresent() && active.get().toAbsolutePath().normalize().equals(target))
                    return err(400, "Cannot delete the active mod JAR");
            } catch (Exception ignored) {}

            Files.delete(target);
            SmpUtilsMod.LOGGER.info("[ModManager] Deleted mod: {}", filename);
            return "{\"ok\":true}";
        } catch (Exception e) {
            return err(500, "Failed to delete: " + jsonEscape(e.getMessage()));
        }
    }

    /**
     * {@code POST /api/admin/mods/upload} — streams a JAR into {@code mods/}.
     *
     * <p>This handler receives the raw {@link InputStream} via the upload route path,
     * bypassing the normal 64 KB body cap. The file is written to a temp file inside
     * {@code mods/} first, then atomically moved to the final destination on success.
     * The temp file is always cleaned up in the {@code finally} block.
     *
     * <p>Required headers:
     * <ul>
     *   <li>{@code X-Filename} — target filename (must end in {@code .jar}, no path components)</li>
     *   <li>{@code Content-Length} — exact byte count (required; upload rejected if absent or 0)</li>
     * </ul>
     *
     * <p>Limits: 100 MB max. Requires admin auth.
     */
    public static String handleModsUpload(String method, Map<String, String> headers,
                                           InputStream stream, long contentLength) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (contentLength <= 0)                 return err(400, "Content-Length required");

        final long maxBytes = 100L * 1024 * 1024; // 100 MB
        if (contentLength > maxBytes)           return err(413, "File too large (max 100 MB)");

        String filename = headers.getOrDefault("x-filename", "").trim();
        if (filename.isEmpty())                 return err(400, "X-Filename header required");
        if (!filename.endsWith(".jar"))         return err(400, "Only .jar files are allowed");
        if (filename.contains("/") || filename.contains("\\") || filename.contains(".."))
            return err(400, "Invalid filename");

        Path modsDir = Path.of("mods").toAbsolutePath().normalize();
        if (!Files.isDirectory(modsDir))        return err(503, "Mods directory not found");

        Path target = modsDir.resolve(filename).normalize();
        if (!target.startsWith(modsDir))        return err(400, "Invalid filename");

        Path temp = null;
        try {
            temp = Files.createTempFile(modsDir, "upload-", ".tmp");
            // Stream exactly contentLength bytes into the temp file
            try (OutputStream fileOut = Files.newOutputStream(temp)) {
                byte[] buf       = new byte[65_536];
                long   remaining = contentLength;
                while (remaining > 0) {
                    int toRead = (int) Math.min(buf.length, remaining);
                    int n      = stream.read(buf, 0, toRead);
                    if (n == -1) break;
                    fileOut.write(buf, 0, n);
                    remaining -= n;
                }
                if (remaining > 0) {
                    return err(400, "Upload incomplete");
                }
            }

            long actual = Files.size(temp);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null; // successfully moved — skip deletion in finally
            SmpUtilsMod.LOGGER.info("[ModManager] Uploaded mod: {} ({} bytes)", filename, actual);
            return String.format(Locale.US, "{\"ok\":true,\"filename\":\"%s\",\"size\":%d}",
                    jsonEscape(filename), actual);
        } catch (Exception e) {
            SmpUtilsMod.LOGGER.warn("[ModManager] Upload failed: {}", e.getMessage());
            return err(500, "Upload failed: " + jsonEscape(e.getMessage()));
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (Exception ignored) {}
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

    /** Parses a single query-string parameter value from a raw query string. */
    private static String queryParam(String queryString, String key) {
        if (queryString == null || queryString.isEmpty()) return "";
        for (String part : queryString.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(key)) {
                try {
                    return java.net.URLDecoder.decode(part.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return part.substring(eq + 1);
                }
            }
        }
        return "";
    }
}
