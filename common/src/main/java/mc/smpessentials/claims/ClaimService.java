package mc.smpessentials.claims;

import mc.smpessentials.claims.model.ClaimData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

import java.util.Optional;
import java.util.UUID;

public final class ClaimService {
    private ClaimService() {}

    /** Hard cap for MVP; tweak freely. */
    public static final int MAX_PER_PLAYER = 50;

    public static Optional<UUID> getOwner(ServerLevel level, ChunkPos pos) {
        return ClaimManager.get(level).get(pos).map(ClaimData::owner);
    }

    /** Count in the *current level* (simple MVP). */
    public static int ownedCount(ServerLevel level, UUID owner) {
        return ClaimManager.get(level).ownedCount(owner);
    }

    /** Owner OR OP can unclaim. */
    public static boolean unclaim(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        var mgr = ClaimManager.get(level);
        var existing = mgr.get(pos);
        if (existing.isEmpty()) return false;

        if (existing.get().owner().equals(player.getUUID())) {
            return mgr.unclaimIfOwned(pos, player.getUUID());
        }
        // OP force
        if (player.getServer().getPlayerList().isOp(player.getGameProfile())) {
            return mgr.forceUnclaim(pos);
        }
        return false;
    }

    /** Player claims for themselves OPs can still claim their own chunks. */
    public static Result claim(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        // Spawn protection guard (vanilla-like square)
        if (level.dimension() == Level.OVERWORLD) {
            BlockPos spawn = level.getSharedSpawnPos();
            int radius = player.getServer().getSpawnRadius(level); // vanilla API
            if (radius > 0) {
                int cx = pos.getMiddleBlockX();
                int cz = pos.getMiddleBlockZ();
                int dx = Math.abs(cx - spawn.getX());
                int dz = Math.abs(cz - spawn.getZ());
                //square, matches vanilla behavior
                if (Math.max(dx, dz) <= radius) return Result.SPAWN_PROTECTED;
            }
            // Also block exact spawn chunk for safety
            if (pos.equals(new ChunkPos(spawn))) return Result.SPAWN_PROTECTED;
        }

        var mgr = ClaimManager.get(level);

        if (mgr.isClaimed(pos)) return Result.ALREADY_CLAIMED;

        UUID me = player.getUUID();
        // OP bypass: allow server operators to ignore the per-player MAX_PER_PLAYER cap.
        boolean isOp = player.getServer().getPlayerList().isOp(player.getGameProfile());
        if (ownedCount(level, me) >= MAX_PER_PLAYER && !isOp) return Result.REACHED_CAP;

        mgr.claim(pos, me);
        return Result.SUCCESS;
    }


    public enum Result {
        SUCCESS,
        ALREADY_CLAIMED,
        REACHED_CAP,
        SPAWN_PROTECTED
    }
}
