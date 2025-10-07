package mc.smpessentials.claims;

import java.util.Optional;
import java.util.UUID;

import mc.smpessentials.claims.model.ClaimData;
import mc.smpessentials.claims.storage.ClaimedSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class ClaimManager {
    private final ClaimedSavedData data;
    private final ServerLevel level;

    private ClaimManager(ClaimedSavedData data, ServerLevel level) {
        this.data = data;
        this.level = level;
    }

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

    /** Owner-guarded unclaim: only removes if the same owner. */
    public boolean unclaimIfOwned(ChunkPos pos, UUID owner) {
        Optional<ClaimData> cd = data.getClaim(level, pos);
        if (cd.isEmpty() || !cd.get().owner().equals(owner)) return false;
        return data.unclaim(level, pos);
    }

    /** Admin/unconditional unclaim. */
    public boolean forceUnclaim(ChunkPos pos) {
        return data.unclaim(level, pos);
    }

    /** Per-level count for this owner. */
    public int ownedCount(UUID owner) {
        return data.countByOwner(level, owner);
    }
}
