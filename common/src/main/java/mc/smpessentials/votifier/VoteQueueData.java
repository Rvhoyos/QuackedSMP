package mc.smpessentials.votifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;

// Persists offline vote rewards so they survive server restarts.
public final class VoteQueueData extends SavedData {

    private final Map<String, Integer> pending;

    public static final Codec<VoteQueueData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .fieldOf("pending")
                    .forGetter(s -> Map.copyOf(s.pending))
    ).apply(i, VoteQueueData::new));

    public static final SavedDataType<VoteQueueData> TYPE = new SavedDataType<>(
            "quackedsmp_vote_queue",
            () -> new VoteQueueData(new HashMap<>()),
            CODEC,
            DataFixTypes.LEVEL
    );

    private VoteQueueData(Map<String, Integer> pending) {
        this.pending = new HashMap<>(pending);
    }

    public static VoteQueueData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public void queue(String username) {
        pending.merge(username, 1, Integer::sum);
        setDirty();
    }

    // Returns and removes all pending vote counts for the player (0 if none).
    public int take(String username) {
        Integer count = pending.remove(username);
        if (count != null && count > 0) {
            setDirty();
            return count;
        }
        return 0;
    }
}
