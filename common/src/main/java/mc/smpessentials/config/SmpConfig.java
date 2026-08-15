package mc.smpessentials.config;

import mc.smpessentials.skills.SkillType;

/**
 * Live runtime holder for all config values. Read from here everywhere.
 *
 * Do NOT give these fields default literals. Every default lives in exactly
 * one place, {@link ConfigData}. {@link #load()} copies ConfigData into these
 * fields at startup (SmpUtilsMod.init, before any subsystem), so any literal
 * written here is dead: overwritten before the first read, and a silent
 * second source of truth that can disagree with the real default.
 *
 * Fields are intentionally declared uninitialized. Collections keep an empty
 * initializer only for null-safety, never a populated default.
 *
 * To change a default: edit {@link ConfigData} only.
 */
public final class SmpConfig {
    // No defaults here. See class Javadoc. Defaults live in ConfigData.
    public static int MAX_CLAIMS;
    public static int TP_WARMUP;
    public static String WELCOME_MESSAGE;
    public static java.util.List<String> RULES = new java.util.ArrayList<>();
    public static java.util.List<String> PERIODIC_MESSAGES = new java.util.ArrayList<>();
    public static int MESSAGE_INTERVAL; // Seconds
    // ---- Tier definitions (in ascending tier order) ----
    public static java.util.List<TierDef> TIERS = new java.util.ArrayList<>();

    public record TierDef(int tier, String name, long minPlaytimeHours, int bonusClaims) {}
    public static boolean ALLOW_LAVA_WILDERNESS;
    public static boolean ALLOW_FIRE_WILDERNESS;
    public static boolean SPAWN_NO_PVP;
    public static boolean SPAWN_NO_HOSTILES;
    public static boolean PROTECT_EXPLOSIONS;
    public static boolean PROTECT_FIRE_CLAIMS;
    public static boolean PROTECT_ENDERMAN;
    public static boolean PROTECT_FARMLAND;
    public static java.util.Map<String, String> MESSAGES = new java.util.HashMap<>();

    // ---- Feature toggles ----
    public static boolean CLAIMS_ENABLED;
    public static boolean SKILLS_ENABLED;
    public static boolean CHATFILTER_ENABLED;
    public static java.util.List<Integer> MUTE_LEVELS_MINUTES = new java.util.ArrayList<>();

    // ---- Votifier ----
    public static boolean VOTIFIER_ENABLED;
    public static int VOTIFIER_PORT;
    public static String VOTIFIER_TOKEN;
    public static String VOTE_BROADCAST;
    public static java.util.List<String> VOTE_REWARDS = new java.util.ArrayList<>();
    // Tier number -> bonus reward commands. Stacks: tier 2 players get tier 1 + tier 2 bonuses.
    public static java.util.Map<Integer, java.util.List<String>> VOTE_VIP_REWARDS = new java.util.HashMap<>();

    // ---- Dashboard ----
    public static boolean DASHBOARD_ENABLED;
    public static int DASHBOARD_PORT;
    public static boolean ADMIN_ENABLED;
    public static String ADMIN_PASSWORD_HASH;
    public static String SERVER_NAME;

    // ---- Discord Webhook ----
    public static String DISCORD_WEBHOOK_URL;
    public static boolean DISCORD_JOIN_LEAVE;
    public static boolean DISCORD_CHAT;

    // ---- Voice Chat ----
    public static boolean VOICECHAT_ENABLE;

    // ---- BlueMap ----
    public static boolean BLUEMAP_ENABLE;
    public static boolean BLUEMAP_SHOW_HOMES;
    public static boolean BLUEMAP_SHOW_CLAIMS;
    public static boolean BLUEMAP_SHOW_WORLDBORDER;
    public static String BLUEMAP_WORLDBORDER_COLOR;
    // Stored as 6-digit RGB hex strings (e.g. "00FFFF")
    public static String BLUEMAP_CLAIM_COLOR;
    public static String BLUEMAP_OP_CLAIM_COLOR;
    public static String BLUEMAP_VIP_CLAIM_COLOR;
    public static boolean BLUEMAP_SHOW_SPAWN_PROTECTION;
    public static String BLUEMAP_SPAWN_PROTECTION_COLOR;

    // ---- Hardcore ----
    public static boolean HARDCORE_ENABLED;
    // Percent of peak players that must die to end a session. 100 = ends only when everyone dies.
    public static int HARDCORE_DEATH_PERCENT;
    // Sends the vanilla withered-heart HUD to session members. Per-connection, so it only
    // takes effect on (re)connect; joining/leaving while online shows a passive warning.
    public static boolean HARDCORE_WITHERED_HEARTS;
    // Gives session members a scoreboard-team identity (nametag/tab color).
    // When off, TeamAutoAssign behaves exactly as before.
    public static boolean HARDCORE_TEAM_VISIBILITY;
    public static String  HARDCORE_TEAM_NAME;
    // Per-player run-time sidebar (transient: periodic + on session entry).
    public static boolean HARDCORE_SIDEBAR_ENABLED;
    public static int     HARDCORE_SIDEBAR_INTERVAL_SECONDS;
    public static int     HARDCORE_SIDEBAR_SHOW_SECONDS;
    public static int     HARDCORE_SIDEBAR_ON_ENTRY_SECONDS;

    // ---- Welcome sidebar (2nd MOTD) ----
    public static boolean WELCOME_SIDEBAR_ENABLED;
    public static String  WELCOME_SIDEBAR_TITLE;
    public static java.util.List<String> WELCOME_SIDEBAR_LINES = new java.util.ArrayList<>();
    public static int     WELCOME_SIDEBAR_SHOW_SECONDS;

    // ---- Anti-XRay ----
    public static boolean ANTIXRAY_ENABLED;

    // ---- World Backups ----
    public static int     BACKUP_MAX_COUNT;
    public static String  BACKUP_DIR;
    public static boolean BACKUP_PERIODIC_ENABLED;
    public static int     BACKUP_INTERVAL_HOURS;
    public static boolean BACKUP_PUBLIC_DOWNLOAD;
    public static int     BACKUP_PUBLIC_MAX_CONCURRENT; // 0 = follow server max-players
    public static int     BACKUP_PUBLIC_MAX_PER_IP;
    // Owner opt-in: expose the world seed to public downloaders.
    public static boolean BACKUP_PUBLIC_SEED_DISCLOSURE;

    // ---- Web Panel link (gated on public download) ----
    public static String  PANEL_URL;
    public static String  PANEL_MESSAGE;
    public static boolean PANEL_MESSAGE_ENABLED;
    public static int     PANEL_MESSAGE_INTERVAL; // Seconds

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
    public static boolean SHOPS_ENABLED;
    public static boolean ECONOMY_ENABLED;

    // ---- Kits ----
    public static boolean KITS_ENABLED;
    public static long KIT_COOLDOWN_SECONDS;
    public static java.util.List<ConfigData.KitDef> KIT_DEFINITIONS = new java.util.ArrayList<>();

    // ---- Team Auto-Assign ----
    public static java.util.Map<String, java.util.List<String>> TEAM_AUTO_ASSIGN = new java.util.HashMap<>();

    // ---- Skills ----
    public static double SKILL_XP_EXPONENT;
    public static java.util.Map<String, Long> SKILL_COOLDOWNS = new java.util.HashMap<>();
    public static java.util.Map<String, Integer> SKILL_UNLOCK_LEVELS = new java.util.HashMap<>();
    // Per-parent max bonuses at level 100: movement speed, hearts, damage, xp orbs,
    // double-drop chance, armor points, fall-damage absorbed (linear).
    public static double CAP_INDUSTRIAL_SPEED;
    public static double CAP_NATURE_HEALTH;
    public static double CAP_COMBAT_DAMAGE;
    public static double CAP_KNOWLEDGE_XP;
    public static double CAP_DOUBLE_DROP;
    public static double CAP_DEFENSE_ARMOR;
    public static double CAP_SAFE_LANDING;

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
        SPAWN_NO_HOSTILES = d.spawnNoHostiles;
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

        WELCOME_SIDEBAR_ENABLED = d.welcomeSidebarEnabled;
        WELCOME_SIDEBAR_TITLE = d.welcomeSidebarTitle;
        WELCOME_SIDEBAR_LINES = d.welcomeSidebarLines;
        WELCOME_SIDEBAR_SHOW_SECONDS = d.welcomeSidebarShowSeconds;

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
        BACKUP_PUBLIC_SEED_DISCLOSURE = d.backupPublicSeedDisclosure;
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
