package mc.smpessentials.config;

import com.google.gson.JsonObject;
import mc.smpessentials.skills.SkillType;

public final class SmpConfig {
    public static int MAX_CLAIMS = 50;
    public static int TP_WARMUP = 5;
    public static String WELCOME_MESSAGE = "Welcome to QuackedSMP, {player}!";
    public static java.util.List<String> RULES = new java.util.ArrayList<>();
    public static java.util.List<String> PERIODIC_MESSAGES = new java.util.ArrayList<>();
    public static int MESSAGE_INTERVAL = 300; // Seconds
    public static java.util.List<String> VIPS = new java.util.ArrayList<>();
    public static int VIP_BONUS_CLAIMS = 20;
    public static boolean ALLOW_LAVA_WILDERNESS = false;
    public static java.util.Map<String, String> MESSAGES = new java.util.HashMap<>();

    // ---- Feature toggles ----
    public static boolean CLAIMS_ENABLED = true;
    public static boolean SKILLS_ENABLED = true;
    public static boolean CHATFILTER_ENABLED = true;
    public static java.util.List<Integer> MUTE_LEVELS_MINUTES = new java.util.ArrayList<>(
            java.util.List.of(60, 120, 240, 480, 1440));

    // ---- Votifier ----
    public static boolean VOTIFIER_ENABLED = false;
    public static int VOTIFIER_PORT = 8192;
    public static String VOTIFIER_TOKEN = "";
    public static String VOTE_BROADCAST = "&6\u2736 {player} &ejust voted for the server! Thanks for the support!";
    public static java.util.List<String> VOTE_REWARDS = new java.util.ArrayList<>();

    // ---- Dashboard ----
    public static boolean DASHBOARD_ENABLED = true;
    public static int DASHBOARD_PORT = 8125;
    public static boolean ADMIN_ENABLED = false;
    public static String ADMIN_PASSWORD_HASH = "";
    public static String SERVER_NAME = "";

    // ---- Discord Webhook ----
    public static String DISCORD_WEBHOOK_URL = "";
    public static boolean DISCORD_JOIN_LEAVE = true;
    public static boolean DISCORD_CHAT = true;

    // ---- Voice Chat ----
    public static boolean VOICECHAT_ENABLE = true;

    // ---- BlueMap ----
    public static boolean BLUEMAP_ENABLE = true;
    public static boolean BLUEMAP_SHOW_HOMES = true;
    public static boolean BLUEMAP_SHOW_CLAIMS = true;
    public static boolean BLUEMAP_SHOW_WORLDBORDER = true;
    public static String BLUEMAP_WORLDBORDER_COLOR = "FF3C3C";
    // Stored as 6-digit RGB hex strings (e.g. "00FFFF")
    public static String BLUEMAP_CLAIM_COLOR = "00FFFF"; // Cyan
    public static String BLUEMAP_OP_CLAIM_COLOR = "FFD700"; // Gold
    public static String BLUEMAP_VIP_CLAIM_COLOR = "8A2BE2"; // Purple

    // ---- Skills ----
    public static double SKILL_XP_EXPONENT = 1.5;
    public static java.util.Map<String, Long> SKILL_COOLDOWNS = new java.util.HashMap<>();
    public static java.util.Map<String, Integer> SKILL_UNLOCK_LEVELS = new java.util.HashMap<>();
    public static double CAP_INDUSTRIAL_SPEED = 0.5; // +50% movement speed at max
    public static double CAP_NATURE_HEALTH = 10.0; // +10 hearts at max
    public static double CAP_COMBAT_DAMAGE = 1.0; // +100% damage at max
    public static double CAP_KNOWLEDGE_XP = 1.0; // +100% xp orbs at max
    public static double CAP_DOUBLE_DROP = 0.5; // max 50% double drop chance at Industrial parent level 100
    public static double CAP_DEFENSE_ARMOR = 10.0; // max +10 armor points at Defense level 100
    public static double CAP_SAFE_LANDING = 1.0; // max 100% fall damage absorbed at Agility level 100 (linear)

    /** Get cooldown in seconds for a skill's active ability. */
    public static long getSkillCooldown(SkillType skill) {
        return SKILL_COOLDOWNS.getOrDefault(skill.name().toLowerCase(), defaultCooldown(skill));
    }

    /**
     * Get cooldown in seconds for a named sub-ability (e.g. "archery_zoom").
     * Falls back to 30s if not configured.
     */
    public static long getAbilityCooldown(String abilityKey) {
        return SKILL_COOLDOWNS.getOrDefault(abilityKey.toLowerCase(), 30L);
    }

    /** Get the minimum level required to unlock a skill's active ability. */
    public static int getAbilityUnlockLevel(SkillType skill) {
        return SKILL_UNLOCK_LEVELS.getOrDefault(skill.name().toLowerCase(), defaultUnlockLevel(skill));
    }

    /** Get the minimum level required to unlock a named sub-ability (e.g. "archery_zoom"). */
    public static int getAbilityUnlockLevel(String abilityKey) {
        return SKILL_UNLOCK_LEVELS.getOrDefault(abilityKey.toLowerCase(), 5);
    }

    private static int defaultUnlockLevel(SkillType skill) {
        return switch (skill) {
            case TRADING -> 1;
            case DEFENSE -> 5;
            case AGILITY -> 3;
            default -> 10;
        };
    }

    private static long defaultCooldown(SkillType skill) {
        return switch (skill) {
            case MINING -> 240L; // 4m
            case EXCAVATION -> 300L; // 5m
            case WOODCUTTING -> 300L; // 5m
            case FARMING -> 180L; // 3m
            case FISHING -> 300L; // 5m
            case AGILITY -> 10L; // 10s (unchanged)
            case MELEE -> 300L; // 5m
            case ARCHERY -> 180L; // 3m
            case DEFENSE -> 600L; // 10m
            case ENCHANTING -> 1200L; // 20m
            case ALCHEMY -> 600L; // 10m
            case TRADING -> 1200L; // 20m
        };
    }

    private SmpConfig() {
    }

    public static void load() {
        JsonObject root = ConfigIO.readOrCreate();
        if (root.has("max_claims")) {
            MAX_CLAIMS = root.get("max_claims").getAsInt();
        }
        if (root.has("tp_warmup")) {
            TP_WARMUP = root.get("tp_warmup").getAsInt();
        }
        if (root.has("welcome_message")) {
            WELCOME_MESSAGE = root.get("welcome_message").getAsString();
        }
        if (root.has("message_interval")) {
            MESSAGE_INTERVAL = root.get("message_interval").getAsInt();
        }
        if (root.has("vip_bonus_claims")) {
            VIP_BONUS_CLAIMS = root.get("vip_bonus_claims").getAsInt();
        }

        if (root.has("allow_lava_wilderness")) {
            ALLOW_LAVA_WILDERNESS = root.get("allow_lava_wilderness").getAsBoolean();
        }

        if (root.has("claims_enabled"))     CLAIMS_ENABLED     = root.get("claims_enabled").getAsBoolean();
        if (root.has("skills_enabled"))     SKILLS_ENABLED     = root.get("skills_enabled").getAsBoolean();
        if (root.has("chatfilter_enabled")) CHATFILTER_ENABLED = root.get("chatfilter_enabled").getAsBoolean();

        if (root.has("votifier") && root.get("votifier").isJsonObject()) {
            JsonObject vt = root.getAsJsonObject("votifier");
            if (vt.has("enabled")) VOTIFIER_ENABLED = vt.get("enabled").getAsBoolean();
            if (vt.has("port"))    VOTIFIER_PORT    = vt.get("port").getAsInt();
            if (vt.has("token"))   VOTIFIER_TOKEN   = vt.get("token").getAsString();
            if (vt.has("broadcast")) VOTE_BROADCAST = vt.get("broadcast").getAsString();
            if (vt.has("rewards") && vt.get("rewards").isJsonArray()) {
                VOTE_REWARDS.clear();
                for (var el : vt.getAsJsonArray("rewards")) VOTE_REWARDS.add(el.getAsString());
            }
        }

        if (root.has("dashboard") && root.get("dashboard").isJsonObject()) {
            JsonObject db = root.getAsJsonObject("dashboard");
            if (db.has("enabled"))        DASHBOARD_ENABLED    = db.get("enabled").getAsBoolean();
            if (db.has("port"))           DASHBOARD_PORT       = db.get("port").getAsInt();
            if (db.has("admin_enabled"))  ADMIN_ENABLED        = db.get("admin_enabled").getAsBoolean();
            if (db.has("admin_password_hash")) ADMIN_PASSWORD_HASH = db.get("admin_password_hash").getAsString();
            if (db.has("server_name"))    SERVER_NAME          = db.get("server_name").getAsString();
        }

        if (root.has("discord") && root.get("discord").isJsonObject()) {
            JsonObject dc = root.getAsJsonObject("discord");
            if (dc.has("webhook_url"))  DISCORD_WEBHOOK_URL = dc.get("webhook_url").getAsString();
            if (dc.has("join_leave"))   DISCORD_JOIN_LEAVE  = dc.get("join_leave").getAsBoolean();
            if (dc.has("chat"))         DISCORD_CHAT        = dc.get("chat").getAsBoolean();
        }

        if (root.has("voicechat_enable"))
            VOICECHAT_ENABLE = root.get("voicechat_enable").getAsBoolean();

        if (root.has("bluemap_enable"))
            BLUEMAP_ENABLE = root.get("bluemap_enable").getAsBoolean();
        if (root.has("bluemap_show_homes"))
            BLUEMAP_SHOW_HOMES = root.get("bluemap_show_homes").getAsBoolean();
        if (root.has("bluemap_show_claims"))
            BLUEMAP_SHOW_CLAIMS = root.get("bluemap_show_claims").getAsBoolean();
        if (root.has("bluemap_show_worldborder"))
            BLUEMAP_SHOW_WORLDBORDER = root.get("bluemap_show_worldborder").getAsBoolean();
        if (root.has("bluemap_worldborder_color"))
            BLUEMAP_WORLDBORDER_COLOR = root.get("bluemap_worldborder_color").getAsString();
        if (root.has("bluemap_claim_color"))
            BLUEMAP_CLAIM_COLOR = root.get("bluemap_claim_color").getAsString();
        if (root.has("bluemap_op_claim_color"))
            BLUEMAP_OP_CLAIM_COLOR = root.get("bluemap_op_claim_color").getAsString();
        if (root.has("bluemap_vip_claim_color"))
            BLUEMAP_VIP_CLAIM_COLOR = root.get("bluemap_vip_claim_color").getAsString();

        loadList(root, "vips", VIPS);
        if (root.has("rules")) {
            loadList(root, "rules", RULES);
        } else if (RULES.isEmpty()) {
            RULES.add("&e1. Be respectful. 2. No GRIEFING. 3. No cheating.");
            RULES.add("&e4. Voice chat is strictly 18+. Falsely verifying your age will result in a ban.");
            RULES.add("&e5. Do not attempt to bypass the chat filter or use slurs.");
            RULES.add("&e6. Found a bug or exploit? Report it to dev@quackedmod.wiki for a reward!");
            RULES.add("&e7. Build bases FAR from spawn. Use the RTP portal!");
            RULES.add("&e8. Spawn's communal area is for cool builds to show off!");
        }

        if (root.has("periodic_messages")) {
            loadList(root, "periodic_messages", PERIODIC_MESSAGES);
        } else if (PERIODIC_MESSAGES.isEmpty()) {
            PERIODIC_MESSAGES.add("&b[Tip] &fUse &a/claim &fto protect your land!");
            PERIODIC_MESSAGES.add("&b[Tip] &fSleep in a bed to set your &a/home&f location!");
            PERIODIC_MESSAGES.add("&b[Tip] &fType &a/smp help &ffor a list of commands!");
            PERIODIC_MESSAGES.add("&b[Tip] &fPlease respect the &6/rules&f!");
            PERIODIC_MESSAGES.add("&b[Tip] &fUnlock active abilities by leveling up your &a/skills&f!");
            PERIODIC_MESSAGES.add("&b[Tip] &fUse &a/tpr <player>&f to teleport to friends!");
            PERIODIC_MESSAGES.add("&b[Tip] &fStuck with an intruder? Use &a/sos &fto eject them from your claim!");
            PERIODIC_MESSAGES.add("&b[Tip] &fReach &aLevel 10 &fin a skill to unlock its unique Active Ability!");
            PERIODIC_MESSAGES.add("&b[Tip] &fUse &aSneak + Drop (Q) &fwith a tool to activate its ability!");
            PERIODIC_MESSAGES.add("&b[Tip] &fActivate &bDash &fby &aSprinting + Jumping + Sneaking&f!");
            PERIODIC_MESSAGES.add("&b[Tip] &fLeveling up skills grants passive buffs like &c+Health &fand &f+Speed!");
            PERIODIC_MESSAGES.add("&b[Tip] &fReport griefers to &adev@quackedmod.wiki&f!");
            PERIODIC_MESSAGES.add("&b[Tip] &fReset the ender dragon in the &6Shogun Temple &fin the village or ask an admin to reset the end world!");
            PERIODIC_MESSAGES.add("&b[Tip] &fVisit Spawn Shops for blocks & gear! Trade items for Emeralds!");
            PERIODIC_MESSAGES.add("&b[Tip] &fKeep Inventory is ON by default! If you prefer a challenge, type &a/keepinv off&f to drop your items on death.");
            PERIODIC_MESSAGES.add("&b[Tip] &fNot sure where a claim ends? Use &a/claim map&f to visualize nearby chunk borders in chat!");
            PERIODIC_MESSAGES.add("&b[Tip] &fBuilding with friends? Use &a/trust <player>&f to give them permission to safely build in your claims!");
            PERIODIC_MESSAGES.add("&b[Tip] &fWe have proximity voice chat! If you're 18+, type &a/verify confirm&f to enable it and start talking!");
            PERIODIC_MESSAGES.add("&b[Tip] &fWant to see who has the highest skills? Type &a/skills top&f to view the server leaderboards!");
            PERIODIC_MESSAGES.add("&b[Tip] &fCheck out our live web-map! You can see your &a/home&f and even name your claims using &a/claim name <name>&f!");
            PERIODIC_MESSAGES.add("&b[Tip] &fGet our recommended modpack for &ashaders&f, &aminimap&f, &avoice chat &fand more! [&bDownload Here](https://www.curseforge.com/minecraft/modpacks/play-quackedmod-wiki)");
        }

        if (root.has("messages") && root.get("messages").isJsonObject()) {
            MESSAGES.clear();
            JsonObject msgs = root.getAsJsonObject("messages");
            for (String key : msgs.keySet()) {
                MESSAGES.put(key, msgs.get(key).getAsString());
            }
        }

        // ---- Skills ----
        if (root.has("skills") && root.get("skills").isJsonObject()) {
            JsonObject sk = root.getAsJsonObject("skills");
            if (sk.has("xp_exponent"))
                SKILL_XP_EXPONENT = sk.get("xp_exponent").getAsDouble();
            if (sk.has("cooldowns") && sk.get("cooldowns").isJsonObject()) {
                SKILL_COOLDOWNS.clear();
                JsonObject cds = sk.getAsJsonObject("cooldowns");
                for (String key : cds.keySet()) {
                    SKILL_COOLDOWNS.put(key, cds.get(key).getAsLong());
                }
            }
            if (sk.has("ability_unlock_levels") && sk.get("ability_unlock_levels").isJsonObject()) {
                SKILL_UNLOCK_LEVELS.clear();
                JsonObject unlocks = sk.getAsJsonObject("ability_unlock_levels");
                for (String key : unlocks.keySet()) {
                    SKILL_UNLOCK_LEVELS.put(key, unlocks.get(key).getAsInt());
                }
            }
            if (sk.has("caps") && sk.get("caps").isJsonObject()) {
                JsonObject caps = sk.getAsJsonObject("caps");
                if (caps.has("industrial_speed"))
                    CAP_INDUSTRIAL_SPEED = caps.get("industrial_speed").getAsDouble();
                if (caps.has("nature_health"))
                    CAP_NATURE_HEALTH = caps.get("nature_health").getAsDouble();
                if (caps.has("combat_damage"))
                    CAP_COMBAT_DAMAGE = caps.get("combat_damage").getAsDouble();
                if (caps.has("knowledge_xp"))
                    CAP_KNOWLEDGE_XP = caps.get("knowledge_xp").getAsDouble();
                if (caps.has("double_drop"))
                    CAP_DOUBLE_DROP = caps.get("double_drop").getAsDouble();
                if (caps.has("defense_armor"))
                    CAP_DEFENSE_ARMOR = caps.get("defense_armor").getAsDouble();
                if (caps.has("safe_landing"))
                    CAP_SAFE_LANDING = caps.get("safe_landing").getAsDouble();
            }
        }

        if (root.has("mute_levels_minutes") && root.get("mute_levels_minutes").isJsonArray()) {
            MUTE_LEVELS_MINUTES.clear();
            for (var el : root.get("mute_levels_minutes").getAsJsonArray()) {
                MUTE_LEVELS_MINUTES.add(el.getAsInt());
            }
        }
    }

    private static void loadList(JsonObject root, String key, java.util.List<String> list) {
        if (root.has(key) && root.get(key).isJsonArray()) {
            list.clear();
            for (var el : root.get(key).getAsJsonArray()) {
                list.add(el.getAsString());
            }
        }
    }
}
