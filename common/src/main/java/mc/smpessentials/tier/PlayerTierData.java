package mc.smpessentials.tier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

// Stores the effective tier per player, keyed by UUID.
// A player's tier is the single source of truth — earned tiers bump it up on login;
// admin assignments set it directly. Both write the same field.
// Also holds a pending migration map (name-keyed) for entries imported from the old JSON config.
public final class PlayerTierData extends SavedData {

    private final Map<UUID, Integer> tiers = new HashMap<>();
    // Lowercase name -> tier; cleared as each player logs in. Persisted across restarts.
    private final Map<String, Integer> pending = new HashMap<>();

    public PlayerTierData() {}

    // ── Codec helpers ──────────────────────────────────────────────────────────

    private record TierEntry(UUID uuid, int tier) {}
    private record PendingEntry(String name, int tier) {}

    private static final Codec<TierEntry> TIER_ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(TierEntry::uuid),
            Codec.INT.fieldOf("tier").forGetter(TierEntry::tier))
            .apply(i, TierEntry::new));

    private static final Codec<PendingEntry> PENDING_ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(PendingEntry::name),
            Codec.INT.fieldOf("tier").forGetter(PendingEntry::tier))
            .apply(i, PendingEntry::new));

    private List<TierEntry> toTierEntries() {
        List<TierEntry> list = new ArrayList<>(tiers.size());
        for (Map.Entry<UUID, Integer> e : tiers.entrySet()) {
            list.add(new TierEntry(e.getKey(), e.getValue()));
        }
        return list;
    }

    private List<PendingEntry> toPendingEntries() {
        List<PendingEntry> list = new ArrayList<>(pending.size());
        for (Map.Entry<String, Integer> e : pending.entrySet()) {
            list.add(new PendingEntry(e.getKey(), e.getValue()));
        }
        return list;
    }

    private static PlayerTierData fromLists(List<TierEntry> tierList, List<PendingEntry> pendingList) {
        PlayerTierData d = new PlayerTierData();
        for (TierEntry e : tierList) {
            if (e.tier() > 0) d.tiers.put(e.uuid(), e.tier());
        }
        for (PendingEntry e : pendingList) {
            if (e.tier() > 0) d.pending.put(e.name().toLowerCase(Locale.ROOT), e.tier());
        }
        return d;
    }

    public static final Codec<PlayerTierData> CODEC = RecordCodecBuilder.create(i -> i.group(
            TIER_ENTRY_CODEC.listOf().optionalFieldOf("tiers", List.of())
                    .forGetter(PlayerTierData::toTierEntries),
            PENDING_ENTRY_CODEC.listOf().optionalFieldOf("pending_migration", List.of())
                    .forGetter(PlayerTierData::toPendingEntries))
            .apply(i, PlayerTierData::fromLists));

    public static final SavedDataType<PlayerTierData> TYPE = new SavedDataType<>(
            "quackedsmp_player_tiers",
            PlayerTierData::new,
            CODEC,
            DataFixTypes.LEVEL);

    public static PlayerTierData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // ── Data access ────────────────────────────────────────────────────────────

    public int getTier(UUID uuid) {
        return tiers.getOrDefault(uuid, 0);
    }

    public void set(UUID uuid, int tier) {
        if (tier <= 0) {
            tiers.remove(uuid);
        } else {
            tiers.put(uuid, tier);
        }
        setDirty();
    }

    public void remove(UUID uuid) {
        if (tiers.remove(uuid) != null) {
            setDirty();
        }
    }

    public Map<UUID, Integer> getAll() {
        return Collections.unmodifiableMap(tiers);
    }

    // ── Migration ──────────────────────────────────────────────────────────────

    // Called at server start. Merges name-keyed entries from the old JSON config into the pending map.
    public void loadPendingMigration(Map<String, Integer> fromConfig) {
        if (fromConfig.isEmpty()) return;
        boolean changed = false;
        for (Map.Entry<String, Integer> e : fromConfig.entrySet()) {
            if (e.getValue() > 0) {
                String key = e.getKey().toLowerCase(Locale.ROOT);
                if (pending.putIfAbsent(key, e.getValue()) == null) changed = true;
            }
        }
        if (changed) setDirty();
    }

    // Called on player login. Promotes any pending entry for this player to UUID-keyed storage.
    // Does NOT bump earned tier — that is handled by TierService.onPlayerJoin.
    public void promotePendingMigration(ServerPlayer player) {
        String key = player.getName().getString().toLowerCase(Locale.ROOT);
        Integer tier = pending.remove(key);
        if (tier != null) {
            if (!tiers.containsKey(player.getUUID())) {
                tiers.put(player.getUUID(), tier);
            }
            setDirty();
        }
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }
}
