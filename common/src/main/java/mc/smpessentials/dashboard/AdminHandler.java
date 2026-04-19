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
import net.minecraft.world.level.Level;
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

// Handles all /api/admin/* HTTP routes, registered by DashboardManager.
public final class AdminHandler {

    private AdminHandler() {}

    // ── Route handlers (called by DashboardServer) ────────────────────────────

    // GET /api/admin/status. Public, no auth. Returns whether admin is enabled and whether a password is set.
    public static String handleStatus(String method, Map<String, String> headers, String body) {
        boolean enabled     = SmpConfig.ADMIN_ENABLED;
        boolean hasPassword = !SmpConfig.ADMIN_PASSWORD_HASH.isBlank();
        return String.format("{\"enabled\":%b,\"hasPassword\":%b}", enabled, hasPassword);
    }

    // POST /api/admin/setup. Sets the initial password; only works when none is stored yet.
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

    // POST /api/admin/logout. Invalidates the caller's session token immediately.
    public static String handleLogout(String method, Map<String, String> headers, String body) {
        if (!"POST".equals(method)) return err(405, "Method not allowed");
        String auth = headers.get("authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            AdminAuth.invalidateSession(auth.substring(7).trim());
        }
        return "{\"ok\":true}";
    }

    // POST /api/admin/login. Verifies password and returns a session token.
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

    // GET /api/admin/players. Returns a list of currently online players.
    public static String handlePlayers(String method, Map<String, String> headers, String body,
                                       MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)                   return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))           return err(403, "Unauthorized");
        if (server == null)                             return err(503, "Server not ready");

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            boolean isOp       = server.getPlayerList().isOp(p.nameAndId());
            String  dim        = p.level().dimension().identifier().toString();
            String  name       = jsonEscape(p.getGameProfile().name());
            String  uuid       = p.getUUID().toString();
            int     playtime     = p.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
            int     tier         = mc.smpessentials.tier.TierService.getTier(p.getUUID(), server);
            int     earnedTier   = mc.smpessentials.tier.TierService.getEarnedTier(p);
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.US,
                    "{\"name\":\"%s\",\"uuid\":\"%s\",\"dimension\":\"%s\",\"isOp\":%b,\"playtime_ticks\":%d,\"tier\":%d,\"earned_tier\":%d}",
                    name, uuid, dim, isOp, playtime, tier, earnedTier));
        }
        sb.append(']');
        return sb.toString();
    }

    // POST /api/admin/exec. Runs a server command on the server thread asynchronously; returns immediately.
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

    // GET /api/admin/config. Returns all editable config fields.
    public static String handleConfigGet(String method, Map<String, String> headers, String body) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");

        StringBuilder sb = new StringBuilder("{");
        // General
        sb.append(String.format("\"max_claims\":%d,", SmpConfig.MAX_CLAIMS));
        sb.append(String.format("\"tp_warmup\":%d,", SmpConfig.TP_WARMUP));
        sb.append(String.format("\"welcome_message\":\"%s\",", jsonEscape(SmpConfig.WELCOME_MESSAGE)));
        sb.append(String.format("\"message_interval\":%d,", SmpConfig.MESSAGE_INTERVAL));
        sb.append(String.format("\"allow_lava_wilderness\":%b,", SmpConfig.ALLOW_LAVA_WILDERNESS));
        sb.append(String.format("\"allow_fire_wilderness\":%b,", SmpConfig.ALLOW_FIRE_WILDERNESS));
        sb.append(String.format("\"spawn_no_pvp\":%b,", SmpConfig.SPAWN_NO_PVP));
        // Feature toggles
        sb.append(String.format("\"claims_enabled\":%b,", SmpConfig.CLAIMS_ENABLED));
        sb.append(String.format("\"skills_enabled\":%b,", SmpConfig.SKILLS_ENABLED));
        sb.append(String.format("\"chatfilter_enabled\":%b,", SmpConfig.CHATFILTER_ENABLED));
        sb.append(String.format("\"shops_enabled\":%b,", SmpConfig.SHOPS_ENABLED));
        sb.append(String.format("\"economy_enabled\":%b,", SmpConfig.ECONOMY_ENABLED));
        // Hardcore
        sb.append(String.format("\"hardcore_enabled\":%b,", SmpConfig.HARDCORE_ENABLED));
        sb.append(String.format("\"hardcore_death_percent\":%d,", SmpConfig.HARDCORE_DEATH_PERCENT));
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
        sb.append("\"vote_vip_rewards\":{");
        boolean firstVip = true;
        for (var entry : SmpConfig.VOTE_VIP_REWARDS.entrySet()) {
            if (!firstVip) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(jsonStrArr(entry.getValue()));
            firstVip = false;
        }
        sb.append("},");
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
        // Tiers
        sb.append("\"tiers\":[");
        for (int i = 0; i < SmpConfig.TIERS.size(); i++) {
            if (i > 0) sb.append(',');
            SmpConfig.TierDef def = SmpConfig.TIERS.get(i);
            sb.append(String.format("{\"tier\":%d,\"name\":\"%s\",\"minPlaytimeHours\":%d,\"bonusClaims\":%d}",
                    def.tier(), jsonEscape(def.name()), def.minPlaytimeHours(), def.bonusClaims()));
        }
        sb.append("],");
        // Lists
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
        sb.append(String.format("\"cap_safe_landing\":%.2f,", SmpConfig.CAP_SAFE_LANDING));
        // Kits
        sb.append(String.format("\"kits_enabled\":%b,", SmpConfig.KITS_ENABLED));
        sb.append(String.format("\"kit_cooldown_seconds\":%d,", SmpConfig.KIT_COOLDOWN_SECONDS));
        sb.append("\"kit_definitions\":[");
        for (int i = 0; i < SmpConfig.KIT_DEFINITIONS.size(); i++) {
            if (i > 0) sb.append(',');
            var kit = SmpConfig.KIT_DEFINITIONS.get(i);
            sb.append(String.format("{\"name\":\"%s\",\"displayName\":\"%s\",\"minTier\":%d,",
                    jsonEscape(kit.name), jsonEscape(kit.displayName), kit.minTier));
            sb.append(String.format("\"armor\":{\"head\":\"%s\",\"chest\":\"%s\",\"legs\":\"%s\",\"feet\":\"%s\"},",
                    jsonEscape(kit.armor.head), jsonEscape(kit.armor.chest),
                    jsonEscape(kit.armor.legs), jsonEscape(kit.armor.feet)));
            sb.append("\"items\":[");
            for (int j = 0; j < kit.items.size(); j++) {
                if (j > 0) sb.append(',');
                var item = kit.items.get(j);
                sb.append(String.format("{\"item\":\"%s\",\"count\":%d}", jsonEscape(item.item), item.count));
            }
            sb.append("]}");
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    // POST /api/admin/config. Applies a partial config patch and hot-reloads. Port changes require restart.
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
            if (patch.has("allow_lava_wilderness")) { SmpConfig.ALLOW_LAVA_WILDERNESS = patch.get("allow_lava_wilderness").getAsBoolean(); changed++; }
            if (patch.has("allow_fire_wilderness")) { SmpConfig.ALLOW_FIRE_WILDERNESS = patch.get("allow_fire_wilderness").getAsBoolean(); changed++; }
            if (patch.has("spawn_no_pvp"))          { SmpConfig.SPAWN_NO_PVP          = patch.get("spawn_no_pvp").getAsBoolean();          changed++; }
            // Feature toggles
            if (patch.has("claims_enabled"))     { SmpConfig.CLAIMS_ENABLED     = patch.get("claims_enabled").getAsBoolean();     changed++; }
            if (patch.has("skills_enabled"))     { SmpConfig.SKILLS_ENABLED     = patch.get("skills_enabled").getAsBoolean();     changed++; }
            if (patch.has("chatfilter_enabled")) { SmpConfig.CHATFILTER_ENABLED = patch.get("chatfilter_enabled").getAsBoolean(); changed++; }
            if (patch.has("shops_enabled"))     { SmpConfig.SHOPS_ENABLED     = patch.get("shops_enabled").getAsBoolean();     changed++; }
            if (patch.has("economy_enabled"))   { SmpConfig.ECONOMY_ENABLED   = patch.get("economy_enabled").getAsBoolean();   changed++; }
            // Hardcore
            if (patch.has("hardcore_enabled"))        { SmpConfig.HARDCORE_ENABLED        = patch.get("hardcore_enabled").getAsBoolean();       changed++; }
            if (patch.has("hardcore_death_percent"))   { SmpConfig.HARDCORE_DEATH_PERCENT   = patch.get("hardcore_death_percent").getAsInt();     changed++; }
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
            if (patch.has("vote_vip_rewards") && patch.get("vote_vip_rewards").isJsonObject()) {
                SmpConfig.VOTE_VIP_REWARDS.clear();
                for (var entry : patch.getAsJsonObject("vote_vip_rewards").entrySet()) {
                    try {
                        int t = Integer.parseInt(entry.getKey());
                        if (entry.getValue().isJsonArray()) {
                            java.util.List<String> cmds = new java.util.ArrayList<>();
                            for (var el : entry.getValue().getAsJsonArray()) cmds.add(el.getAsString());
                            SmpConfig.VOTE_VIP_REWARDS.put(t, cmds);
                        }
                    } catch (NumberFormatException ignored) {}
                }
                changed++;
            }
            if (patch.has("tiers") && patch.get("tiers").isJsonArray()) {
                SmpConfig.TIERS.clear();
                for (var el : patch.getAsJsonArray("tiers")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject td = el.getAsJsonObject();
                    int tdTier    = td.has("tier")              ? td.get("tier").getAsInt()              : 0;
                    String tdName = td.has("name")              ? td.get("name").getAsString()            : "";
                    long tdHours  = td.has("minPlaytimeHours")  ? td.get("minPlaytimeHours").getAsLong()  : 0;
                    int tdBonus   = td.has("bonusClaims")       ? td.get("bonusClaims").getAsInt()        : 0;
                    if (tdTier > 0) SmpConfig.TIERS.add(new SmpConfig.TierDef(tdTier, tdName, tdHours, tdBonus));
                }
                changed++;
            }
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
            // Kits
            if (patch.has("kits_enabled"))         { SmpConfig.KITS_ENABLED         = patch.get("kits_enabled").getAsBoolean();       changed++; }
            if (patch.has("kit_cooldown_seconds"))  { SmpConfig.KIT_COOLDOWN_SECONDS  = patch.get("kit_cooldown_seconds").getAsLong();  changed++; }
            if (patch.has("kit_definitions") && patch.get("kit_definitions").isJsonArray()) {
                SmpConfig.KIT_DEFINITIONS.clear();
                for (var el : patch.getAsJsonArray("kit_definitions")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject ko = el.getAsJsonObject();
                    var kit = new mc.smpessentials.config.ConfigData.KitDef();
                    kit.name        = ko.has("name")        ? ko.get("name").getAsString()        : "";
                    kit.displayName = ko.has("displayName") ? ko.get("displayName").getAsString() : "";
                    kit.minTier     = ko.has("minTier")     ? ko.get("minTier").getAsInt()        : 0;
                    if (ko.has("armor") && ko.get("armor").isJsonObject()) {
                        JsonObject ao = ko.getAsJsonObject("armor");
                        kit.armor.head  = ao.has("head")  ? ao.get("head").getAsString()  : "";
                        kit.armor.chest = ao.has("chest") ? ao.get("chest").getAsString() : "";
                        kit.armor.legs  = ao.has("legs")  ? ao.get("legs").getAsString()  : "";
                        kit.armor.feet  = ao.has("feet")  ? ao.get("feet").getAsString()  : "";
                    }
                    if (ko.has("items") && ko.get("items").isJsonArray()) {
                        kit.items.clear();
                        for (var ie : ko.getAsJsonArray("items")) {
                            if (!ie.isJsonObject()) continue;
                            JsonObject itemObj = ie.getAsJsonObject();
                            String itemId = itemObj.has("item")  ? itemObj.get("item").getAsString() : "";
                            int count     = itemObj.has("count") ? itemObj.get("count").getAsInt()   : 1;
                            kit.items.add(new mc.smpessentials.config.ConfigData.KitItem(itemId, count));
                        }
                    }
                    if (!kit.name.isBlank()) SmpConfig.KIT_DEFINITIONS.add(kit);
                }
                changed++;
            }

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

    // POST /api/admin/dashboard/disable. Sets dashboard_enabled=false and shuts down the server after responding.
    public static String handleDashboardDisable(String method, Map<String, String> headers, String body) {
        if (!"POST".equals(method))           return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return err(403, "Unauthorized");
        DashboardManager.scheduleDisable();
        return "{\"ok\":true}";
    }

    // POST /api/admin/setop. Edits ops.json directly; levels 1-3 have no vanilla command. Reload is dispatched to the server thread.
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

    // GET /api/admin/dims. Lists all non-vanilla dimensions from the live registry, including datapack-added ones. Vanilla dims excluded.
    public static String handleDimsGet(String method, Map<String, String> headers, String body,
                                       MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");

        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                DimSavedData savedData = DimSavedData.get(server);
                List<ResourceKey<Level>> allLevels = DimManager.listAll(server);
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (ResourceKey<Level> key : allLevels) {
                    if (key.equals(Level.OVERWORLD) || key.equals(Level.NETHER) || key.equals(Level.END)) continue;
                    if (!first) sb.append(',');
                    first = false;
                    String id = key.identifier().toString();
                    Optional<DimSavedData.DimEntry> entry = savedData.getEntry(id);
                    sb.append("{\"id\":\"").append(jsonEscape(id)).append('"');
                    sb.append(",\"generatorType\":\"").append(jsonEscape(entry.map(DimSavedData.DimEntry::generatorType).orElse("external"))).append('"');
                    sb.append(",\"generatorConfig\":");
                    entry.flatMap(DimSavedData.DimEntry::generatorConfig).ifPresentOrElse(
                        c -> sb.append('"').append(jsonEscape(c)).append('"'),
                        () -> sb.append("null"));
                    sb.append(",\"portalBlock\":");
                    entry.flatMap(DimSavedData.DimEntry::portalBlock).ifPresentOrElse(
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

    // POST /api/admin/dims/create. Creates a custom dimension. Body: {id, type, config (optional)}.
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

    // POST /api/admin/dims/delete. Deletes a custom dimension. Body: {id}.
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

    // POST /api/admin/dims/setportal. Assigns a portal frame block to a custom dimension. Body: {dimId, blockId}.
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

    // GET /api/admin/blocks. Returns sorted array of all registered block IDs for portal-block autocomplete.
    public static String handleBlocksGet(String method, Map<String, String> headers, String body) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");

        List<String> ids = BuiltInRegistries.BLOCK.keySet().stream()
                .map(Object::toString)
                .sorted()
                .collect(Collectors.toList());
        return jsonStrArr(ids);
    }

    // GET /api/admin/biomes. Returns sorted array of all registered biome IDs from the dynamic registry.
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

    // GET /api/skills/leaderboard. Public, no auth. Returns overall top-10 and per-skill top-5.
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
                    String playerName = jsonEscape(data.getDisplayName(e.uuid()));
                    ServerPlayer online = server.getPlayerList().getPlayer(e.uuid());
                    int tier = mc.smpessentials.tier.TierService.getTier(e.uuid(), server);
                    if (online != null) {
                        int playtime = online.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
                        sb.append(String.format("{\"name\":\"%s\",\"level\":%d,\"playtime_ticks\":%d,\"tier\":%d}",
                                playerName, e.level(), playtime, tier));
                    } else {
                        sb.append(String.format("{\"name\":\"%s\",\"level\":%d,\"tier\":%d}",
                                playerName, e.level(), tier));
                    }
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

    // GET /api/admin/skills/players. Returns all players with skill data, sorted by total level desc.
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

    // GET /api/admin/skills?player=<uuid_or_name>. Returns full skill data for a single player.
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

    // POST /api/admin/skills/set. Sets a player's level for a specific skill. Body: {uuid, skill, level}.
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

    // ── Tiers ─────────────────────────────────────────────────────────────────

    // POST /api/admin/players/settier. Assigns or clears an admin tier for a player by name.
    // The dashboard player list only shows online players, so the player is guaranteed online.
    public static String handleSetTier(String method, Map<String, String> headers, String body,
                                       MinecraftServer server) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String name = req.get("name").getAsString().strip();
            int tier    = req.get("tier").getAsInt();
            if (tier < 0) return err(400, "Tier must be >= 0 (0 clears assignment)");
            if (tier > 0 && SmpConfig.TIERS.stream().noneMatch(t -> t.tier() == tier)) {
                return err(400, "Tier " + tier + " is not defined in config");
            }

            java.util.concurrent.CompletableFuture<String> future = new java.util.concurrent.CompletableFuture<>();
            server.execute(() -> {
                try {
                    net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayerByName(name);
                    if (player == null) {
                        future.complete(err(400, "Player not online"));
                        return;
                    }
                    mc.smpessentials.tier.PlayerTierData.get(server).set(player.getUUID(), tier);
                    future.complete(String.format("{\"ok\":true,\"name\":\"%s\",\"tier\":%d}", jsonEscape(name), tier));
                } catch (Exception ex) {
                    future.complete(err(500, jsonEscape(ex.getMessage())));
                }
            });
            return future.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return err(400, "Invalid request: " + jsonEscape(e.getMessage()));
        }
    }

    // ── Claims ────────────────────────────────────────────────────────────────

    // GET /api/admin/claims. Returns claim counts per player, sorted by count desc.
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

    // POST /api/admin/claims/unclaim. Removes all claims owned by a player. Body: {uuid}.
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

    // GET /api/admin/chatfilter. Paginated word list. Query params: q, page, size, tab (blocked|whitelist).
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

    // POST /api/admin/chatfilter/add. Bulk adds words to the blocked list or whitelist. Body: {words, whitelist}.
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

    // POST /api/admin/chatfilter/remove. Bulk removes words from the blocked list or whitelist. Body: {words, whitelist}.
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

    // GET /api/admin/chatfilter/mutes. Returns all currently active mutes; expired ones excluded.
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

    // POST /api/admin/chatfilter/unmute. Immediately unmutes a player. Body: {uuid}.
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

    // GET /api/admin/mods: lists .jar files with name, size, modified, and active flag.
    // DELETE /api/admin/mods: removes a named JAR. Body: {filename}.
    public static String handleMods(String method, Map<String, String> headers, String body) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");

        if ("GET".equals(method))    return handleModsList(headers);
        if ("DELETE".equals(method)) return handleModsDelete(body);
        return err(405, "Method not allowed");
    }

    // Lists all .jar files in mods/, sorted alphabetically, with name, size, modified ms, and active flag.
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

    // Deletes a single JAR from mods/. Validates filename ends with .jar, has no path components, and resolves inside mods/.
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

    // POST /api/admin/mods/upload. Streams a JAR into mods/ via temp file then atomic move. Max 100 MB. Requires X-Filename and Content-Length headers.
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

    // GET /api/admin/hardcore. Lists all active hardcore sessions.
    public static String handleHardcoreGet(String method, Map<String, String> headers, String body,
                                            MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)         return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return err(403, "Unauthorized");

        var data = mc.smpessentials.hardcore.HardcoreSavedData.get(server);
        StringBuilder sb = new StringBuilder("{\"sessions\":[");
        boolean first = true;
        for (var session : data.allSessions()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(String.format(
                    "{\"name\":\"%s\",\"alive\":%d,\"dead\":%d,\"peak\":%d,\"deaths\":%d,\"threshold\":%d,"
                            + "\"start\":\"%d, %d, %d\",\"dim\":\"%s\"}",
                    jsonEscape(session.getName()),
                    session.getAliveCount(), session.getDeadCount(),
                    session.getPeakPlayers(), session.getDeaths(), session.getThreshold(),
                    session.getStartX(), session.getStartY(), session.getStartZ(),
                    jsonEscape(session.getStartDim())));
        }
        sb.append("]}");
        return sb.toString();
    }

    // POST /api/admin/hardcore/end. Force-ends a session by name. Body: {name}.
    public static String handleHardcoreEnd(String method, Map<String, String> headers, String body,
                                            MinecraftServer server) {
        if (!"POST".equals(method))           return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)         return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return err(403, "Unauthorized");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String name = req.get("name").getAsString();
            var data = mc.smpessentials.hardcore.HardcoreSavedData.get(server);

            // Run on server thread since it modifies player state
            server.execute(() -> {
                String error = data.forceEndSession(name, server);
                if (error != null) {
                    SmpUtilsMod.LOGGER.warn("[Hardcore] Force-end failed: {}", error);
                }
            });
            return "{\"ok\":true}";
        } catch (Exception e) {
            return err(400, "Invalid request: " + jsonEscape(e.getMessage()));
        }
    }

    // GET /api/admin/regen — check pending status.
    // POST /api/admin/regen — queue wilderness regen.
    // DELETE /api/admin/regen — cancel pending regen.
    public static String handleRegen(String method, Map<String, String> headers, String body,
                                     MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)         return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers)) return err(403, "Unauthorized");

        if ("GET".equals(method)) {
            boolean pending = mc.smpessentials.regen.ChunkRegenManager.isPending(server);
            return String.format("{\"pending\":%b}", pending);
        }

        if ("POST".equals(method)) {
            try {
                mc.smpessentials.regen.ChunkRegenManager.queueRegen(server);
                return "{\"ok\":true}";
            } catch (Exception e) {
                return err(500, "Failed to queue regen: " + jsonEscape(e.getMessage()));
            }
        }

        if ("DELETE".equals(method)) {
            try {
                mc.smpessentials.regen.ChunkRegenManager.cancelRegen(server);
                return "{\"ok\":true}";
            } catch (Exception e) {
                return err(500, "Failed to cancel regen: " + jsonEscape(e.getMessage()));
            }
        }

        return err(405, "Method not allowed");
    }

    // ── Teams ─────────────────────────────────────────────────────────────────

    // GET /api/admin/teams. Returns all scoreboard teams with settings and members.
    public static String handleTeamsGet(String method, Map<String, String> headers, String body,
                                        MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");

        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                var scoreboard = server.getScoreboard();
                var teams = scoreboard.getPlayerTeams();
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (var team : teams) {
                    if (!first) sb.append(',');
                    first = false;
                    sb.append('{');
                    sb.append("\"name\":\"").append(jsonEscape(team.getName())).append('"');
                    sb.append(",\"displayName\":\"").append(jsonEscape(team.getDisplayName().getString())).append('"');
                    sb.append(",\"color\":\"").append(jsonEscape(team.getColor().getName())).append('"');
                    sb.append(",\"friendlyFire\":").append(team.isAllowFriendlyFire());
                    sb.append(",\"seeFriendlyInvisibles\":").append(team.canSeeFriendlyInvisibles());
                    sb.append(",\"nameTagVisibility\":\"").append(jsonEscape(team.getNameTagVisibility().name)).append('"');
                    sb.append(",\"deathMessageVisibility\":\"").append(jsonEscape(team.getDeathMessageVisibility().name)).append('"');
                    sb.append(",\"collisionRule\":\"").append(jsonEscape(team.getCollisionRule().name)).append('"');
                    sb.append(",\"prefix\":\"").append(jsonEscape(team.getPlayerPrefix().getString())).append('"');
                    sb.append(",\"prefixColor\":\"").append(jsonEscape(extractChatFormatting(team.getPlayerPrefix()))).append('"');
                    sb.append(",\"suffix\":\"").append(jsonEscape(team.getPlayerSuffix().getString())).append('"');
                    sb.append(",\"suffixColor\":\"").append(jsonEscape(extractChatFormatting(team.getPlayerSuffix()))).append('"');
                    sb.append(",\"players\":[");
                    boolean pFirst = true;
                    for (String player : team.getPlayers()) {
                        if (!pFirst) sb.append(',');
                        pFirst = false;
                        sb.append('"').append(jsonEscape(player)).append('"');
                    }
                    sb.append("]}");
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
            return err(500, "Timeout reading teams");
        }
    }

    // POST /api/admin/teams/create. Creates a new team. Body: {name}.
    public static String handleTeamCreate(String method, Map<String, String> headers, String body,
                                          MinecraftServer server) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String name = req.get("name").getAsString().strip();
            if (name.isEmpty() || name.length() > 16)
                return err(400, "Team name must be 1-16 characters");
            if (!name.matches("[a-zA-Z0-9._+-]+"))
                return err(400, "Team name may only contain letters, numbers, dots, underscores, plus, and hyphens");

            CompletableFuture<String> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    var scoreboard = server.getScoreboard();
                    if (scoreboard.getPlayerTeam(name) != null) {
                        future.complete(err(400, "Team '" + jsonEscape(name) + "' already exists"));
                        return;
                    }
                    scoreboard.addPlayerTeam(name);
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

    // POST /api/admin/teams/update. Modifies team settings. Body: {name, ...fields}.
    public static String handleTeamUpdate(String method, Map<String, String> headers, String body,
                                          MinecraftServer server) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String name = req.get("name").getAsString().strip();

            CompletableFuture<String> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    var scoreboard = server.getScoreboard();
                    var team = scoreboard.getPlayerTeam(name);
                    if (team == null) {
                        future.complete(err(404, "Team not found"));
                        return;
                    }
                    if (req.has("displayName"))
                        team.setDisplayName(net.minecraft.network.chat.Component.literal(
                                req.get("displayName").getAsString()));
                    if (req.has("color")) {
                        var cf = net.minecraft.ChatFormatting.getByName(req.get("color").getAsString());
                        if (cf != null) team.setColor(cf);
                    }
                    if (req.has("friendlyFire"))
                        team.setAllowFriendlyFire(req.get("friendlyFire").getAsBoolean());
                    if (req.has("seeFriendlyInvisibles"))
                        team.setSeeFriendlyInvisibles(req.get("seeFriendlyInvisibles").getAsBoolean());
                    if (req.has("nameTagVisibility")) {
                        var v = parseVisibility(req.get("nameTagVisibility").getAsString());
                        if (v != null) team.setNameTagVisibility(v);
                    }
                    if (req.has("deathMessageVisibility")) {
                        var v = parseVisibility(req.get("deathMessageVisibility").getAsString());
                        if (v != null) team.setDeathMessageVisibility(v);
                    }
                    if (req.has("collisionRule")) {
                        var c = parseCollisionRule(req.get("collisionRule").getAsString());
                        if (c != null) team.setCollisionRule(c);
                    }
                    if (req.has("prefix") || req.has("prefixColor")) {
                        String pText = req.has("prefix")
                                ? req.get("prefix").getAsString()
                                : team.getPlayerPrefix().getString();
                        var pComp = net.minecraft.network.chat.Component.literal(pText);
                        if (req.has("prefixColor")) {
                            var pcf = net.minecraft.ChatFormatting.getByName(req.get("prefixColor").getAsString());
                            if (pcf != null && pcf.isColor()) pComp = pComp.withStyle(pcf);
                        } else {
                            var existing = team.getPlayerPrefix().getStyle().getColor();
                            if (existing != null)
                                pComp = pComp.withStyle(
                                        net.minecraft.network.chat.Style.EMPTY.withColor(existing));
                        }
                        team.setPlayerPrefix(pComp);
                    }
                    if (req.has("suffix") || req.has("suffixColor")) {
                        String sText = req.has("suffix")
                                ? req.get("suffix").getAsString()
                                : team.getPlayerSuffix().getString();
                        var sComp = net.minecraft.network.chat.Component.literal(sText);
                        if (req.has("suffixColor")) {
                            var scf = net.minecraft.ChatFormatting.getByName(req.get("suffixColor").getAsString());
                            if (scf != null && scf.isColor()) sComp = sComp.withStyle(scf);
                        } else {
                            var existing = team.getPlayerSuffix().getStyle().getColor();
                            if (existing != null)
                                sComp = sComp.withStyle(
                                        net.minecraft.network.chat.Style.EMPTY.withColor(existing));
                        }
                        team.setPlayerSuffix(sComp);
                    }
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

    // POST /api/admin/teams/delete. Removes a team. Body: {name}.
    public static String handleTeamDelete(String method, Map<String, String> headers, String body,
                                          MinecraftServer server) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String name = req.get("name").getAsString().strip();

            CompletableFuture<String> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    var scoreboard = server.getScoreboard();
                    var team = scoreboard.getPlayerTeam(name);
                    if (team == null) {
                        future.complete(err(404, "Team not found"));
                        return;
                    }
                    scoreboard.removePlayerTeam(team);
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

    // POST /api/admin/teams/addplayer. Adds a player to a team. Body: {team, player}.
    public static String handleTeamAddPlayer(String method, Map<String, String> headers, String body,
                                             MinecraftServer server) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String teamName  = req.get("team").getAsString().strip();
            String player    = req.get("player").getAsString().strip();
            if (player.isEmpty()) return err(400, "Player name required");
            if (!isKnownPlayer(player, server))
                return err(400, "Unknown player: " + jsonEscape(player));

            CompletableFuture<String> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    var scoreboard = server.getScoreboard();
                    var team = scoreboard.getPlayerTeam(teamName);
                    if (team == null) {
                        future.complete(err(404, "Team not found"));
                        return;
                    }
                    scoreboard.addPlayerToTeam(player, team);
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

    // POST /api/admin/teams/removeplayer. Removes a player from their team. Body: {player}.
    public static String handleTeamRemovePlayer(String method, Map<String, String> headers, String body,
                                                MinecraftServer server) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String player = req.get("player").getAsString().strip();
            if (player.isEmpty()) return err(400, "Player name required");

            CompletableFuture<String> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    var scoreboard = server.getScoreboard();
                    if (!scoreboard.removePlayerFromTeam(player)) {
                        future.complete(err(400, "Player is not on any team"));
                        return;
                    }
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

    // GET /api/admin/dims/all. Returns ALL dimensions including vanilla (overworld, nether, end).
    public static String handleDimsAll(String method, Map<String, String> headers, String body,
                                       MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");

        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                List<ResourceKey<Level>> allLevels = DimManager.listAll(server);
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (ResourceKey<Level> key : allLevels) {
                    if (!first) sb.append(',');
                    first = false;
                    sb.append('"').append(jsonEscape(key.identifier().toString())).append('"');
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

    // GET /api/admin/teams/autoassign. Returns current auto-assign config.
    public static String handleAutoAssignGet(String method, Map<String, String> headers, String body) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : SmpConfig.TEAM_AUTO_ASSIGN.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(jsonEscape(entry.getKey())).append("\":");
            sb.append(jsonStrArr(entry.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    // POST /api/admin/teams/autoassign. Updates auto-assign dims for a team.
    // Body: {team, dimensions: [...]}. Empty dimensions list removes auto-assign for that team.
    public static String handleAutoAssignPost(String method, Map<String, String> headers, String body,
                                              MinecraftServer server) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String teamName = req.get("team").getAsString().strip();
            if (teamName.isEmpty()) return err(400, "Team name required");

            List<String> dims = new ArrayList<>();
            if (req.has("dimensions")) {
                for (var el : req.getAsJsonArray("dimensions")) {
                    dims.add(el.getAsString().strip());
                }
            }

            // Validate no dimension is assigned to a different team
            for (String dim : dims) {
                for (var entry : SmpConfig.TEAM_AUTO_ASSIGN.entrySet()) {
                    if (!entry.getKey().equals(teamName) && entry.getValue().contains(dim)) {
                        return err(400, "Dimension " + dim + " is already assigned to team " + entry.getKey());
                    }
                }
            }

            if (dims.isEmpty()) {
                SmpConfig.TEAM_AUTO_ASSIGN.remove(teamName);
            } else {
                SmpConfig.TEAM_AUTO_ASSIGN.put(teamName, dims);
            }
            mc.smpessentials.teams.TeamAutoAssign.buildReverseMap();
            ConfigIO.save();
            return "{\"ok\":true}";
        } catch (Exception e) {
            return err(400, "Invalid request: " + jsonEscape(e.getMessage()));
        }
    }

    // Returns the ChatFormatting name for the color applied to a Component's style, or "reset" if none.
    private static String extractChatFormatting(net.minecraft.network.chat.Component comp) {
        var textColor = comp.getStyle().getColor();
        if (textColor == null) return "reset";
        for (var cf : net.minecraft.ChatFormatting.values()) {
            if (cf.isColor() && java.util.Objects.equals(
                    net.minecraft.network.chat.TextColor.fromLegacyFormat(cf), textColor)) {
                return cf.getName();
            }
        }
        return "reset";
    }

    private static net.minecraft.world.scores.Team.Visibility parseVisibility(String s) {
        for (var v : net.minecraft.world.scores.Team.Visibility.values()) {
            if (v.name.equals(s)) return v;
        }
        return null;
    }

    private static net.minecraft.world.scores.Team.CollisionRule parseCollisionRule(String s) {
        for (var c : net.minecraft.world.scores.Team.CollisionRule.values()) {
            if (c.name.equals(s)) return c;
        }
        return null;
    }

    // GET /api/admin/playernames. Returns sorted array of all known player names
    // from usercache.json merged with currently online players.
    public static String handlePlayerNames(String method, Map<String, String> headers, String body,
                                           MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        try {
            Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            Path cacheFile = Path.of("usercache.json");
            if (Files.exists(cacheFile)) {
                String raw = Files.readString(cacheFile);
                JsonArray arr = JsonParser.parseString(raw).getAsJsonArray();
                for (var el : arr) {
                    JsonObject entry = el.getAsJsonObject();
                    if (entry.has("name")) names.add(entry.get("name").getAsString());
                }
            }
            if (server != null) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    names.add(p.getGameProfile().name());
                }
            }
            return jsonStrArr(new ArrayList<>(names));
        } catch (Exception e) {
            return err(500, "Failed to read player cache: " + jsonEscape(e.getMessage()));
        }
    }

    // Returns true if the player name exists in usercache.json or is currently online.
    private static boolean isKnownPlayer(String name, MinecraftServer server) {
        // Check online first
        if (server.getPlayerList().getPlayerByName(name) != null) return true;
        // Check usercache
        try {
            Path cacheFile = Path.of("usercache.json");
            if (!Files.exists(cacheFile)) return false;
            String raw = Files.readString(cacheFile);
            JsonArray arr = JsonParser.parseString(raw).getAsJsonArray();
            for (var el : arr) {
                JsonObject entry = el.getAsJsonObject();
                if (entry.has("name") && entry.get("name").getAsString().equalsIgnoreCase(name))
                    return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ── Shops ─────────────────────────────────────────────────────────────────

    // GET /api/admin/shops. Lists all registered shops with live stock counts.
    public static String handleShopsGet(String method, Map<String, String> headers, String body,
                                        MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");

        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                var shops = mc.smpessentials.shops.ShopData.get(server).listAll();
                SkillData skills = SkillData.get(server.overworld());
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < shops.size(); i++) {
                    if (i > 0) sb.append(',');
                    var shop = shops.get(i);
                    ServerLevel sl = server.getLevel(shop.dimension());
                    int stock = sl != null ? mc.smpessentials.shops.ShopService.getStockCount(sl, shop) : 0;
                    sb.append(String.format(
                            "{\"x\":%d,\"y\":%d,\"z\":%d,\"dim\":\"%s\",\"owner\":\"%s\",\"ownerName\":\"%s\","
                                    + "\"item\":\"%s\",\"price\":%d,\"currency\":\"%s\",\"stock\":%s,\"spawnShop\":%b}",
                            shop.pos().getX(), shop.pos().getY(), shop.pos().getZ(),
                            jsonEscape(shop.dimension().identifier().toString()),
                            shop.owner(), jsonEscape(skills.getDisplayName(shop.owner())),
                            jsonEscape(shop.itemId()), shop.pricePerItem(),
                            jsonEscape(shop.currencyItemId()),
                            shop.spawnShop() ? "\"unlimited\"" : String.valueOf(stock),
                            shop.spawnShop()));
                }
                sb.append("]");
                future.complete(sb.toString());
            } catch (Exception ex) {
                future.complete(err(500, jsonEscape(ex.getMessage())));
            }
        });
        try { return future.get(5, TimeUnit.SECONDS); } catch (Exception e) {
            return err(500, "Timeout");
        }
    }

    // POST /api/admin/shops/delete. Body: {dim, x, y, z}
    public static String handleShopsDelete(String method, Map<String, String> headers, String body,
                                           MinecraftServer server) {
        if (!"POST".equals(method))             return err(405, "Method not allowed");
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (server == null)                     return err(503, "Server not ready");
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String dim = req.get("dim").getAsString();
            int x = req.get("x").getAsInt();
            int y = req.get("y").getAsInt();
            int z = req.get("z").getAsInt();
            var dimKey = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    Identifier.parse(dim));
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);

            CompletableFuture<String> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    boolean removed = mc.smpessentials.shops.ShopData.get(server).removeShop(dimKey, pos);
                    future.complete(String.format("{\"ok\":true,\"removed\":%b}", removed));
                } catch (Exception ex) {
                    future.complete(err(500, jsonEscape(ex.getMessage())));
                }
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return err(400, "Invalid request: " + jsonEscape(e.getMessage()));
        }
    }

    // GET /api/admin/economy. Lists all player balances.
    public static String handleEconomyGet(String method, Map<String, String> headers, String body,
                                          MinecraftServer server) {
        if (!SmpConfig.ADMIN_ENABLED)           return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))   return err(403, "Unauthorized");
        if (!SmpConfig.ECONOMY_ENABLED)         return err(200, "Economy disabled");
        if (server == null)                     return err(503, "Server not ready");

        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                var econ = mc.smpessentials.shops.EconomyData.get(server);
                SkillData skills = SkillData.get(server.overworld());
                var balances = econ.allBalances();
                StringBuilder sb = new StringBuilder("[");
                int i = 0;
                for (var entry : balances.entrySet()) {
                    if (i > 0) sb.append(',');
                    sb.append(String.format("{\"uuid\":\"%s\",\"name\":\"%s\",\"balance\":%d}",
                            entry.getKey(), jsonEscape(skills.getDisplayName(entry.getKey())), entry.getValue()));
                    i++;
                }
                sb.append("]");
                future.complete(sb.toString());
            } catch (Exception ex) {
                future.complete(err(500, jsonEscape(ex.getMessage())));
            }
        });
        try { return future.get(5, TimeUnit.SECONDS); } catch (Exception e) { return err(500, "Timeout"); }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Builds a JSON error string. The status code is embedded so DashboardServer can read it.
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

    // Parses a single query-string parameter value from a raw query string.
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
