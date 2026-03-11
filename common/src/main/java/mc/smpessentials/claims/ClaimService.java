package mc.smpessentials.claims;

import mc.smpessentials.claims.model.ClaimData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

import java.util.Optional;
import java.util.UUID;

public final class ClaimService {
    private ClaimService() {
    }

    /** Limit configured in SmpConfig. */
    // public static final int MAX_PER_PLAYER = 50; // moved to config

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
        if (existing.isEmpty())
            return false;

        if (existing.get().owner().equals(player.getUUID())) {
            return mgr.unclaimIfOwned(pos, player.getUUID());
        }
        // OP force
        if (((ServerLevel) player.level()).getServer().getPlayerList().isOp(player.nameAndId())) {
            return mgr.forceUnclaim(pos);
        }
        return false;
    }

    /** Owner can set name. */
    public static boolean setName(ServerPlayer player, ServerLevel level, ChunkPos pos, String name) {
        var mgr = ClaimManager.get(level);
        return mgr.setNameIfOwned(pos, player.getUUID(), name);
    }

    /** Player claims for themselves OPs can still claim their own chunks. */
    public static Result claim(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        // Spawn protection guard (vanilla-like square)
        if (level.dimension() == Level.OVERWORLD) {
            BlockPos spawn = level.getRespawnData().pos();
            int radius = (((ServerLevel) player.level()).getServer() instanceof DedicatedServer ds)
                    ? ds.spawnProtectionRadius()
                    : 0; // vanilla API
            if (radius > 0) {
                int cx = pos.getMiddleBlockX();
                int cz = pos.getMiddleBlockZ();
                int dx = Math.abs(cx - spawn.getX());
                int dz = Math.abs(cz - spawn.getZ());
                // square, matches vanilla behavior
                if (Math.max(dx, dz) <= radius)
                    return Result.SPAWN_PROTECTED;
            }
            // Also block exact spawn chunk for safety
            if (pos.equals(new ChunkPos(spawn)))
                return Result.SPAWN_PROTECTED;
        }

        var mgr = ClaimManager.get(level);

        if (mgr.isClaimed(pos))
            return Result.ALREADY_CLAIMED;

        UUID me = player.getUUID();
        // OP bypass: allow server operators to ignore the per-player MAX_PER_PLAYER
        // cap.
        boolean isOp = ((ServerLevel) player.level()).getServer().getPlayerList().isOp(player.nameAndId());
        int limit = mc.smpessentials.config.SmpConfig.MAX_CLAIMS;
        if (mc.smpessentials.config.SmpConfig.VIPS.contains(player.getName().getString())) {
            limit += mc.smpessentials.config.SmpConfig.VIP_BONUS_CLAIMS;
        }

        if (!isOp && ownedCount(level, me) >= limit)
            return Result.REACHED_CAP;

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
