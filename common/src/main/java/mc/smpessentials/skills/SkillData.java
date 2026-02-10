package mc.smpessentials.skills;

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

    /** Per-player record: XP for each skill + cooldown timestamps. */
    public record PlayerProfile(UUID uuid, Map<String, Double> xp, Map<String, Long> cooldowns) {

        public static final Codec<PlayerProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("uuid").forGetter(PlayerProfile::uuid),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("xp").forGetter(PlayerProfile::xp),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("cooldowns").forGetter(PlayerProfile::cooldowns))
                .apply(i, PlayerProfile::new));
    }

    private final Map<UUID, PlayerProfile> profiles;

    public static final Codec<SkillData> CODEC = RecordCodecBuilder.create(i -> i.group(
            PlayerProfile.CODEC.listOf().fieldOf("profiles").forGetter(s -> List.copyOf(s.profiles.values())))
            .apply(i, SkillData::fromList));

    public static final SavedDataType<SkillData> TYPE = new SavedDataType<>(
            "quackedsmp_skills",
            ctx -> new SkillData(Map.of()),
            ctx -> SkillData.CODEC,
            DataFixTypes.LEVEL);

    private SkillData(Map<UUID, PlayerProfile> profiles) {
        this.profiles = new HashMap<>(profiles);
    }

    private static SkillData fromList(List<PlayerProfile> list) {
        Map<UUID, PlayerProfile> map = new HashMap<>();
        for (PlayerProfile p : list) {
            map.put(p.uuid(), p);
        }
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
                id -> new PlayerProfile(id, new HashMap<>(), new HashMap<>()));
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
     * Check if an ability is on cooldown. Returns remaining seconds, or 0 if ready.
     */
    public long getCooldownRemaining(UUID uuid, SkillType skill) {
        PlayerProfile p = getOrCreate(uuid);
        Long lastUsed = p.cooldowns().get(skill.name());
        if (lastUsed == null)
            return 0;

        long cooldownSeconds = mc.smpessentials.config.SmpConfig.getSkillCooldown(skill);
        long elapsed = (System.currentTimeMillis() - lastUsed) / 1000;
        long remaining = cooldownSeconds - elapsed;
        return Math.max(0, remaining);
    }

    /** Start cooldown for an ability. */
    public void setCooldown(UUID uuid, SkillType skill) {
        getOrCreate(uuid).cooldowns().put(skill.name(), System.currentTimeMillis());
        setDirty();
    }

    /** Check if ability is ready (not on cooldown). */
    public boolean isAbilityReady(UUID uuid, SkillType skill) {
        return getCooldownRemaining(uuid, skill) == 0;
    }
}
