package mc.smpessentials.config;

import mc.smpessentials.skills.SkillType;

public final class SmpConfig {
    public static int MAX_CLAIMS = 50;
    public static int TP_WARMUP = 5;
    public static String WELCOME_MESSAGE = "Welcome to {server}, {player}!";
    public static java.util.List<String> RULES = new java.util.ArrayList<>();
    public static java.util.List<String> PERIODIC_MESSAGES = new java.util.ArrayList<>();
    public static int MESSAGE_INTERVAL = 300; // Seconds
    // ---- Tier definitions (in ascending tier order) ----
    public static java.util.List<TierDef> TIERS = new java.util.ArrayList<>();

    public record TierDef(int tier, String name, long minPlaytimeHours, int bonusClaims) {}
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
    public static boolean DASHBOARD_ENABLED = false;
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

    public static long getSkillCooldown(SkillType skill) {
        return SKILL_COOLDOWNS.getOrDefault(skill.name().toLowerCase(), defaultCooldown(skill));
    }

    // Returns cooldown for a named sub-ability key (e.g. "archery_zoom"), defaulting to 30s.
    public static long getAbilityCooldown(String abilityKey) {
        return SKILL_COOLDOWNS.getOrDefault(abilityKey.toLowerCase(), 30L);
    }

    public static int getAbilityUnlockLevel(SkillType skill) {
        return SKILL_UNLOCK_LEVELS.getOrDefault(skill.name().toLowerCase(), defaultUnlockLevel(skill));
    }

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
        ConfigData d = ConfigIO.readOrCreate();

        MAX_CLAIMS = d.maxClaims;
        TP_WARMUP = d.tpWarmup;
        WELCOME_MESSAGE = d.welcomeMessage;
        MESSAGE_INTERVAL = d.messageInterval;
        ALLOW_LAVA_WILDERNESS = d.allowLavaWilderness;
        CLAIMS_ENABLED = d.claimsEnabled;
        SKILLS_ENABLED = d.skillsEnabled;
        CHATFILTER_ENABLED = d.chatfilterEnabled;
        VOICECHAT_ENABLE = d.voicechatEnable;
        BLUEMAP_ENABLE = d.bluemapEnable;
        BLUEMAP_SHOW_HOMES = d.bluemapShowHomes;
        BLUEMAP_SHOW_CLAIMS = d.bluemapShowClaims;
        BLUEMAP_SHOW_WORLDBORDER = d.bluemapShowWorldborder;
        BLUEMAP_WORLDBORDER_COLOR = d.bluemapWorldborderColor;
        BLUEMAP_CLAIM_COLOR = d.bluemapClaimColor;
        BLUEMAP_OP_CLAIM_COLOR = d.bluemapOpClaimColor;
        BLUEMAP_VIP_CLAIM_COLOR = d.bluemapVipClaimColor;
        MUTE_LEVELS_MINUTES = d.muteLevelsMinutes;
        TIERS = d.tiers;
        RULES = d.rules;
        PERIODIC_MESSAGES = d.periodicMessages;
        MESSAGES = d.messages;

        VOTIFIER_ENABLED = d.votifier.enabled;
        VOTIFIER_PORT = d.votifier.port;
        VOTIFIER_TOKEN = d.votifier.token;
        VOTE_BROADCAST = d.votifier.broadcast;
        VOTE_REWARDS = d.votifier.rewards;

        DASHBOARD_ENABLED = d.dashboard.enabled;
        DASHBOARD_PORT = d.dashboard.port;
        ADMIN_ENABLED = d.dashboard.adminEnabled;
        ADMIN_PASSWORD_HASH = d.dashboard.adminPasswordHash;
        SERVER_NAME = d.dashboard.serverName;

        DISCORD_WEBHOOK_URL = d.discord.webhookUrl;
        DISCORD_JOIN_LEAVE = d.discord.joinLeave;
        DISCORD_CHAT = d.discord.chat;

        SKILL_XP_EXPONENT = d.skills.xpExponent;
        SKILL_COOLDOWNS = d.skills.cooldowns;
        SKILL_UNLOCK_LEVELS = d.skills.abilityUnlockLevels;
        CAP_INDUSTRIAL_SPEED = d.skills.caps.industrialSpeed;
        CAP_NATURE_HEALTH = d.skills.caps.natureHealth;
        CAP_COMBAT_DAMAGE = d.skills.caps.combatDamage;
        CAP_KNOWLEDGE_XP = d.skills.caps.knowledgeXp;
        CAP_DOUBLE_DROP = d.skills.caps.doubleDrop;
        CAP_DEFENSE_ARMOR = d.skills.caps.defenseArmor;
        CAP_SAFE_LANDING = d.skills.caps.safeLanding;
    }
}
