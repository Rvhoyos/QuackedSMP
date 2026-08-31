package mc.smpessentials.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
    // Big features default OFF: this is a modular plugin, operators opt in per feature.
    public boolean claimsEnabled = false;
    public boolean skillsEnabled = false;
    public boolean chatfilterEnabled = false;
    public boolean voicechatEnable = false;
    public boolean bluemapEnable = false;
    public boolean bluemapShowHomes = true;
    public boolean bluemapShowClaims = true;
    public boolean bluemapShowWorldborder = true;
    public String bluemapWorldborderColor = "FF3C3C";
    public String bluemapClaimColor = "00FFFF";
    public String bluemapOpClaimColor = "FFD700";
    public String bluemapVipClaimColor = "8A2BE2";
    // Camera distance past which map icons stop drawing, so a zoomed out view is not a wall of
    // icons. Purely a looks setting: 0 disables the cutoff and icons always draw.
    public double bluemapIconMaxDistance = 2000.0;
    public boolean bluemapShowShops = true;
    public boolean bluemapShowYoutube = true;
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

        // Only reached when a config file already existed, which is exactly when there is
        // something older than the current kit format to bring forward.
        if (kits != null && kits.kits != null) {
            for (KitDef kit : kits.kits) {
                if (kit != null) kit.migrateLegacyStacks();
            }
        }
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
    // session entry, not permanently on screen. On by default; inert until hardcore is enabled
    // since it only ever targets players in a hardcore session.
    public boolean hardcoreSidebarEnabled        = true;
    // Mean gap between periodic sidebar pulses (jittered +/-50% at runtime). ~45 minutes.
    public int     hardcoreSidebarIntervalSeconds = 2700;
    // How long the periodic pulse stays on screen.
    public int     hardcoreSidebarShowSeconds     = 20;
    // How long the flash on session create/join stays on screen.
    public int     hardcoreSidebarOnEntrySeconds  = 10;

    // Welcome sidebar (2nd MOTD). Shown transiently when a player enters normal survival: on join
    // (unless in a hardcore session) and when returning to survival from a session. On by default,
    // and the default content is itself the nudge: it tells the owner to customize it in the panel
    // or config. Title and lines support & color codes and {player}/{server} placeholders.
    public boolean welcomeSidebarEnabled     = true;
    public String  welcomeSidebarTitle       = "&6{server}";
    public List<String> welcomeSidebarLines  = new ArrayList<>(List.of(
            "&eNew here? &f/rules",
            "&aProtect land: &f/claim",
            "&bSleep in a bed to set &f/home",
            "&dMore commands: &f/smp help",
            " ",
            "&8Owners: edit this in the panel",
            "&8(Config > Chat) or",
            "&8config/quackedsmp.json + /smp reload"));
    // How long the welcome sidebar stays on screen.
    public int     welcomeSidebarShowSeconds = 12;

    // World hints. Two ambient cues that make invisible world state readable without a client mod.

    // Slime chunk hint: slime particles at the feet (and a rare quiet squish) while standing in a
    // slime chunk below Y 40. The chunk test and the depth gate are vanilla's own spawn rule, so
    // the cue appears exactly where slimes can spawn. Density and sound rarity are taste, not a
    // derived limit, so they are here to tune.
    public boolean slimeHintEnabled        = true;
    public int     slimeHintIntervalTicks  = 20;
    public int     slimeHintParticleCount  = 2;
    public double  slimeHintSoundChance    = 0.02;

    // End finder: while an eye of ender is held in the overworld, mark the nearest stronghold on
    // the vanilla Locator Bar and show the distance on the action bar. The search is cached per
    // player and only re-run once they have travelled recheckDistance blocks AND the cooldown has
    // passed, since the nearest of the 128 ring-placed strongholds changes as you travel.
    public boolean endFinderEnabled                = true;
    public int     endFinderSearchRadius           = 100;
    public int     endFinderRecheckDistance        = 512;
    public int     endFinderRecheckCooldownSeconds = 30;
    public boolean endFinderActionBar              = true;

    // Sky entry: glide up past a threshold height in the overworld and cross into the linked ether
    // dim, the reverse of the void fall that already drops you out of one. Opt-in, like every other
    // feature that changes how the world behaves. 0 picks the height automatically, which is the
    // fall-out height (one above the overworld build limit) plus 20, so falling out while still
    // gliding cannot bounce you straight back up. See EtherVerticalTravel.
    public boolean etherSkyEntryEnabled = false;
    public int     etherSkyEntryY       = 0;

    public VotifierConfig votifier = new VotifierConfig();
    public DashboardConfig dashboard = new DashboardConfig();
    public DiscordConfig discord = new DiscordConfig();
    public SkillsConfig skills = new SkillsConfig();
    // Import queue: loaded into NBT on startup/reload, then cleared from the file by ConfigIO.save().
    public ChatFilterData chatfilter = new ChatFilterData();
    public boolean kitsEnabled = false;
    public Map<String, List<String>> teamAutoAssign = new HashMap<>();

    public boolean antixrayEnabled = true;
    public int     backupMaxCount             = 5;
    public String  backupDir                  = "backups";
    public boolean backupPeriodicEnabled      = false;
    public int     backupIntervalHours        = 24;
    public boolean backupPublicDownload       = false;
    public int     backupPublicMaxConcurrent  = 0;
    public int     backupPublicMaxPerIp       = 2;
    // When on, the world seed is disclosed to public downloaders (via /api/health) so they
    // can restore matching terrain. Off by default: a public seed lets players locate
    // structures/loot with online seed tools.
    public boolean backupPublicSeedDisclosure = false;
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

    // Welcome book: one command reference handed out by /guide, /smp help, kits and rtp arrivals.
    // On by default, like the welcome sidebar, since both exist to orient a new player.
    public WelcomeBookConfig welcomeBook = new WelcomeBookConfig();

    // Random teleport. One profile per dimension, so /rtp can behave differently in each.
    // Off by default (opt-in feature).
    public boolean rtpEnabled = false;
    public RtpConfig rtp = new RtpConfig();

    // World-map timelapse: periodically snapshots a top-down render of the world,
    // auto-sized to the generated chunk extent (read from region files, no BlueMap
    // dependency), to build a timelapse over time. Off by default (opt-in feature).
    public boolean timelapseEnabled         = false;
    public int     timelapseIntervalMinutes = 60;
    public String  timelapseDir             = "timelapse";
    // Dimensions to snapshot. Each capture (periodic or manual) renders every
    // listed dimension sequentially into its own parallel folder under
    // timelapseDir. Ids not present on the running server are skipped.
    public List<String> timelapseDimensions = new ArrayList<>(List.of("minecraft:overworld"));
    // Heap cap in MB for one render. 0 = auto: render 1 block = 1 pixel and fit
    // to the heap free at capture time, downsampling only if a capture would not
    // fit. A positive value caps forced (players-online) captures, trading map
    // resolution for RAM on a server that never idles; it has no effect on idle
    // captures. Idle captures always render at full resolution.
    public int     timelapseMaxRenderMb     = 0;
    // Consecutive overdue intervals to wait for an idle server before forcing a
    // capture while players are online. 0 = capture every interval regardless.
    public int     timelapseMaxSkips        = 3;
    // 0 = keep every frame. Above 0, once stored frames exceed this the oldest
    // are downsampled (every Nth dropped) so the timelapse degrades in smoothness
    // rather than losing its beginning.
    public int     timelapseMaxFrames       = 0;

    // Chunk pre-generation: generates the area around spawn at startup so players never walk into
    // terrain the server has to invent on the spot. Off by default (opt-in feature). The run is
    // blocking, so a server with this on does not accept joins until it finishes.
    public boolean pregenEnabled = false;
    public PregenConfig pregen = new PregenConfig();

    /** Which dimensions pre-generate and how far past the spawn protection radius they go. */
    public static final class PregenConfig {
        // Blocks beyond the spawn protection radius, in overworld scale. A dimension with a
        // coordinate scale of its own (the nether's 8) covers the same ground with 1/8 the radius.
        public int distance = 1000;
        // Ids not present on the running server are skipped, the same way timelapse treats them.
        public List<String> dimensions = new ArrayList<>(List.of("minecraft:overworld"));
    }

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
                new KitDef("starter", "&aStarter Kit", 0, true,
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
                new KitDef("vip", "&6VIP Kit", 1, false,
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
        // Hands over the welcome book alongside the kit. The book itself lives once under
        // welcomeBook, so this is a switch rather than a copy of the content.
        public boolean giveWelcomeBook = false;
        public KitArmor armor = new KitArmor();
        public List<KitItem> items = new ArrayList<>();

        public KitDef() {}

        public KitDef(String name, String displayName, int minTier, boolean giveWelcomeBook,
                      KitArmor armor, List<KitItem> items) {
            this.name = name;
            this.displayName = displayName;
            this.minTier = minTier;
            this.giveWelcomeBook = giveWelcomeBook;
            this.armor = armor;
            this.items = items;
        }

        void migrateLegacyStacks() {
            if (this.armor != null) this.armor.migrateLegacyStacks();
            if (this.items == null) return;
            for (KitItem item : this.items) {
                if (item != null) item.migrateLegacyStack();
            }
        }
    }

    /**
     * The four worn slots, each an ItemStack.CODEC JSON stack so a kit can hand out enchanted or
     * named armor rather than only a bare item id.
     */
    public static final class KitArmor {
        public JsonElement head;
        public JsonElement chest;
        public JsonElement legs;
        public JsonElement feet;

        public KitArmor() {}

        public KitArmor(String head, String chest, String legs, String feet) {
            this.head = stackOf(head);
            this.chest = stackOf(chest);
            this.legs = stackOf(legs);
            this.feet = stackOf(feet);
        }

        /**
         * Configs written before armor carried stacks stored a bare item id string in each slot.
         * The field names did not change, so the old value arrives here as a JSON string and is
         * rewritten in place as a one-item stack.
         */
        void migrateLegacyStacks() {
            this.head = migrateSlot(this.head);
            this.chest = migrateSlot(this.chest);
            this.legs = migrateSlot(this.legs);
            this.feet = migrateSlot(this.feet);
        }

        private static JsonElement migrateSlot(JsonElement slot) {
            if (slot == null || !slot.isJsonPrimitive() || !slot.getAsJsonPrimitive().isString()) {
                return slot;
            }
            return stackOf(slot.getAsString());
        }

        private static JsonElement stackOf(String itemId) {
            if (itemId == null || itemId.isBlank()) return null;
            JsonObject stack = new JsonObject();
            stack.addProperty("id", itemId);
            stack.addProperty("count", 1);
            return stack;
        }
    }

    /**
     * One kit item, held as ItemStack.CODEC JSON ({id, count, components}) so it can carry a
     * written book or a custom name. Same shape the random teleport arrival list uses.
     */
    public static final class KitItem {
        public JsonElement stack;

        /**
         * Configs written before kit items carried stacks stored a bare id and count in these two
         * fields. Read once on load, folded into {@code stack}, then dropped: Gson omits nulls, so
         * they disappear from quackedsmp.json the next time it is written.
         */
        public String item;
        public Integer count;

        public KitItem() {}

        public KitItem(String itemId, int count) {
            JsonObject s = new JsonObject();
            s.addProperty("id", itemId);
            s.addProperty("count", count);
            this.stack = s;
        }

        void migrateLegacyStack() {
            if (this.stack != null && !this.stack.isJsonNull()) {
                this.item = null;
                this.count = null;
                return;
            }
            if (this.item == null || this.item.isBlank()) return;

            JsonObject s = new JsonObject();
            s.addProperty("id", this.item);
            s.addProperty("count", this.count == null ? 1 : this.count);
            this.stack = s;
            this.item = null;
            this.count = null;
        }
    }

    /**
     * The welcome book. One book, stored once, handed out by every delivery path, so editing it
     * in the panel updates /guide, /smp help, kit rewards and rtp arrivals together.
     */
    public static final class WelcomeBookConfig {
        public boolean enabled = true;
        // minecraft:written_book_content JSON. Typed as JsonElement so it nests inline in
        // quackedsmp.json rather than becoming an escaped blob.
        public JsonElement content = mc.smpessentials.welcomebook.DefaultWelcomeBook.content();
    }

    /**
     * Random teleport. Global timings plus one profile per dimension.
     */
    public static final class RtpConfig {
        public int warmupSeconds = 5;
        public int cooldownSeconds = 300;
        public List<RtpProfile> profiles = new ArrayList<>(List.of(defaultProfile()));

        // The out-of-the-box overworld profile, which does hand over the guide on arrival.
        private static RtpProfile defaultProfile() {
            RtpProfile profile = new RtpProfile();
            profile.giveWelcomeBook = true;
            return profile;
        }
    }

    /** How /rtp behaves in one dimension. Distance is measured from that level's spawn. */
    public static final class RtpProfile {
        public String dimension = "minecraft:overworld";
        public boolean enabled = true;
        // Added on top of the spawn protection radius from server.properties.
        public int minDistance = 0;
        // 0 means the world border is the only limit. A number caps it tighter than the border.
        public int maxDistance = 3000;
        // 1 draws uniformly; higher values pull landings toward the minimum.
        public double spawnBias = 1.0;
        public boolean allowWater = false;
        public int maxAttempts = 24;
        public String message = "&aWhoosh! You landed &e{distance}&a blocks from spawn.";
        // 0 means the arrival reward is granted once and never again.
        public long rewardCooldownSeconds = 0;
        // Hands over the welcome book on arrival. The book lives once under welcomeBook, so this
        // is a switch rather than a copy of the content.
        //
        // Off here on purpose: a config written before this feature existed has no value for it,
        // so an upgraded server keeps handing out exactly what it did before. The shipped default
        // profile turns it on, so a fresh config does include the guide.
        public boolean giveWelcomeBook = false;
        public List<RtpEffect> effects = new ArrayList<>();
        public List<RtpItem> items = new ArrayList<>(List.of(new RtpItem("{\"id\":\"minecraft:red_bed\",\"count\":1}")));
    }

    public static final class RtpEffect {
        public String effect = "minecraft:resistance";
        public int seconds = 30;
        public int amplifier = 0;
        public boolean showParticles = true;
    }

    /**
     * One arrival item, held as ItemStack.CODEC JSON ({id, count, components}). Components are
     * what let a stack carry a written book or a coloured name. Typed as JsonElement so it nests
     * inline in quackedsmp.json rather than becoming an escaped blob. Same shape as KitItem.
     */
    public static final class RtpItem {
        public JsonElement stack;

        public RtpItem() {}

        public RtpItem(String stackJson) {
            this.stack = JsonParser.parseString(stackJson);
        }
    }
}
