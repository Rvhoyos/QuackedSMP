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
    public static java.util.List<Integer> MUTE_LEVELS_MINUTES = new java.util.ArrayList<>(
            java.util.List.of(60, 120, 240, 480, 1440));

    // ---- BlueMap ----
    public static boolean BLUEMAP_ENABLE = true;
    public static boolean BLUEMAP_SHOW_HOMES = true;
    public static boolean BLUEMAP_SHOW_CLAIMS = true;
    // Format: "R,G,B,A" where color components are 0-255 (A is 0.0-1.0 or 0-255?
    // BlueMap API uses java.awt.Color, let's just use hex strings like "#88FF0000"
    // (ARGB) or store integer RGB and float alpha). Let's use standard integer RGB,
    // and a float opacity.
    public static String BLUEMAP_CLAIM_COLOR = "00FFFF"; // Cyan
    public static String BLUEMAP_OP_CLAIM_COLOR = "FFD700"; // Gold
    public static String BLUEMAP_VIP_CLAIM_COLOR = "8A2BE2"; // Purple

    // ---- Skills ----
    public static double SKILL_XP_EXPONENT = 1.5;
    public static int LEADERBOARD_SIZE = 10;
    public static java.util.Map<String, Long> SKILL_COOLDOWNS = new java.util.HashMap<>();
    public static java.util.Map<String, Integer> SKILL_UNLOCK_LEVELS = new java.util.HashMap<>();
    public static double CAP_INDUSTRIAL_SPEED = 0.5; // +50% mining speed at max
    public static double CAP_NATURE_HEALTH = 10.0; // +10 hearts at max
    public static double CAP_COMBAT_DAMAGE = 1.0; // +100% damage at max
    public static double CAP_KNOWLEDGE_XP = 1.0; // +100% xp orbs at max

    /** Get cooldown in seconds for a skill's active ability. */
    public static long getSkillCooldown(SkillType skill) {
        return SKILL_COOLDOWNS.getOrDefault(skill.name().toLowerCase(), defaultCooldown(skill));
    }

    /** Get the minimum level required to unlock a skill's active ability. */
    public static int getAbilityUnlockLevel(SkillType skill) {
        return SKILL_UNLOCK_LEVELS.getOrDefault(skill.name().toLowerCase(), defaultUnlockLevel(skill));
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

        if (root.has("bluemap_enable"))
            BLUEMAP_ENABLE = root.get("bluemap_enable").getAsBoolean();
        if (root.has("bluemap_show_homes"))
            BLUEMAP_SHOW_HOMES = root.get("bluemap_show_homes").getAsBoolean();
        if (root.has("bluemap_show_claims"))
            BLUEMAP_SHOW_CLAIMS = root.get("bluemap_show_claims").getAsBoolean();
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
            RULES.add("&e1. Be respectful.");
            RULES.add("&e2. No griefing inside claims.");
            RULES.add("&e3. Wilderness is dangerous (PvP enabled).");
            RULES.add("&e4. No cheating.");
        }

        if (root.has("periodic_messages")) {
            loadList(root, "periodic_messages", PERIODIC_MESSAGES);
        } else if (PERIODIC_MESSAGES.isEmpty()) {
            PERIODIC_MESSAGES.add("&b[Tip] &fUse &a/claim &fto protect your land!");
            PERIODIC_MESSAGES.add("&b[Tip] &fSet your home by sleeping in a bed!");
            PERIODIC_MESSAGES.add("&b[Tip] &fType &a/smp help &ffor commands!");
            PERIODIC_MESSAGES.add("&b[Reminder] &fPlease respect the &6/rules&f!");
            PERIODIC_MESSAGES.add("&b[Tip] &fUse &a/tpr <player>&f to teleport to friends!");
            PERIODIC_MESSAGES.add("&b[Reminder] &fReport griefers to &adev@quackedmod.wiki&f!");
            PERIODIC_MESSAGES.add("&b[Tip] &fReset the ender dragon in the &6Shogun Temple &fin the village or ask an admin to reset the end world!");
            PERIODIC_MESSAGES.add("&b[Reminder] &fVote for us on &a[CurseForge](https://www.curseforge.com/servers/minecraft/game/quackedsmp) &fservers to help us grow! :)");
            PERIODIC_MESSAGES.add("&b[Tip] &fVisit Spawn Shops for blocks & gear! Trade items for Emeralds!");
        }

        if (root.has("messages") && root.get("messages").isJsonObject()) {
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
            if (sk.has("leaderboard_size"))
                LEADERBOARD_SIZE = sk.get("leaderboard_size").getAsInt();
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
