package mc.smpessentials.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// GSON POJO for quackedsmp.json. All defaults live here; one place to change them.
// FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES maps camelCase fields to snake_case JSON keys.
public final class ConfigData {

    public int maxClaims = 50;
    public int tpWarmup = 5;
    public String welcomeMessage = "&6Welcome to QuackedSMP, {player}!";
    public int messageInterval = 300;
    public boolean allowLavaWilderness = false;
    public boolean allowFireWilderness = false;
    public boolean spawnNoPvp = true;
    public boolean spawnNoHostiles = true;
    public boolean protectExplosions = true;
    public boolean protectFireClaims = true;
    public boolean protectEnderman = true;
    public boolean protectFarmland = true;
    public boolean claimsEnabled = true;
    public boolean skillsEnabled = true;
    public boolean chatfilterEnabled = true;
    public boolean voicechatEnable = true;
    public boolean bluemapEnable = true;
    public boolean bluemapShowHomes = true;
    public boolean bluemapShowClaims = true;
    public boolean bluemapShowWorldborder = true;
    public String bluemapWorldborderColor = "FF3C3C";
    public String bluemapClaimColor = "00FFFF";
    public String bluemapOpClaimColor = "FFD700";
    public String bluemapVipClaimColor = "8A2BE2";
    public boolean bluemapShowSpawnProtection = true;
    public String bluemapSpawnProtectionColor = "80409040";

    public List<Integer> muteLevelsMinutes = new ArrayList<>(List.of(60, 120, 240, 480, 1440));

    public List<SmpConfig.TierDef> tiers = new ArrayList<>(List.of(
            new SmpConfig.TierDef(1, "VIP", 100, 20)));

    public List<String> rules = new ArrayList<>(List.of(
            "&e1. Be respectful. 2. No GRIEFING. 3. No cheating.",
            "&e4. Voice chat is strictly 18+. Falsely verifying your age will result in a ban.",
            "&e5. Do not attempt to bypass the chat filter or use slurs.",
            "&e6. Found a bug or exploit? Report it to dev@quackedmod.wiki for a reward!",
            "&e7. Build bases FAR from spawn. Use the RTP portal!",
            "&e8. Spawn's communal area is for cool builds to show off!"));

    public List<String> periodicMessages = new ArrayList<>(List.of(
            "&b[Tip] &fUse &a/claim &fto protect your land!",
            "&b[Tip] &fSleep in a bed to set your &a/home&f location!",
            "&b[Tip] &fType &a/smp help &ffor a list of commands!",
            "&b[Tip] &fPlease respect the &6/rules&f!",
            "&b[Tip] &fUnlock active abilities by leveling up your &a/skills&f!",
            "&b[Tip] &fUse &a/tpr <player>&f to teleport to friends!",
            "&b[Tip] &fStuck with an intruder? Use &a/sos &fto eject them from your claim!",
            "&b[Tip] &fReach &aLevel 10 &fin a skill to unlock its unique Active Ability!",
            "&b[Tip] &fUse &aSneak + Drop (Q) &fwith a tool to activate its ability!",
            "&b[Tip] &fActivate &bDash &fby &aSprinting + Jumping + Sneaking&f!",
            "&b[Tip] &fLeveling up skills grants passive buffs like &c+Health &fand &f+Speed!",
            "&b[Tip] &fReport griefers to &adev@quackedmod.wiki&f!",
            "&b[Tip] &fReset the ender dragon in the &6Shogun Temple &fin the village or ask an admin to reset the end world!",
            "&b[Tip] &fVisit Spawn Shops for blocks & gear! Trade items for Emeralds!",
            "&b[Tip] &fKeep Inventory is ON by default! If you prefer a challenge, type &a/smp keepinv off&f to drop your items on death.",
            "&b[Tip] &fNot sure where a claim ends? Use &a/claim map&f to visualize nearby chunk borders in chat!",
            "&b[Tip] &fBuilding with friends? Use &a/trust <player>&f to give them permission to safely build in your claims!",
            "&b[Tip] &fWe have proximity voice chat! If you're 18+, type &a/verify confirm&f to enable it and start talking!",
            "&b[Tip] &fWant to see who has the highest skills? Type &a/skills top&f to view the server leaderboards!",
            "&b[Tip] &fCheck out our live web-map! You can see your &a/home&f and even name your claims using &a/claim name <name>&f!",
            "&b[Tip] &fGet our recommended modpack for &ashaders&f, &aminimap&f, &avoice chat &fand more! [&bDownload Here](https://www.curseforge.com/minecraft/modpacks/play-quackedmod-wiki)",
            "&b[Tip] &fClaim your daily kit! &a/smp kit list &fto see what's available for you!",
            "&b[Tip] &fGetting bored? Try &ahardcore mode&f! &a/smp hardcore create &fto start a session!"));

    public Map<String, String> messages = new HashMap<>(Map.ofEntries(
            // Claiming
            Map.entry("claim.success", "Chunk claimed."),
            Map.entry("claim.already_claimed", "This claim is already protected."),
            Map.entry("claim.limit_reached", "You reached the claim limit ({count})."),
            Map.entry("claim.spawn_protected", "You can't claim inside spawn protection."),
            Map.entry("unclaim.success", "Chunk unclaimed."),
            Map.entry("unclaim.fail_ownership", "You don't control this claim."),
            Map.entry("claim.info.owned", "You own {count} chunk(s) total."),
            Map.entry("claim.info.protected_by_you", "This chunk is protected by you."),
            Map.entry("claim.info.protected", "This chunk is protected."),
            Map.entry("claim.info.unclaimed", "Current chunk is unclaimed."),
            // Teleportation
            Map.entry("tpr.sent", "Teleport request sent to {player}"),
            Map.entry("tpr.received", "{player} requested to teleport to you. Use /tpa accept or /tpa deny."),
            Map.entry("tpr.self", "Cannot request teleport to self."),
            Map.entry("tpr.cooldown", "A request was sent recently. Please wait."),
            Map.entry("tpr.already_pending", "A request is already pending."),
            Map.entry("tpr.queue_full", "That player has too many pending requests."),
            Map.entry("tpa.no_pending", "No pending teleport requests."),
            Map.entry("tpa.requester_offline", "Requester is no longer online."),
            Map.entry("tpa.requester_busy", "Requester cannot be teleported right now."),
            Map.entry("tpa.no_location", "No valid teleport location."),
            Map.entry("tpa.teleporting_requester", "Teleporting {player} in 5 seconds..."),
            Map.entry("tpa.teleporting_to", "Teleported to {player}"),
            Map.entry("tpa.denied", "Teleport request denied by {player}"),
            Map.entry("tpa.denied_confirm", "Denied the oldest pending request.")));

    // Fills in any map entries missing from the file using defaults.
    // Called after GSON deserialization so new keys added in future versions appear automatically.
    void mergeDefaults() {
        ConfigData defaults = new ConfigData();
        defaults.messages.forEach(messages::putIfAbsent);
        defaults.skills.cooldowns.forEach(skills.cooldowns::putIfAbsent);
        defaults.skills.abilityUnlockLevels.forEach(skills.abilityUnlockLevels::putIfAbsent);
    }

    public boolean hardcoreEnabled = false;
    // Percent of peak players that must die to end a session. 100 = ends only when everyone dies.
    public int hardcoreDeathPercent = 100;
    public boolean hardcoreWitheredHearts = true;
    // Optional scoreboard-team identity for session members (nametag/tab color). Off by
    // default: current behavior is unchanged until enabled. The team's color/prefix are edited
    // in the dashboard teams editor; only the name is fixed here.
    public boolean hardcoreTeamVisibility = false;
    public String  hardcoreTeamName       = "hardcore";
    // Per-player run-time sidebar for session members. Transient: shown periodically and on
    // session entry, not permanently on screen.
    public boolean hardcoreSidebarEnabled        = false;
    // Mean gap between periodic sidebar pulses (jittered +/-50% at runtime). ~45 minutes.
    public int     hardcoreSidebarIntervalSeconds = 2700;
    // How long the periodic pulse stays on screen.
    public int     hardcoreSidebarShowSeconds     = 20;
    // How long the flash on session create/join stays on screen.
    public int     hardcoreSidebarOnEntrySeconds  = 10;

    public VotifierConfig votifier = new VotifierConfig();
    public DashboardConfig dashboard = new DashboardConfig();
    public DiscordConfig discord = new DiscordConfig();
    public SkillsConfig skills = new SkillsConfig();
    // Import queue: loaded into NBT on startup/reload, then cleared from the file by ConfigIO.save().
    public ChatFilterData chatfilter = new ChatFilterData();
    public boolean kitsEnabled = true;
    public Map<String, List<String>> teamAutoAssign = new HashMap<>();

    public boolean antixrayEnabled = true;
    public int     backupMaxCount             = 5;
    public String  backupDir                  = "backups";
    public boolean backupPeriodicEnabled      = false;
    public int     backupIntervalHours        = 24;
    public boolean backupPublicDownload       = false;
    public int     backupPublicMaxConcurrent  = 0;
    public int     backupPublicMaxPerIp       = 2;
    // Public web panel link. Only surfaced (via /smp download and the optional periodic
    // broadcast) when backupPublicDownload is on AND panelUrl is a non-blank http/https URL.
    // panelMessage supports & color codes and [label](url) clickable links (see TextUtil).
    // {url} is replaced with panelUrl before formatting.
    public String  panelUrl                   = "";
    public String  panelMessage               = "&aServer web panel & world download: [Click here]({url})";
    // Toggles the periodic chat broadcast of the link. Independent of periodicMessages.
    public boolean panelMessageEnabled        = false;
    public int     panelMessageInterval       = 1800; // Seconds, minimum 60 enforced on save
    public boolean shopsEnabled = false;
    public boolean economyEnabled = false;
    public KitsConfig kits = new KitsConfig();

    public static final class VotifierConfig {
        public boolean enabled = false;
        public int port = 8192;
        public String token = "";
        public String broadcast = "&6\u2736 {player} &ejust voted for the server! Thanks for the support!";
        public List<String> rewards = new ArrayList<>(List.of(
                "give {player} diamond 2",
                "give {player} emerald 5",
                "give {player} gold_ingot 10"));
        // Tier-keyed bonus rewards. Each tier's list stacks on top of all lower tiers.
        // e.g. a tier 2 player gets: base reward + tier 1 bonus + tier 2 bonus.
        public Map<String, List<String>> vipRewards = new HashMap<>();
    }

    public static final class DashboardConfig {
        public boolean enabled = false;
        public int port = 8125;
        public boolean adminEnabled = false;
        public String adminPasswordHash = "";
        public String serverName = "";
    }

    public static final class DiscordConfig {
        public String webhookUrl = "";
        public boolean joinLeave = true;
        public boolean chat = true;
    }

    public static final class SkillsConfig {
        public double xpExponent = 1.5;
        public Map<String, Long> cooldowns = new HashMap<>(Map.ofEntries(
                Map.entry("mining", 240L),
                Map.entry("excavation", 300L),
                Map.entry("woodcutting", 300L),
                Map.entry("farming", 180L),
                Map.entry("fishing", 300L),
                Map.entry("agility", 10L),
                Map.entry("melee", 300L),
                Map.entry("archery", 180L),
                Map.entry("archery_zoom", 30L),
                Map.entry("defense", 600L),
                Map.entry("enchanting", 1200L),
                Map.entry("alchemy", 600L),
                Map.entry("trading", 1200L)));
        public Map<String, Integer> abilityUnlockLevels = new HashMap<>(Map.ofEntries(
                Map.entry("mining", 10),
                Map.entry("excavation", 10),
                Map.entry("woodcutting", 10),
                Map.entry("farming", 10),
                Map.entry("fishing", 10),
                Map.entry("agility", 3),
                Map.entry("melee", 10),
                Map.entry("archery", 10),
                Map.entry("archery_zoom", 5),
                Map.entry("defense", 5),
                Map.entry("enchanting", 10),
                Map.entry("alchemy", 10),
                Map.entry("trading", 1)));
        public SkillCaps caps = new SkillCaps();
    }

    public static final class SkillCaps {
        public double industrialSpeed = 0.5;
        public double natureHealth = 10.0;
        public double combatDamage = 1.0;
        public double knowledgeXp = 1.0;
        public double doubleDrop = 0.5;
        public double defenseArmor = 10.0;
        public double safeLanding = 1.0;
    }

    public static final class ChatFilterData {
        public List<String> contents = new ArrayList<>();
        public List<String> whitelist = new ArrayList<>();
    }

    public static final class KitsConfig {
        public long cooldownSeconds = 86400;
        public List<KitDef> kits = new ArrayList<>(List.of(
                new KitDef("starter", "&aStarter Kit", 0,
                        new KitArmor("minecraft:leather_helmet", "minecraft:leather_chestplate",
                                "minecraft:leather_leggings", "minecraft:leather_boots"),
                        new ArrayList<>(List.of(
                                new KitItem("minecraft:wooden_sword", 1),
                                new KitItem("minecraft:bread", 16),
                                new KitItem("minecraft:torch", 16),
                                new KitItem("minecraft:oak_log", 16),
                                new KitItem("minecraft:crafting_table", 1),
                                new KitItem("minecraft:wheat_seeds", 8),
                                new KitItem("minecraft:carrot", 4)))),
                new KitDef("vip", "&6VIP Kit", 1,
                        new KitArmor("minecraft:iron_helmet", "minecraft:iron_chestplate",
                                "minecraft:iron_leggings", "minecraft:iron_boots"),
                        new ArrayList<>(List.of(
                                new KitItem("minecraft:iron_sword", 1),
                                new KitItem("minecraft:cooked_beef", 32),
                                new KitItem("minecraft:torch", 32),
                                new KitItem("minecraft:oak_log", 32),
                                new KitItem("minecraft:golden_apple", 2),
                                new KitItem("minecraft:iron_pickaxe", 1))))));
    }

    public static final class KitDef {
        public String name = "starter";
        public String displayName = "&aStarter Kit";
        public int minTier = 0;
        public KitArmor armor = new KitArmor();
        public List<KitItem> items = new ArrayList<>();

        public KitDef() {}

        public KitDef(String name, String displayName, int minTier, KitArmor armor, List<KitItem> items) {
            this.name = name;
            this.displayName = displayName;
            this.minTier = minTier;
            this.armor = armor;
            this.items = items;
        }
    }

    public static final class KitArmor {
        public String head = "";
        public String chest = "";
        public String legs = "";
        public String feet = "";

        public KitArmor() {}

        public KitArmor(String head, String chest, String legs, String feet) {
            this.head = head;
            this.chest = chest;
            this.legs = legs;
            this.feet = feet;
        }
    }

    public static final class KitItem {
        public String item = "minecraft:stone";
        public int count = 1;

        public KitItem() {}

        public KitItem(String item, int count) {
            this.item = item;
            this.count = count;
        }
    }
}
