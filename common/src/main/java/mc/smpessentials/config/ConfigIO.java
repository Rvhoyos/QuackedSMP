package mc.smpessentials.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import mc.smpessentials.platform.SmpServices;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Shared config file helpers for all packages.
public final class ConfigIO {
    private static final String FILE_NAME = "quackedsmp.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private ConfigIO() {
    }

    // The one Gson configured for this mod's config shapes. Anything that serialises a piece of
    // ConfigData outside this class (the dashboard's RTP editor) uses it so field naming matches
    // quackedsmp.json exactly.
    public static Gson gson() {
        return GSON;
    }

    public static Path path() {
        return SmpServices.PLATFORM.getConfigDir().resolve(FILE_NAME);
    }

    // The in-memory mirror of quackedsmp.json, of which the SmpConfig statics are the live subset.
    // save() copies the statics onto this rather than onto a blank ConfigData, so a field with no
    // copy line in save() keeps its stored value instead of reverting to its factory default.
    private static ConfigData stored;

    // Words rescued from the retired chatfilter import queue, null when there is nothing to migrate.
    private static LegacyChatFilterQueue legacyChatFilter;

    /** Blocked words and whitelist found in the pre-2.3.3 {@code chatfilter} config block. */
    public record LegacyChatFilterQueue(List<String> contents, List<String> whitelist) {
    }

    // Reads the config file, filling in defaults for any missing fields.
    // Always writes the result back to disk so new fields appear immediately.
    public static ConfigData readOrCreate() {
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            ConfigData data = null;
            if (Files.exists(p) && Files.size(p) > 0) {
                String s = Files.readString(p, StandardCharsets.UTF_8);
                JsonObject tree = GSON.fromJson(s, JsonObject.class);
                // Before the write-back below drops the key, since ConfigData no longer has a field
                // for it and Gson silently discards what it cannot map.
                captureLegacyChatFilter(tree);
                data = GSON.fromJson(tree, ConfigData.class);
            }
            if (data == null) {
                data = new ConfigData();
            } else {
                data.mergeDefaults();
            }
            Files.writeString(p, GSON.toJson(data), StandardCharsets.UTF_8);
            stored = data;
            return data;
        } catch (JsonSyntaxException e) {
            throw new ConfigParseException(
                    "Malformed JSON in " + FILE_NAME + ": " + e.getMessage(), e);
        } catch (IOException e) {
            stored = new ConfigData();
            return stored;
        }
    }

    // The chat filter's config import queue was retired: words live in NBT and are edited from the
    // panel or /chatfilter add, both of which already take a list. A config written before that can
    // still hold words nobody imported, so take them here and log them, because the write-back
    // happens at mod init and the server that owns the NBT does not exist yet.
    private static void captureLegacyChatFilter(JsonObject tree) {
        if (tree == null || !tree.has("chatfilter") || !tree.get("chatfilter").isJsonObject()) return;
        JsonObject block = tree.getAsJsonObject("chatfilter");
        List<String> contents = stringList(block, "contents");
        List<String> whitelist = stringList(block, "whitelist");
        if (contents.isEmpty() && whitelist.isEmpty()) return;

        legacyChatFilter = new LegacyChatFilterQueue(contents, whitelist);
        mc.smpessentials.SmpUtilsMod.LOGGER.info(
                "[Config] Retired chatfilter config block holds {} blocked and {} whitelisted words,"
                        + " importing them into the filter. Blocked: {}. Whitelisted: {}",
                contents.size(), whitelist.size(),
                String.join(", ", contents), String.join(", ", whitelist));
    }

    private static List<String> stringList(JsonObject block, String key) {
        if (!block.has(key) || !block.get(key).isJsonArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonElement element : block.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive()) continue;
            String word = element.getAsString().strip();
            if (!word.isEmpty()) out.add(word);
        }
        return out;
    }

    // Hands the rescued words over once, then forgets them. Null for any config that has been
    // through a single start since the queue existed, because the queue emptied itself on write.
    public static LegacyChatFilterQueue takeLegacyChatFilterQueue() {
        LegacyChatFilterQueue queue = legacyChatFilter;
        legacyChatFilter = null;
        return queue;
    }

    // Thrown when quackedsmp.json contains invalid JSON syntax.
    public static class ConfigParseException extends RuntimeException {
        public ConfigParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // Serializes current SmpConfig static fields back to disk, over the settings already stored.
    // Every field a panel or a command can change needs a copy line below. A field without one keeps
    // its stored value, so forgetting a line means the setting cannot be saved, never that the
    // owner's value is overwritten with a default.
    public static void save() {
        ConfigData data = stored != null ? stored : new ConfigData();

        data.maxClaims = SmpConfig.MAX_CLAIMS;
        data.tpWarmup = SmpConfig.TP_WARMUP;
        data.welcomeMessage = SmpConfig.WELCOME_MESSAGE;
        data.messageInterval = SmpConfig.MESSAGE_INTERVAL;
        data.allowLavaWilderness = SmpConfig.ALLOW_LAVA_WILDERNESS;
        data.allowFireWilderness = SmpConfig.ALLOW_FIRE_WILDERNESS;
        data.spawnNoPvp = SmpConfig.SPAWN_NO_PVP;
        data.spawnNoHostiles = SmpConfig.SPAWN_NO_HOSTILES;
        data.protectExplosions = SmpConfig.PROTECT_EXPLOSIONS;
        data.protectFireClaims = SmpConfig.PROTECT_FIRE_CLAIMS;
        data.protectEnderman = SmpConfig.PROTECT_ENDERMAN;
        data.protectFarmland = SmpConfig.PROTECT_FARMLAND;
        data.keepInvEnabled = SmpConfig.KEEP_INV_ENABLED;
        data.teleportEnabled = SmpConfig.TELEPORT_ENABLED;
        data.dimsEnabled = SmpConfig.DIMS_ENABLED;
        data.backupEnabled = SmpConfig.BACKUP_ENABLED;
        data.claimsEnabled = SmpConfig.CLAIMS_ENABLED;
        data.skillsEnabled = SmpConfig.SKILLS_ENABLED;
        data.chatfilterEnabled = SmpConfig.CHATFILTER_ENABLED;
        data.voicechatEnable = SmpConfig.VOICECHAT_ENABLE;
        data.bluemapEnable = SmpConfig.BLUEMAP_ENABLE;
        data.bluemapShowHomes = SmpConfig.BLUEMAP_SHOW_HOMES;
        data.bluemapShowClaims = SmpConfig.BLUEMAP_SHOW_CLAIMS;
        data.bluemapShowWorldborder = SmpConfig.BLUEMAP_SHOW_WORLDBORDER;
        data.bluemapWorldborderColor = SmpConfig.BLUEMAP_WORLDBORDER_COLOR;
        data.bluemapClaimColor = SmpConfig.BLUEMAP_CLAIM_COLOR;
        data.bluemapOpClaimColor = SmpConfig.BLUEMAP_OP_CLAIM_COLOR;
        data.bluemapVipClaimColor = SmpConfig.BLUEMAP_VIP_CLAIM_COLOR;
        data.bluemapShowSpawnProtection = SmpConfig.BLUEMAP_SHOW_SPAWN_PROTECTION;
        data.bluemapSpawnProtectionColor = SmpConfig.BLUEMAP_SPAWN_PROTECTION_COLOR;
        data.bluemapIconMaxDistance = SmpConfig.BLUEMAP_ICON_MAX_DISTANCE;
        data.bluemapShowShops = SmpConfig.BLUEMAP_SHOW_SHOPS;
        data.bluemapShowYoutube = SmpConfig.BLUEMAP_SHOW_YOUTUBE;
        data.muteLevelsMinutes = new java.util.ArrayList<>(SmpConfig.MUTE_LEVELS_MINUTES);
        data.tiers = new java.util.ArrayList<>(SmpConfig.TIERS);
        data.rules = new java.util.ArrayList<>(SmpConfig.RULES);
        data.periodicMessages = new java.util.ArrayList<>(SmpConfig.PERIODIC_MESSAGES);
        data.messages = new java.util.HashMap<>(SmpConfig.MESSAGES);

        data.hardcoreEnabled = SmpConfig.HARDCORE_ENABLED;
        data.hardcoreDeathPercent = SmpConfig.HARDCORE_DEATH_PERCENT;
        data.hardcoreWitheredHearts = SmpConfig.HARDCORE_WITHERED_HEARTS;
        data.hardcoreStartAtDay     = SmpConfig.HARDCORE_START_AT_DAY;
        data.hardcoreTeamVisibility = SmpConfig.HARDCORE_TEAM_VISIBILITY;
        data.hardcoreTeamName = SmpConfig.HARDCORE_TEAM_NAME;
        data.hardcoreSidebarEnabled = SmpConfig.HARDCORE_SIDEBAR_ENABLED;
        data.hardcoreSidebarIntervalSeconds = SmpConfig.HARDCORE_SIDEBAR_INTERVAL_SECONDS;
        data.hardcoreSidebarShowSeconds = SmpConfig.HARDCORE_SIDEBAR_SHOW_SECONDS;
        data.hardcoreSidebarOnEntrySeconds = SmpConfig.HARDCORE_SIDEBAR_ON_ENTRY_SECONDS;

        data.welcomeSidebarEnabled = SmpConfig.WELCOME_SIDEBAR_ENABLED;
        data.welcomeSidebarTitle = SmpConfig.WELCOME_SIDEBAR_TITLE;
        data.welcomeSidebarLines = new java.util.ArrayList<>(SmpConfig.WELCOME_SIDEBAR_LINES);
        data.welcomeSidebarShowSeconds = SmpConfig.WELCOME_SIDEBAR_SHOW_SECONDS;

        data.slimeHintEnabled = SmpConfig.SLIME_HINT_ENABLED;
        data.slimeHintIntervalTicks = SmpConfig.SLIME_HINT_INTERVAL_TICKS;
        data.slimeHintParticleCount = SmpConfig.SLIME_HINT_PARTICLE_COUNT;
        data.slimeHintSoundChance = SmpConfig.SLIME_HINT_SOUND_CHANCE;

        data.endFinderEnabled = SmpConfig.END_FINDER_ENABLED;
        data.endFinderSearchRadius = SmpConfig.END_FINDER_SEARCH_RADIUS;
        data.endFinderRecheckDistance = SmpConfig.END_FINDER_RECHECK_DISTANCE;
        data.endFinderRecheckCooldownSeconds = SmpConfig.END_FINDER_RECHECK_COOLDOWN_SECONDS;
        data.endFinderActionBar = SmpConfig.END_FINDER_ACTION_BAR;

        data.etherSkyEntryEnabled = SmpConfig.ETHER_SKY_ENTRY_ENABLED;
        data.etherSkyEntryY = SmpConfig.ETHER_SKY_ENTRY_Y;

        data.votifier.enabled = SmpConfig.VOTIFIER_ENABLED;
        data.votifier.port = SmpConfig.VOTIFIER_PORT;
        data.votifier.token = SmpConfig.VOTIFIER_TOKEN;
        data.votifier.broadcast = SmpConfig.VOTE_BROADCAST;
        data.votifier.rewards = new java.util.ArrayList<>(SmpConfig.VOTE_REWARDS);
        data.votifier.vipRewards = new java.util.HashMap<>();
        for (var entry : SmpConfig.VOTE_VIP_REWARDS.entrySet()) {
            data.votifier.vipRewards.put(String.valueOf(entry.getKey()), new java.util.ArrayList<>(entry.getValue()));
        }

        data.dashboard.enabled = SmpConfig.DASHBOARD_ENABLED;
        data.dashboard.port = SmpConfig.DASHBOARD_PORT;
        data.dashboard.adminEnabled = SmpConfig.ADMIN_ENABLED;
        data.dashboard.adminPasswordHash = SmpConfig.ADMIN_PASSWORD_HASH;
        data.dashboard.serverName = SmpConfig.SERVER_NAME;

        data.discord.webhookUrl = SmpConfig.DISCORD_WEBHOOK_URL;
        data.discord.joinLeave = SmpConfig.DISCORD_JOIN_LEAVE;
        data.discord.chat = SmpConfig.DISCORD_CHAT;

        data.teamAutoAssign = new java.util.HashMap<>();
        for (var entry : SmpConfig.TEAM_AUTO_ASSIGN.entrySet()) {
            data.teamAutoAssign.put(entry.getKey(), new java.util.ArrayList<>(entry.getValue()));
        }

        data.antixrayEnabled = SmpConfig.ANTIXRAY_ENABLED;
        data.backupMaxCount             = SmpConfig.BACKUP_MAX_COUNT;
        data.backupDir                  = SmpConfig.BACKUP_DIR;
        data.backupPeriodicEnabled      = SmpConfig.BACKUP_PERIODIC_ENABLED;
        data.backupIntervalHours        = SmpConfig.BACKUP_INTERVAL_HOURS;
        data.backupPublicDownload       = SmpConfig.BACKUP_PUBLIC_DOWNLOAD;
        data.backupPublicSeedDisclosure = SmpConfig.BACKUP_PUBLIC_SEED_DISCLOSURE;
        data.backupPublicMaxConcurrent  = SmpConfig.BACKUP_PUBLIC_MAX_CONCURRENT;
        data.backupPublicMaxPerIp       = SmpConfig.BACKUP_PUBLIC_MAX_PER_IP;
        data.panelUrl                   = SmpConfig.PANEL_URL;
        data.panelMessage               = SmpConfig.PANEL_MESSAGE;
        data.panelMessageEnabled        = SmpConfig.PANEL_MESSAGE_ENABLED;
        data.panelMessageInterval       = SmpConfig.PANEL_MESSAGE_INTERVAL;
        data.shopsEnabled = SmpConfig.SHOPS_ENABLED;
        data.economyEnabled = SmpConfig.ECONOMY_ENABLED;

        data.timelapseEnabled         = SmpConfig.TIMELAPSE_ENABLED;
        data.timelapseIntervalMinutes = SmpConfig.TIMELAPSE_INTERVAL_MINUTES;
        data.timelapseDir             = SmpConfig.TIMELAPSE_DIR;
        data.timelapseDimensions      = new java.util.ArrayList<>(SmpConfig.TIMELAPSE_DIMENSIONS);
        data.timelapseMaxRenderMb     = SmpConfig.TIMELAPSE_MAX_RENDER_MB;
        data.timelapseMaxSkips        = SmpConfig.TIMELAPSE_MAX_SKIPS;
        data.timelapseMaxFrames       = SmpConfig.TIMELAPSE_MAX_FRAMES;

        data.pregenEnabled     = SmpConfig.PREGEN_ENABLED;
        data.pregen.distance   = SmpConfig.PREGEN_DISTANCE;
        data.pregen.dimensions = new java.util.ArrayList<>(SmpConfig.PREGEN_DIMENSIONS);
        data.kitsEnabled = SmpConfig.KITS_ENABLED;
        data.kits.cooldownSeconds = SmpConfig.KIT_COOLDOWN_SECONDS;
        data.kits.kits = new java.util.ArrayList<>(SmpConfig.KIT_DEFINITIONS);

        data.welcomeBook.enabled = SmpConfig.WELCOME_BOOK_ENABLED;
        data.welcomeBook.content = SmpConfig.WELCOME_BOOK_CONTENT;

        data.rtpEnabled = SmpConfig.RTP_ENABLED;
        data.rtp.warmupSeconds = SmpConfig.RTP_WARMUP_SECONDS;
        data.rtp.cooldownSeconds = SmpConfig.RTP_COOLDOWN_SECONDS;
        data.rtp.profiles = new java.util.ArrayList<>(SmpConfig.RTP_PROFILES);

        data.skills.xpExponent = SmpConfig.SKILL_XP_EXPONENT;
        data.skills.cooldowns = new java.util.HashMap<>(SmpConfig.SKILL_COOLDOWNS);
        data.skills.abilityUnlockLevels = new java.util.HashMap<>(SmpConfig.SKILL_UNLOCK_LEVELS);
        data.skills.caps.industrialSpeed = SmpConfig.CAP_INDUSTRIAL_SPEED;
        data.skills.caps.natureHealth = SmpConfig.CAP_NATURE_HEALTH;
        data.skills.caps.combatDamage = SmpConfig.CAP_COMBAT_DAMAGE;
        data.skills.caps.knowledgeXp = SmpConfig.CAP_KNOWLEDGE_XP;
        data.skills.caps.doubleDrop = SmpConfig.CAP_DOUBLE_DROP;
        data.skills.caps.defenseArmor = SmpConfig.CAP_DEFENSE_ARMOR;
        data.skills.caps.safeLanding = SmpConfig.CAP_SAFE_LANDING;

        stored = data;

        try {
            Files.writeString(path(), GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            mc.smpessentials.SmpUtilsMod.LOGGER.error("[Config] Failed to save config: {}", e.getMessage());
        }
    }

    public static void resetToFactory() {
        try {
            Files.deleteIfExists(path());
            SmpConfig.load();
        } catch (IOException e) {
            mc.smpessentials.SmpUtilsMod.LOGGER.error("[Config] Failed to reset config: {}", e.getMessage());
        }
    }
}
