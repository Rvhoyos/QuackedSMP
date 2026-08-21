package mc.smpessentials.rtp;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers when each player last received the arrival reward, per dimension, so a profile can
 * grant its items once and never again or once per reward cooldown. Stored in the overworld data
 * directory alongside the other SavedData in this mod.
 */
public final class RtpData extends SavedData {

    private record RewardKey(UUID uuid, String dimension) {}

    private record RewardEntry(UUID uuid, String dimension, long timestamp) {}

    private final Map<RewardKey, Long> lastReward = new HashMap<>();

    public RtpData() {}

    private static final Codec<RewardEntry> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(RewardEntry::uuid),
            Codec.STRING.fieldOf("dimension").forGetter(RewardEntry::dimension),
            Codec.LONG.fieldOf("timestamp").forGetter(RewardEntry::timestamp))
            .apply(i, RewardEntry::new));

    private List<RewardEntry> toEntries() {
        List<RewardEntry> list = new ArrayList<>(this.lastReward.size());
        for (Map.Entry<RewardKey, Long> e : this.lastReward.entrySet()) {
            list.add(new RewardEntry(e.getKey().uuid(), e.getKey().dimension(), e.getValue()));
        }
        return list;
    }

    private static RtpData fromEntries(List<RewardEntry> entries) {
        RtpData data = new RtpData();
        for (RewardEntry e : entries) {
            data.lastReward.put(new RewardKey(e.uuid(), e.dimension()), e.timestamp());
        }
        return data;
    }

    public static final Codec<RtpData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ENTRY_CODEC.listOf().optionalFieldOf("rtp_rewards", List.of())
                    .forGetter(RtpData::toEntries))
            .apply(i, RtpData::fromEntries));

    public static final SavedDataType<RtpData> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace("quackedsmp_rtp"),
            RtpData::new,
            CODEC,
            DataFixTypes.LEVEL);

    public static RtpData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Whether this player is owed the arrival reward for this dimension. A cooldown of zero or
     * less means the reward is granted exactly once and never repeats.
     */
    public boolean isRewardDue(UUID uuid, String dimension, long cooldownSeconds, long nowMs) {
        Long last = this.lastReward.get(new RewardKey(uuid, dimension));
        if (last == null) return true;
        if (cooldownSeconds <= 0) return false;
        return nowMs - last >= cooldownSeconds * 1000L;
    }

    public void markRewarded(UUID uuid, String dimension, long nowMs) {
        this.lastReward.put(new RewardKey(uuid, dimension), nowMs);
        setDirty();
    }
}
