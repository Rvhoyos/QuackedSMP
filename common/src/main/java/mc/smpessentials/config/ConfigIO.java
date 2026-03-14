package mc.smpessentials.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import mc.smpessentials.platform.SmpServices;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared config file helpers for all packages. */
public final class ConfigIO {
    private static final String FILE_NAME = "quackedsmp.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ConfigIO() {
    }

    public static Path path() {
        return SmpServices.PLATFORM.getConfigDir().resolve(FILE_NAME);
    }

    /**
     * Ensures a file exists and returns its parsed JSON; writes a minimal default
     * if missing or empty.
     */
    public static JsonObject readOrCreate() {
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            if (!Files.exists(p) || Files.size(p) == 0) {
                JsonObject root = defaultJson();
                Files.writeString(p, GSON.toJson(root), StandardCharsets.UTF_8);
                return root;
            }
            String s = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject obj = GSON.fromJson(s, JsonObject.class);
            if (obj == null)
                obj = defaultJson();
            boolean dirty = false;
            // Backfill missing sections for forward-compat
            if (!obj.has("chatfilter") || !obj.get("chatfilter").isJsonObject()) {
                obj.add("chatfilter", defaultChatFilterJson());
                dirty = true;
            }
            if (!obj.has("messages") || !obj.get("messages").isJsonObject()) {
                obj.add("messages", defaultJson().getAsJsonObject("messages"));
                dirty = true;
            }
            if (!obj.has("max_claims")) {
                obj.addProperty("max_claims", 50);
                dirty = true;
            }
            if (!obj.has("tp_warmup")) {
                obj.addProperty("tp_warmup", 5);
                dirty = true;
            }
            if (!obj.has("welcome_message")) {
                obj.addProperty("welcome_message", "Welcome to QuackedSMP, {player}!");
                dirty = true;
            }
            if (!obj.has("rules")) {
                com.google.gson.JsonArray r = new com.google.gson.JsonArray();
                r.add("&e1. Be respectful.");
                r.add("&e2. No griefing inside claims.");
                r.add("&e3. Wilderness is dangerous (PvP enabled).");
                r.add("&e4. No cheating.");
                obj.add("rules", r);
                dirty = true;
            }
            if (!obj.has("periodic_messages")) {
                com.google.gson.JsonArray pm = new com.google.gson.JsonArray();
                pm.add("&b[Tip] &fUse &a/claim &fto protect your land!");
                pm.add("&b[Tip] &fSleep in a bed to set your &a/home&f location!");
                pm.add("&b[Tip] &fType &a/smp help &ffor a list of commands!");
                pm.add("&b[Reminder] &fPlease respect the &6/rules&f!");
                pm.add("&b[Tip] &fUnlock active abilities by leveling up your &a/skills&f!");
                pm.add("&b[Tip] &fUse &a/tpr <player>&f to teleport to friends!");
                pm.add("&b[Tip] &fStuck with an intruder? Use &a/sos &fto eject them from your claim!");
                pm.add("&b[Tip] &fReach &aLevel 10 &fin a skill to unlock its unique Active Ability!");
                pm.add("&b[Tip] &fUse &aSneak + Drop (Q) &fwith a tool to activate its ability!");
                pm.add("&b[Tip] &fActivate &bDash &fby &aSprinting + Jumping + Sneaking&f!");
                pm.add("&b[Tip] &fLeveling up skills grants passive buffs like &c+Health &fand &f+Speed!");
                pm.add("&b[Reminder] &fReport griefers to &adev@quackedmod.wiki&f!");
                pm.add("&b[Tip] &fReset the ender dragon in the &6Shogun Temple &fin the village or ask an admin to reset the end world!");
                pm.add("&b[Reminder] &fVote for us on &a[CurseForge](https://www.curseforge.com/servers/minecraft/game/quackedsmp) &fservers to help us grow! :)");
                pm.add("&b[Tip] &fVisit Spawn Shops for blocks & gear! Trade items for Emeralds!");
                obj.add("periodic_messages", pm);
                dirty = true;
            }
            if (!obj.has("message_interval")) {
                obj.addProperty("message_interval", 300);
                dirty = true;
            }
            if (!obj.has("vip_bonus_claims")) {
                obj.addProperty("vip_bonus_claims", 20);
                dirty = true;
            }
            if (!obj.has("allow_lava_wilderness")) {
                obj.addProperty("allow_lava_wilderness", false);
                dirty = true;
            }
            if (!obj.has("bluemap_enable")) {
                obj.addProperty("bluemap_enable", true);
                dirty = true;
            }
            if (!obj.has("bluemap_show_homes")) {
                obj.addProperty("bluemap_show_homes", true);
                dirty = true;
            }
            if (!obj.has("bluemap_show_claims")) {
                obj.addProperty("bluemap_show_claims", true);
                dirty = true;
            }
            if (!obj.has("bluemap_claim_color")) {
                obj.addProperty("bluemap_claim_color", "00FFFF");
                dirty = true;
            }
            if (!obj.has("bluemap_op_claim_color")) {
                obj.addProperty("bluemap_op_claim_color", "FFD700");
                dirty = true;
            }
            if (!obj.has("bluemap_vip_claim_color")) {
                obj.addProperty("bluemap_vip_claim_color", "8A2BE2");
                dirty = true;
            }

            if (!obj.has("vips")) {
                obj.add("vips", new com.google.gson.JsonArray());
                dirty = true;
            }
            if (!obj.has("skills") || !obj.get("skills").isJsonObject()) {
                obj.add("skills", defaultSkillsJson());
                dirty = true;
            }
            if (!obj.has("mute_levels_minutes")) {
                com.google.gson.JsonArray ml = new com.google.gson.JsonArray();
                ml.add(60);
                ml.add(120);
                ml.add(240);
                ml.add(480);
                ml.add(1440);
                obj.add("mute_levels_minutes", ml);
                dirty = true;
            }

            if (dirty) {
                Files.writeString(p, GSON.toJson(obj), StandardCharsets.UTF_8);
            }
            return obj;
        } catch (JsonSyntaxException e) {
            // Malformed JSON — throw a descriptive error for callers to display
            throw new ConfigParseException(
                    "Malformed JSON in " + FILE_NAME + ": " + e.getMessage(), e);
        } catch (IOException e) {
            // Fall back to default in-memory config when IO fails
            return defaultJson();
        }
    }

    /**
     * Thrown when quackedsmp.json contains invalid JSON syntax.
     * The message includes Gson's description of what went wrong
     * (line, column, and expected token).
     */
    public static class ConfigParseException extends RuntimeException {
        public ConfigParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static JsonObject defaultJson() {
        JsonObject root = new JsonObject();
        root.addProperty("max_claims", 50);
        root.addProperty("tp_warmup", 5);
        root.addProperty("welcome_message", "&6Welcome to QuackedSMP, {player}!");
        root.addProperty("message_interval", 300);
        root.addProperty("vip_bonus_claims", 20);
        root.addProperty("allow_lava_wilderness", false);
        root.addProperty("bluemap_enable", true);
        root.addProperty("bluemap_show_homes", true);
        root.addProperty("bluemap_show_claims", true);
        root.addProperty("bluemap_claim_color", "00FFFF");
        root.addProperty("bluemap_op_claim_color", "FFD700");
        root.addProperty("bluemap_vip_claim_color", "8A2BE2");

        com.google.gson.JsonArray muteLevels = new com.google.gson.JsonArray();
        muteLevels.add(60);
        muteLevels.add(120);
        muteLevels.add(240);
        muteLevels.add(480);
        muteLevels.add(1440);
        root.add("mute_levels_minutes", muteLevels);

        root.add("vips", new com.google.gson.JsonArray());

        com.google.gson.JsonArray rules = new com.google.gson.JsonArray();
        rules.add("&e1. Be respectful.");
        rules.add("&e2. No griefing inside claims.");
        rules.add("&e3. Wilderness is dangerous (PvP enabled).");
        rules.add("&e4. No cheating.");
        root.add("rules", rules);

        com.google.gson.JsonArray pm = new com.google.gson.JsonArray();
        pm.add("&b[Tip] &fUse &a/claim &fto protect your land!");
        pm.add("&b[Tip] &fSleep in a bed to set your &a/home&f location!");
        pm.add("&b[Tip] &fType &a/smp help &ffor a list of commands!");
        pm.add("&b[Reminder] &fPlease respect the &6/rules&f!");
        pm.add("&b[Tip] &fUnlock active abilities by leveling up your &a/skills&f!");
        pm.add("&b[Tip] &fUse &a/tpr <player>&f to teleport to friends!");
        pm.add("&b[Tip] &fStuck with an intruder? Use &a/sos &fto eject them from your claim!");
        pm.add("&b[Tip] &fReach &aLevel 10 &fin a skill to unlock its unique Active Ability!");
        pm.add("&b[Tip] &fUse &aSneak + Drop (Q) &fwith a tool to activate its ability!");
        pm.add("&b[Tip] &fActivate &bDash &fby &aSprinting + Jumping + Sneaking&f!");
        pm.add("&b[Tip] &fLeveling up skills grants passive buffs like &c+Health &fand &f+Speed!");
        pm.add("&b[Reminder] &fReport griefers to &adev@quackedmod.wiki&f!");
        pm.add("&b[Tip] &fReset the ender dragon in the &6Shogun Temple &fin the village or ask an admin to reset the end world!");
        pm.add("&b[Reminder] &fVote for us on &a[CurseForge](https://www.curseforge.com/servers/minecraft/game/quackedsmp) &fservers to help us grow! :)");
        pm.add("&b[Tip] &fVisit Spawn Shops for blocks & gear! Trade items for Emeralds!");
        root.add("periodic_messages", pm);

        root.add("chatfilter", defaultChatFilterJson());

        JsonObject msgs = new JsonObject();
        // Claiming
        msgs.addProperty("claim.success", "Chunk claimed.");
        msgs.addProperty("claim.already_claimed", "This claim is already protected.");
        msgs.addProperty("claim.limit_reached", "You reached the claim limit ({count}).");
        msgs.addProperty("claim.spawn_protected", "You can’t claim inside spawn protection.");
        msgs.addProperty("unclaim.success", "Chunk unclaimed.");
        msgs.addProperty("unclaim.fail_ownership", "You don’t control this claim.");
        msgs.addProperty("claim.info.owned", "You own {count} chunk(s) in this dimension.");
        msgs.addProperty("claim.info.protected_by_you", "This chunk is protected by you.");
        msgs.addProperty("claim.info.protected", "This chunk is protected.");
        msgs.addProperty("claim.info.unclaimed", "Current chunk is unclaimed.");

        // Teleportation
        msgs.addProperty("tpr.sent", "Teleport request sent to {player}");
        msgs.addProperty("tpr.received", "{player} requested to teleport to you. Use /tpa accept or /tpa deny.");
        msgs.addProperty("tpr.self", "Cannot request teleport to self.");
        msgs.addProperty("tpr.cooldown", "A request was sent recently. Please wait.");
        msgs.addProperty("tpr.already_pending", "A request is already pending.");
        msgs.addProperty("tpr.queue_full", "That player has too many pending requests.");
        msgs.addProperty("tpa.no_pending", "No pending teleport requests.");
        msgs.addProperty("tpa.requester_offline", "Requester is no longer online.");
        msgs.addProperty("tpa.requester_busy", "Requester cannot be teleported right now.");
        msgs.addProperty("tpa.no_location", "No valid teleport location.");
        msgs.addProperty("tpa.teleporting_requester", "Teleporting {player} in 5 seconds...");
        msgs.addProperty("tpa.teleporting_to", "Teleported to {player}");
        msgs.addProperty("tpa.denied", "Teleport request denied by {player}");
        msgs.addProperty("tpa.denied_confirm", "Denied the oldest pending request.");

        root.add("messages", msgs);
        root.add("skills", defaultSkillsJson());
        return root;
    }

    static JsonObject defaultSkillsJson() {
        JsonObject sk = new JsonObject();
        sk.addProperty("xp_exponent", 1.5);

        JsonObject cds = new JsonObject();
        cds.addProperty("mining", 240);
        cds.addProperty("excavation", 300);
        cds.addProperty("woodcutting", 300);
        cds.addProperty("farming", 180);
        cds.addProperty("fishing", 300);
        cds.addProperty("agility", 10);
        cds.addProperty("melee", 300);
        cds.addProperty("archery", 180);
        cds.addProperty("defense", 600);
        cds.addProperty("enchanting", 1200);
        cds.addProperty("alchemy", 600);
        cds.addProperty("trading", 1200);
        sk.add("cooldowns", cds);

        JsonObject unlocks = new JsonObject();
        unlocks.addProperty("mining", 10);
        unlocks.addProperty("excavation", 10);
        unlocks.addProperty("woodcutting", 10);
        unlocks.addProperty("farming", 10);
        unlocks.addProperty("fishing", 10);
        unlocks.addProperty("agility", 10);
        unlocks.addProperty("melee", 10);
        unlocks.addProperty("archery", 10);
        unlocks.addProperty("defense", 10);
        unlocks.addProperty("enchanting", 10);
        unlocks.addProperty("alchemy", 10);
        unlocks.addProperty("trading", 10);
        sk.add("ability_unlock_levels", unlocks);

        JsonObject caps = new JsonObject();
        caps.addProperty("industrial_speed", 0.5);
        caps.addProperty("nature_health", 10);
        caps.addProperty("combat_damage", 1.0);
        caps.addProperty("knowledge_xp", 1.0);
        sk.add("caps", caps);

        return sk;
    }

    /** Default chatfilter section with starter blocklist and whitelist. */
    private static JsonObject defaultChatFilterJson() {
        JsonObject cf = new JsonObject();

        // Starter blocklist — common profanity for kid-safety
        com.google.gson.JsonArray contents = new com.google.gson.JsonArray();
        for (String w : new String[] {
                "fuck", "shit", "bitch", "ass", "damn", "crap",
                "dick", "cock", "pussy", "bastard", "slut", "whore",
                "cunt", "piss", "nigger", "nigga", "faggot", "retard",
                "kill yourself", "kys"
        }) {
            contents.add(w);
        }
        cf.add("contents", contents);

        // Default whitelist — words that contain blocked substrings
        com.google.gson.JsonArray whitelist = new com.google.gson.JsonArray();
        for (String w : new String[] {
                "assassin", "bass", "class", "grass", "glass", "mass",
                "pass", "compass", "bypass", "classic", "assign",
                "assist", "assumption", "password", "butterscotch",
                "cockatoo", "cocktail", "peacock", "scrapbook",
                "shitake", "therapist"
        }) {
            whitelist.add(w);
        }
        cf.add("whitelist", whitelist);

        return cf;
    }

    public static void save() {
        JsonObject root = defaultJson();

        // Update root with current values
        root.addProperty("max_claims", SmpConfig.MAX_CLAIMS);
        root.addProperty("tp_warmup", SmpConfig.TP_WARMUP);
        root.addProperty("welcome_message", SmpConfig.WELCOME_MESSAGE);
        root.addProperty("message_interval", SmpConfig.MESSAGE_INTERVAL);
        root.addProperty("vip_bonus_claims", SmpConfig.VIP_BONUS_CLAIMS);
        root.addProperty("allow_lava_wilderness", SmpConfig.ALLOW_LAVA_WILDERNESS);
        root.addProperty("bluemap_enable", SmpConfig.BLUEMAP_ENABLE);
        root.addProperty("bluemap_show_homes", SmpConfig.BLUEMAP_SHOW_HOMES);
        root.addProperty("bluemap_show_claims", SmpConfig.BLUEMAP_SHOW_CLAIMS);
        root.addProperty("bluemap_claim_color", SmpConfig.BLUEMAP_CLAIM_COLOR);
        root.addProperty("bluemap_op_claim_color", SmpConfig.BLUEMAP_OP_CLAIM_COLOR);
        root.addProperty("bluemap_vip_claim_color", SmpConfig.BLUEMAP_VIP_CLAIM_COLOR);

        com.google.gson.JsonArray muteLevels = new com.google.gson.JsonArray();
        for (int m : SmpConfig.MUTE_LEVELS_MINUTES)
            muteLevels.add(m);
        root.add("mute_levels_minutes", muteLevels);

        com.google.gson.JsonArray vips = new com.google.gson.JsonArray();
        for (String v : SmpConfig.VIPS)
            vips.add(v);
        root.add("vips", vips);

        com.google.gson.JsonArray rules = new com.google.gson.JsonArray();
        for (String r : SmpConfig.RULES)
            rules.add(r);
        root.add("rules", rules);

        com.google.gson.JsonArray pm = new com.google.gson.JsonArray();
        for (String p : SmpConfig.PERIODIC_MESSAGES)
            pm.add(p);
        root.add("periodic_messages", pm);

        // Messages
        JsonObject msgs = new JsonObject();
        for (var entry : SmpConfig.MESSAGES.entrySet()) {
            msgs.addProperty(entry.getKey(), entry.getValue());
        }
        root.add("messages", msgs);

        // Skills
        JsonObject skills = new JsonObject();
        skills.addProperty("xp_exponent", SmpConfig.SKILL_XP_EXPONENT);
        skills.addProperty("leaderboard_size", SmpConfig.LEADERBOARD_SIZE);

        JsonObject cds = new JsonObject();
        for (var entry : SmpConfig.SKILL_COOLDOWNS.entrySet()) {
            cds.addProperty(entry.getKey(), entry.getValue());
        }
        skills.add("cooldowns", cds);

        JsonObject unlocks = new JsonObject();
        for (var entry : SmpConfig.SKILL_UNLOCK_LEVELS.entrySet()) {
            unlocks.addProperty(entry.getKey(), entry.getValue());
        }
        skills.add("ability_unlock_levels", unlocks);

        JsonObject caps = new JsonObject();
        caps.addProperty("industrial_speed", SmpConfig.CAP_INDUSTRIAL_SPEED);
        caps.addProperty("nature_health", SmpConfig.CAP_NATURE_HEALTH);
        caps.addProperty("combat_damage", SmpConfig.CAP_COMBAT_DAMAGE);
        caps.addProperty("knowledge_xp", SmpConfig.CAP_KNOWLEDGE_XP);
        skills.add("caps", caps);

        root.add("skills", skills);

        try {
            Path p = path();
            if (Files.exists(p)) {
                String s = Files.readString(p, StandardCharsets.UTF_8);
                JsonObject existing = GSON.fromJson(s, JsonObject.class);
                if (existing != null && existing.has("chatfilter")) {
                    root.add("chatfilter", existing.get("chatfilter"));
                } else {
                    root.add("chatfilter", defaultChatFilterJson());
                }
            } else {
                root.add("chatfilter", defaultChatFilterJson());
            }
            Files.writeString(p, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Deletes the config file and reloads defaults.
     */
    public static void resetToFactory() {
        try {
            Files.deleteIfExists(path());
            SmpConfig.load();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
