package mc.smpessentials.claims;

import java.util.Optional;
import java.util.UUID;

import mc.smpessentials.claims.model.ClaimData;
import mc.smpessentials.claims.storage.ClaimedSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Thin wrapper around {@link ClaimedSavedData} that scopes all operations to a single
 * {@link ServerLevel}.
 *
 * <p>Callers obtain an instance via {@link #get(ServerLevel)}.  Internally, all data is
 * stored in the overworld's {@code DataStorage} (see {@link ClaimedSavedData#get}), so
 * claim counts are accurate across dimensions even though each {@code ClaimManager}
 * instance filters by level.
 */
public final class ClaimManager {
    private final ClaimedSavedData data;
    private final ServerLevel level;

    private ClaimManager(ClaimedSavedData data, ServerLevel level) {
        this.data = data;
        this.level = level;
    }

    /**
     * Creates a {@code ClaimManager} scoped to {@code level}.
     * The underlying {@link ClaimedSavedData} is always the global overworld store.
     */
    public static ClaimManager get(ServerLevel level) {
        return new ClaimManager(ClaimedSavedData.get(level), level);
    }

    public boolean isClaimed(ChunkPos pos) {
        return data.isClaimed(level, pos);
    }

    public Optional<ClaimData> get(ChunkPos pos) {
        return data.getClaim(level, pos);
    }

    public void claim(ChunkPos pos, UUID owner) {
        data.claim(level, pos, owner);
    }

    public boolean setNameIfOwned(ChunkPos pos, UUID owner, String name) {
        Optional<ClaimData> cd = data.getClaim(level, pos);
        if (cd.isEmpty() || !cd.get().owner().equals(owner))
            return false;
        return data.updateName(level, pos, name);
    }

    /** Owner-guarded unclaim: only removes if the same owner. */
    public boolean unclaimIfOwned(ChunkPos pos, UUID owner) {
        Optional<ClaimData> cd = data.getClaim(level, pos);
        if (cd.isEmpty() || !cd.get().owner().equals(owner))
            return false;
        return data.unclaim(level, pos);
    }

    /** Admin/unconditional unclaim. */
    public boolean forceUnclaim(ChunkPos pos) {
        return data.unclaim(level, pos);
    }

    /** Global claim count for this owner (across all dimensions). */
    public int ownedCount(UUID owner) {
        return data.countByOwner(owner);
    }
}
