package mc.smpessentials.claims;

import java.util.Optional;
import java.util.UUID;

import mc.smpessentials.claims.model.ClaimData;
import mc.smpessentials.claims.storage.ClaimedSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Thin wrapper around {@link ClaimedSavedData} that scopes all operations to a single
 * {@link ServerLevel}. All data is stored in the overworld's data storage, so claim counts
 * are accurate across dimensions even though each instance filters by level.
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

    public RegionNames.RenameResult nameRegion(ChunkPos standChunk, UUID owner, String name,
            mc.smpessentials.claims.model.WarpAnchor anchor) {
        return RegionNames.nameRegion(data, level, standChunk, owner, name, anchor);
    }

    /** After a chunk is claimed, resolve any two-named-regions-merged conflict for this owner. */
    public void resolveMerge(ChunkPos justClaimed, UUID owner) {
        RegionNames.resolveMerge(data, level, justClaimed, owner);
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
