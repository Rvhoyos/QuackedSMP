package mc.smpessentials.dims;

import net.minecraft.resources.Identifier;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists each player's overworld portal entry position so they return to the correct portal
 * after a custom dim trip, even across server restarts. Stored in the overworld's data storage
 * under {@code quackedsmp_ether_returns}. On outbound entry the position is written via
 * {@link #record}; on return it is read and removed via {@link #remove}. Falls back to overworld
 * spawn if no position is stored.
 */
public final class EtherReturnData extends SavedData {

    /** Codec over the internal {@code uuid-string → BlockPos} map. */
    public static final Codec<EtherReturnData> CODEC =
            Codec.unboundedMap(Codec.STRING, BlockPos.CODEC)
                 .xmap(EtherReturnData::new, d -> d.positions);

    /**
     * Registration descriptor; the storage key is {@code quackedsmp_ether_returns}.
     * Defaults to an empty map when no file exists.
     */
    public static final SavedDataType<EtherReturnData> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace("quackedsmp_ether_returns"),
            () -> new EtherReturnData(new HashMap<>()),
            CODEC,
            DataFixTypes.LEVEL);

    /** Mutable map of UUID string → last overworld entry position. */
    private final Map<String, BlockPos> positions;

    private EtherReturnData(Map<String, BlockPos> positions) {
        this.positions = new HashMap<>(positions);
    }

    /** Primary entry point — lazily creates the instance from the overworld data storage. */
    public static EtherReturnData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Stores {@code pos} as the overworld return destination for {@code uuid} and marks dirty.
     * Overwrites any previously stored position for the same player.
     */
    public void record(UUID uuid, BlockPos pos) {
        positions.put(uuid.toString(), pos);
        setDirty();
    }

    /**
     * Retrieves and removes the stored return position for {@code uuid}, or returns {@code null}
     * if none exists. Marks dirty only when an entry was actually removed.
     */
    public BlockPos remove(UUID uuid) {
        BlockPos stored = positions.remove(uuid.toString());
        if (stored != null) setDirty();
        return stored;
    }
}
