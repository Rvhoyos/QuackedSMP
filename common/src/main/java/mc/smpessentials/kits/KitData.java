package mc.smpessentials.kits;

import net.minecraft.resources.Identifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

// Persists per-player kit redemption timestamps. Stored in the overworld data directory.
public final class KitData extends SavedData {

    private final Map<UUID, Long> lastKitRedeem = new HashMap<>();

    public KitData() {}

    private record RedeemEntry(UUID uuid, long timestamp) {}

    private static final Codec<RedeemEntry> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(RedeemEntry::uuid),
            Codec.LONG.fieldOf("timestamp").forGetter(RedeemEntry::timestamp))
            .apply(i, RedeemEntry::new));

    private List<RedeemEntry> toEntries() {
        List<RedeemEntry> list = new ArrayList<>(lastKitRedeem.size());
        for (Map.Entry<UUID, Long> e : lastKitRedeem.entrySet()) {
            list.add(new RedeemEntry(e.getKey(), e.getValue()));
        }
        return list;
    }

    private static KitData fromEntries(List<RedeemEntry> entries) {
        KitData d = new KitData();
        for (RedeemEntry e : entries) {
            d.lastKitRedeem.put(e.uuid(), e.timestamp());
        }
        return d;
    }

    public static final Codec<KitData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ENTRY_CODEC.listOf().optionalFieldOf("kit_redeems", List.of())
                    .forGetter(KitData::toEntries))
            .apply(i, KitData::fromEntries));

    public static final SavedDataType<KitData> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace("quackedsmp_kits"),
            KitData::new,
            CODEC,
            DataFixTypes.LEVEL);

    public static KitData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public long getLastRedeem(UUID uuid) {
        return lastKitRedeem.getOrDefault(uuid, 0L);
    }

    public void setRedeemed(UUID uuid, long epochMillis) {
        lastKitRedeem.put(uuid, epochMillis);
        setDirty();
    }
}
