package mc.smpessentials.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import mc.smpessentials.platform.SmpServices;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

    public static Path path() {
        return SmpServices.PLATFORM.getConfigDir().resolve(FILE_NAME);
    }

    // Returns the chatfilter import queue from the config file without writing the file back.
    public static ConfigData.ChatFilterData readChatFilterQueue() {
        Path p = path();
        try {
            if (Files.exists(p) && Files.size(p) > 0) {
                String s = Files.readString(p, StandardCharsets.UTF_8);
                ConfigData data = GSON.fromJson(s, ConfigData.class);
                if (data != null && data.chatfilter != null) {
                    return data.chatfilter;
                }
            }
        } catch (JsonSyntaxException | IOException ignored) {
        }
        return new ConfigData.ChatFilterData();
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
                data = GSON.fromJson(s, ConfigData.class);
            }
            if (data == null) {
                data = new ConfigData();
            } else {
                data.mergeDefaults();
            }
            Files.writeString(p, GSON.toJson(data), StandardCharsets.UTF_8);
            return data;
        } catch (JsonSyntaxException e) {
            throw new ConfigParseException(
                    "Malformed JSON in " + FILE_NAME + ": " + e.getMessage(), e);
        } catch (IOException e) {
            return new ConfigData();
        }
    }

    // Thrown when quackedsmp.json contains invalid JSON syntax.
    public static class ConfigParseException extends RuntimeException {
        public ConfigParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // Serializes current SmpConfig static fields back to disk.
    // chatfilter is always written as empty arrays; it is an import queue, not persistent storage.
    public static void save() {
        ConfigData data = new ConfigData();

        data.maxClaims = SmpConfig.MAX_CLAIMS;
        data.tpWarmup = SmpConfig.TP_WARMUP;
        data.welcomeMessage = SmpConfig.WELCOME_MESSAGE;
        data.messageInterval = SmpConfig.MESSAGE_INTERVAL;
        data.allowLavaWilderness = SmpConfig.ALLOW_LAVA_WILDERNESS;
        data.allowFireWilderness = SmpConfig.ALLOW_FIRE_WILDERNESS;
        data.spawnNoPvp = SmpConfig.SPAWN_NO_PVP;
        data.protectExplosions = SmpConfig.PROTECT_EXPLOSIONS;
        data.protectFireClaims = SmpConfig.PROTECT_FIRE_CLAIMS;
        data.protectEnderman = SmpConfig.PROTECT_ENDERMAN;
        data.protectFarmland = SmpConfig.PROTECT_FARMLAND;
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
        data.muteLevelsMinutes = new java.util.ArrayList<>(SmpConfig.MUTE_LEVELS_MINUTES);
        data.tiers = new java.util.ArrayList<>(SmpConfig.TIERS);
        data.rules = new java.util.ArrayList<>(SmpConfig.RULES);
        data.periodicMessages = new java.util.ArrayList<>(SmpConfig.PERIODIC_MESSAGES);
        data.messages = new java.util.HashMap<>(SmpConfig.MESSAGES);

        data.hardcoreEnabled = SmpConfig.HARDCORE_ENABLED;
        data.hardcoreDeathPercent = SmpConfig.HARDCORE_DEATH_PERCENT;
        data.hardcoreWitheredHearts = SmpConfig.HARDCORE_WITHERED_HEARTS;

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
        data.backupPublicDownload       = SmpConfig.BACKUP_PUBLIC_DOWNLOAD;
        data.backupPublicMaxConcurrent  = SmpConfig.BACKUP_PUBLIC_MAX_CONCURRENT;
        data.backupPublicMaxPerIp       = SmpConfig.BACKUP_PUBLIC_MAX_PER_IP;
        data.panelUrl                   = SmpConfig.PANEL_URL;
        data.panelMessage               = SmpConfig.PANEL_MESSAGE;
        data.panelMessageEnabled        = SmpConfig.PANEL_MESSAGE_ENABLED;
        data.panelMessageInterval       = SmpConfig.PANEL_MESSAGE_INTERVAL;
        data.shopsEnabled = SmpConfig.SHOPS_ENABLED;
        data.economyEnabled = SmpConfig.ECONOMY_ENABLED;
        data.kitsEnabled = SmpConfig.KITS_ENABLED;
        data.kits.cooldownSeconds = SmpConfig.KIT_COOLDOWN_SECONDS;
        data.kits.kits = new java.util.ArrayList<>(SmpConfig.KIT_DEFINITIONS);

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

        // chatfilter intentionally left as empty arrays; it is an import queue.

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
