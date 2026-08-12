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
    public static boolean ALLOW_FIRE_WILDERNESS = false;
    public static boolean SPAWN_NO_PVP = true;
    public static boolean PROTECT_EXPLOSIONS = true;
    public static boolean PROTECT_FIRE_CLAIMS = true;
    public static boolean PROTECT_ENDERMAN = true;
    public static boolean PROTECT_FARMLAND = true;
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
    // Tier number -> bonus reward commands. Stacks: tier 2 players get tier 1 + tier 2 bonuses.
    public static java.util.Map<Integer, java.util.List<String>> VOTE_VIP_REWARDS = new java.util.HashMap<>();

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
    public static boolean BLUEMAP_SHOW_SPAWN_PROTECTION = true;
    public static String BLUEMAP_SPAWN_PROTECTION_COLOR = "80409040";

    // ---- Hardcore ----
    public static boolean HARDCORE_ENABLED = false;
    // Percent of peak players that must die to end a session. 100 = ends only when everyone dies.
    public static int HARDCORE_DEATH_PERCENT = 100;
    // Sends the vanilla withered-heart HUD to session members. Per-connection, so it only
    // takes effect on (re)connect; joining/leaving while online shows a passive warning.
    public static boolean HARDCORE_WITHERED_HEARTS = true;
    // Gives session members a scoreboard-team identity (nametag/tab color). Off by default;
    // when off, TeamAutoAssign behaves exactly as before.
    public static boolean HARDCORE_TEAM_VISIBILITY = false;
    public static String  HARDCORE_TEAM_NAME       = "hardcore";
    // Per-player run-time sidebar (transient: periodic + on session entry).
    public static boolean HARDCORE_SIDEBAR_ENABLED          = false;
    public static int     HARDCORE_SIDEBAR_INTERVAL_SECONDS = 2700;
    public static int     HARDCORE_SIDEBAR_SHOW_SECONDS     = 20;
    public static int     HARDCORE_SIDEBAR_ON_ENTRY_SECONDS = 10;

    // ---- Anti-XRay ----
    public static boolean ANTIXRAY_ENABLED = true;

    // ---- World Backups ----
    public static int     BACKUP_MAX_COUNT             = 5;
    public static String  BACKUP_DIR                   = "backups";
    public static boolean BACKUP_PERIODIC_ENABLED      = false;
    public static int     BACKUP_INTERVAL_HOURS        = 24;
    public static boolean BACKUP_PUBLIC_DOWNLOAD       = false;
    public static int     BACKUP_PUBLIC_MAX_CONCURRENT = 0; // 0 = follow server max-players
    public static int     BACKUP_PUBLIC_MAX_PER_IP     = 2;

    // ---- Web Panel link (gated on public download) ----
    public static String  PANEL_URL              = "";
    public static String  PANEL_MESSAGE          = "&aServer web panel & world download: [Click here]({url})";
    public static boolean PANEL_MESSAGE_ENABLED  = false;
    public static int     PANEL_MESSAGE_INTERVAL = 1800; // Seconds

    // Single gate for both /smp download and the periodic broadcast: public download must be
    // on and a URL configured. URL scheme (http/https) is validated at save time in AdminHandler.
    public static boolean panelLinkAvailable() {
        return BACKUP_PUBLIC_DOWNLOAD && PANEL_URL != null && !PANEL_URL.isBlank();
    }

    // panelMessage with {url} substituted for the configured panel URL. Still needs TextUtil.format
    // to turn & codes and [label](url) markdown into a component before sending.
    public static String panelMessageResolved() {
        return PANEL_MESSAGE.replace("{url}", PANEL_URL == null ? "" : PANEL_URL.trim());
    }

    // ---- Shops ----
    public static boolean SHOPS_ENABLED = false;
    public static boolean ECONOMY_ENABLED = false;

    // ---- Kits ----
    public static boolean KITS_ENABLED = true;
    public static long KIT_COOLDOWN_SECONDS = 86400;
    public static java.util.List<ConfigData.KitDef> KIT_DEFINITIONS = new java.util.ArrayList<>();

    // ---- Team Auto-Assign ----
    public static java.util.Map<String, java.util.List<String>> TEAM_AUTO_ASSIGN = new java.util.HashMap<>();

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
        ALLOW_FIRE_WILDERNESS = d.allowFireWilderness;
        SPAWN_NO_PVP = d.spawnNoPvp;
        PROTECT_EXPLOSIONS = d.protectExplosions;
        PROTECT_FIRE_CLAIMS = d.protectFireClaims;
        PROTECT_ENDERMAN = d.protectEnderman;
        PROTECT_FARMLAND = d.protectFarmland;
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
        BLUEMAP_SHOW_SPAWN_PROTECTION = d.bluemapShowSpawnProtection;
        BLUEMAP_SPAWN_PROTECTION_COLOR = d.bluemapSpawnProtectionColor;
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
        VOTE_VIP_REWARDS = new java.util.HashMap<>();
        for (var entry : d.votifier.vipRewards.entrySet()) {
            try {
                VOTE_VIP_REWARDS.put(Integer.parseInt(entry.getKey()), new java.util.ArrayList<>(entry.getValue()));
            } catch (NumberFormatException ignored) {}
        }

        DASHBOARD_ENABLED = d.dashboard.enabled;
        DASHBOARD_PORT = d.dashboard.port;
        ADMIN_ENABLED = d.dashboard.adminEnabled;
        ADMIN_PASSWORD_HASH = d.dashboard.adminPasswordHash;
        SERVER_NAME = d.dashboard.serverName;

        DISCORD_WEBHOOK_URL = d.discord.webhookUrl;
        DISCORD_JOIN_LEAVE = d.discord.joinLeave;
        DISCORD_CHAT = d.discord.chat;

        HARDCORE_ENABLED = d.hardcoreEnabled;
        HARDCORE_DEATH_PERCENT = d.hardcoreDeathPercent;
        HARDCORE_WITHERED_HEARTS = d.hardcoreWitheredHearts;
        HARDCORE_TEAM_VISIBILITY = d.hardcoreTeamVisibility;
        HARDCORE_TEAM_NAME = d.hardcoreTeamName;
        HARDCORE_SIDEBAR_ENABLED = d.hardcoreSidebarEnabled;
        HARDCORE_SIDEBAR_INTERVAL_SECONDS = d.hardcoreSidebarIntervalSeconds;
        HARDCORE_SIDEBAR_SHOW_SECONDS = d.hardcoreSidebarShowSeconds;
        HARDCORE_SIDEBAR_ON_ENTRY_SECONDS = d.hardcoreSidebarOnEntrySeconds;

        TEAM_AUTO_ASSIGN = new java.util.HashMap<>();
        for (var entry : d.teamAutoAssign.entrySet()) {
            TEAM_AUTO_ASSIGN.put(entry.getKey(), new java.util.ArrayList<>(entry.getValue()));
        }

        ANTIXRAY_ENABLED = d.antixrayEnabled;
        BACKUP_MAX_COUNT             = d.backupMaxCount;
        BACKUP_DIR                   = d.backupDir;
        BACKUP_PERIODIC_ENABLED      = d.backupPeriodicEnabled;
        BACKUP_INTERVAL_HOURS        = d.backupIntervalHours;
        BACKUP_PUBLIC_DOWNLOAD       = d.backupPublicDownload;
        BACKUP_PUBLIC_MAX_CONCURRENT = d.backupPublicMaxConcurrent;
        BACKUP_PUBLIC_MAX_PER_IP     = d.backupPublicMaxPerIp;
        PANEL_URL                    = d.panelUrl;
        PANEL_MESSAGE                = d.panelMessage;
        PANEL_MESSAGE_ENABLED        = d.panelMessageEnabled;
        PANEL_MESSAGE_INTERVAL       = d.panelMessageInterval;
        SHOPS_ENABLED = d.shopsEnabled;
        ECONOMY_ENABLED = d.economyEnabled;
        KITS_ENABLED = d.kitsEnabled;
        KIT_COOLDOWN_SECONDS = d.kits.cooldownSeconds;
        KIT_DEFINITIONS = d.kits.kits;

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

        mc.smpessentials.teams.TeamAutoAssign.buildReverseMap();
    }
}
