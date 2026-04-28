package mc.smpessentials.skills;

import net.minecraft.resources.Identifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/**
 * Persists per-player skill XP and ability cooldowns.
 * Stored in the overworld data folder as "quackedsmp_skills".
 */
public final class SkillData extends SavedData {

    /** Per-player record: XP for each skill + cooldown timestamps + display name cache. */
    public static final class PlayerProfile {
        private final UUID uuid;
        private String name;
        private final Map<String, Double> xp;
        private final Map<String, Long> cooldowns;

        public PlayerProfile(UUID uuid, String name, Map<String, Double> xp, Map<String, Long> cooldowns) {
            this.uuid = uuid;
            this.name = name;
            this.xp = new HashMap<>(xp);
            this.cooldowns = new HashMap<>(cooldowns);
        }

        public UUID uuid() { return uuid; }
        public String name() { return name; }
        public void setName(String name) { this.name = name; }
        public Map<String, Double> xp() { return xp; }
        public Map<String, Long> cooldowns() { return cooldowns; }

        public static final Codec<PlayerProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("uuid").forGetter(PlayerProfile::uuid),
                Codec.STRING.optionalFieldOf("name", "").forGetter(PlayerProfile::name),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("xp").forGetter(PlayerProfile::xp),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("cooldowns").forGetter(PlayerProfile::cooldowns))
                .apply(i, PlayerProfile::new));
    }

    private final Map<UUID, PlayerProfile> profiles;

    public static final Codec<SkillData> CODEC = RecordCodecBuilder.create(i -> i.group(
            PlayerProfile.CODEC.listOf().fieldOf("profiles").forGetter(s -> List.copyOf(s.profiles.values())))
            .apply(i, SkillData::fromList));

    public static final SavedDataType<SkillData> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace("quackedsmp_skills"),
            () -> new SkillData(new HashMap<>()),
            SkillData.CODEC,
            DataFixTypes.LEVEL);

    private SkillData(Map<UUID, PlayerProfile> profiles) {
        this.profiles = new HashMap<>(profiles);
    }

    private static SkillData fromList(List<PlayerProfile> list) {
        Map<UUID, PlayerProfile> map = new HashMap<>();
        for (PlayerProfile p : list) map.put(p.uuid(), p);
        return new SkillData(map);
    }

    /** Get from the overworld's data storage. */
    public static SkillData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    // ---- XP Operations ----

    private PlayerProfile getOrCreate(UUID uuid) {
        return profiles.computeIfAbsent(uuid,
                id -> new PlayerProfile(id, "", new HashMap<>(), new HashMap<>()));
    }

    /** Update cached display name for a player (call on join). */
    public void updateName(UUID uuid, String name) {
        PlayerProfile p = getOrCreate(uuid);
        if (!name.equals(p.name())) {
            p.setName(name);
            setDirty();
        }
    }

    /** Resolve a display name: use cached name if available, else UUID prefix. */
    public String getDisplayName(UUID uuid) {
        PlayerProfile p = profiles.get(uuid);
        if (p != null && !p.name().isEmpty()) return p.name();
        return uuid.toString().substring(0, 8) + "...";
    }

    /** Get all XP for a player. */
    public Map<String, Double> getXpMap(UUID uuid) {
        return getOrCreate(uuid).xp();
    }

    /** Get typed XP map using SkillType keys. */
    public Map<SkillType, Double> getTypedXpMap(UUID uuid) {
        Map<String, Double> raw = getXpMap(uuid);
        Map<SkillType, Double> result = new EnumMap<>(SkillType.class);
        for (SkillType st : SkillType.values()) {
            result.put(st, raw.getOrDefault(st.name(), 0.0));
        }
        return result;
    }

    /** Get XP for a specific skill. */
    public double getXp(UUID uuid, SkillType skill) {
        return getOrCreate(uuid).xp().getOrDefault(skill.name(), 0.0);
    }

    /** Add XP to a skill. Returns the new total. */
    public double addXp(UUID uuid, SkillType skill, double amount) {
        PlayerProfile p = getOrCreate(uuid);
        double current = p.xp().getOrDefault(skill.name(), 0.0);
        int currentLevel = SkillManager.levelFromXp(current);
        if (currentLevel >= SkillManager.MAX_LEVEL)
            return current; // capped

        double newTotal = current + amount;
        p.xp().put(skill.name(), newTotal);
        setDirty();
        return newTotal;
    }

    /** Set XP for a skill directly. Returns the new total. */
    public double setXp(UUID uuid, SkillType skill, double amount) {
        PlayerProfile p = getOrCreate(uuid);
        p.xp().put(skill.name(), amount);
        setDirty();
        return amount;
    }

    /** Get level for a specific skill. */
    public int getLevel(UUID uuid, SkillType skill) {
        return SkillManager.levelFromXp(getXp(uuid, skill));
    }

    // ---- Cooldown Operations ----

    /**
     * Core cooldown check by raw string key (e.g. "archery", "archery_zoom").
     * baseCd is in seconds. level is the skill level used for scaling.
     */
    public long getCooldownRemaining(UUID uuid, String abilityKey, long baseCd, int level) {
        PlayerProfile p = getOrCreate(uuid);
        Long lastUsed = p.cooldowns().get(abilityKey);
        if (lastUsed == null)
            return 0;

        // Piecewise scaling:
        // Lv 0-10: Drops from 100% to 75% (2.5% per level)
        // Lv 10-100: Drops from 75% to 25% (~0.556% per level)
        double multiplier;
        if (level < 10) {
            multiplier = 1.0 - (0.025 * level);
        } else {
            multiplier = 0.75 - ((level - 10) * 0.00555556);
        }
        multiplier = Math.max(0.25, multiplier);

        long effectiveCd = (long) (baseCd * multiplier);
        long elapsed = (System.currentTimeMillis() - lastUsed) / 1000;
        return Math.max(0, effectiveCd - elapsed);
    }

    /** Check if an ability is on cooldown. Returns remaining seconds, or 0 if ready. */
    public long getCooldownRemaining(UUID uuid, SkillType skill) {
        return getCooldownRemaining(uuid, skill.name(),
                mc.smpessentials.config.SmpConfig.getSkillCooldown(skill),
                getLevel(uuid, skill));
    }

    /** Check cooldown for a named sub-ability (e.g. "archery_zoom") on a given skill. */
    public long getCooldownRemaining(UUID uuid, String abilityKey, SkillType skill) {
        return getCooldownRemaining(uuid, abilityKey,
                mc.smpessentials.config.SmpConfig.getAbilityCooldown(abilityKey),
                getLevel(uuid, skill));
    }

    /** Start cooldown for an ability by SkillType. */
    public void setCooldown(UUID uuid, SkillType skill) {
        getOrCreate(uuid).cooldowns().put(skill.name(), System.currentTimeMillis());
        setDirty();
    }

    /** Start cooldown for a named sub-ability (e.g. "archery_zoom"). */
    public void setCooldown(UUID uuid, String abilityKey) {
        getOrCreate(uuid).cooldowns().put(abilityKey, System.currentTimeMillis());
        setDirty();
    }

    /** Check if ability is ready (not on cooldown). */
    public boolean isAbilityReady(UUID uuid, SkillType skill) {
        return getCooldownRemaining(uuid, skill) == 0;
    }

    /** Check if a named sub-ability is ready. */
    public boolean isAbilityReady(UUID uuid, String abilityKey, SkillType skill) {
        return getCooldownRemaining(uuid, abilityKey, skill) == 0;
    }

    // ---- Leaderboard ----

    public record LeaderboardEntry(UUID uuid, int level) {}

    /** Overall leaderboard sorted by sum of all skill levels. */
    public List<LeaderboardEntry> getLeaderboard(int limit, int offset) {
        return profiles.values().stream()
                .map(p -> new LeaderboardEntry(p.uuid(), totalLevel(p)))
                .sorted((a, b) -> Integer.compare(b.level(), a.level()))
                .skip(offset)
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }

    /** Per-skill leaderboard sorted by level in the given skill. */
    public List<LeaderboardEntry> getSkillLeaderboard(SkillType skill, int limit, int offset) {
        return profiles.values().stream()
                .map(p -> new LeaderboardEntry(p.uuid(),
                        SkillManager.levelFromXp(p.xp().getOrDefault(skill.name(), 0.0))))
                .sorted((a, b) -> Integer.compare(b.level(), a.level()))
                .skip(offset)
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }

    /** Rank of a player in the overall leaderboard (1-based). */
    public int getOverallRank(UUID uuid) {
        int target = totalLevel(profiles.getOrDefault(uuid,
                new PlayerProfile(uuid, "", new HashMap<>(), new HashMap<>())));
        long rank = profiles.values().stream()
                .filter(p -> totalLevel(p) > target)
                .count();
        return (int) rank + 1;
    }

    /** Returns the total number of player profiles stored (i.e. players who have ever logged in). */
    public int totalProfiles() {
        return profiles.size();
    }

    private int totalLevel(PlayerProfile p) {
        int sum = 0;
        for (SkillType s : SkillType.values())
            sum += SkillManager.levelFromXp(p.xp().getOrDefault(s.name(), 0.0));
        return sum;
    }
}
