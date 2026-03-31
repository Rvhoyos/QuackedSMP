package mc.smpessentials.claims;

import mc.smpessentials.claims.model.ClaimData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.core.BlockPos;

import java.util.Optional;
import java.util.UUID;

// Business logic for claim operations. Commands call this; storage is handled by ClaimManager.
public final class ClaimService {
    private ClaimService() {
    }

    public static Optional<UUID> getOwner(ServerLevel level, ChunkPos pos) {
        return ClaimManager.get(level).get(pos).map(ClaimData::owner);
    }

    public static int ownedCount(ServerLevel level, UUID owner) {
        return ClaimManager.get(level).ownedCount(owner);
    }

    // Owner or OP can unclaim. Returns false if not claimed or caller has no permission.
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

    public static boolean setName(ServerPlayer player, ServerLevel level, ChunkPos pos, String name) {
        var mgr = ClaimManager.get(level);
        return mgr.setNameIfOwned(pos, player.getUUID(), name);
    }

    public static Result claim(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        UUID me = player.getUUID();
        // OP bypass: allow server operators to ignore the per-player MAX_PER_PLAYER cap
        // AND bypass spawn protection.
        boolean isOp = ((ServerLevel) player.level()).getServer().getPlayerList().isOp(player.nameAndId());

        // Spawn protection guard (vanilla-like square)
        if (!isOp && level.dimension() == level.getRespawnData().dimension()) {
            net.minecraft.world.level.storage.LevelData.RespawnData respawnData = level.getRespawnData();
            BlockPos spawn = respawnData.pos();
            int radius = (((ServerLevel) player.level()).getServer() instanceof DedicatedServer ds)
                    ? ds.spawnProtectionRadius()
                    : 0;

            if (radius > 0) {
                // Inclusive chunk check: does any part of the 16x16 chunk overlap the
                // [spawn-radius, spawn+radius] square?
                int chunkMinX = pos.getMinBlockX();
                int chunkMaxX = pos.getMaxBlockX();
                int chunkMinZ = pos.getMinBlockZ();
                int chunkMaxZ = pos.getMaxBlockZ();

                int protMinX = spawn.getX() - radius;
                int protMaxX = spawn.getX() + radius;
                int protMinZ = spawn.getZ() - radius;
                int protMaxZ = spawn.getZ() + radius;

                boolean overlapX = Math.max(chunkMinX, protMinX) <= Math.min(chunkMaxX, protMaxX);
                boolean overlapZ = Math.max(chunkMinZ, protMinZ) <= Math.min(chunkMaxZ, protMaxZ);

                if (overlapX && overlapZ) {
                    return Result.SPAWN_PROTECTED;
                }
            }
            // Also block exact spawn chunk for safety
            if (pos.equals(new ChunkPos(spawn)))
                return Result.SPAWN_PROTECTED;
        }

        var mgr = ClaimManager.get(level);

        if (mgr.isClaimed(pos))
            return Result.ALREADY_CLAIMED;

        int limit = mc.smpessentials.config.SmpConfig.MAX_CLAIMS;
        limit += mc.smpessentials.tier.TierService.getBonusClaims(
                mc.smpessentials.tier.TierService.getTier(player.getUUID(),
                        ((net.minecraft.server.level.ServerLevel) player.level()).getServer()));
        if (!isOp && ownedCount(level, me) >= limit)
            return Result.REACHED_CAP;

        mgr.claim(pos, me);
        return Result.SUCCESS;
    }

    // Returns claims transferred, or negative: -1 same player, -2 no claims, -3 would exceed target limit.
    public static int transfer(ServerPlayer sender, ServerPlayer target) {
        if (sender.getUUID().equals(target.getUUID()))
            return -1;

        var data = mc.smpessentials.claims.storage.ClaimedSavedData.get((ServerLevel) sender.level());
        int senderCount = data.countByOwner(sender.getUUID());
        if (senderCount == 0)
            return -2;


        boolean isTargetOp = ((ServerLevel) sender.level()).getServer().getPlayerList().isOp(target.nameAndId());
        if (!isTargetOp) {
            int targetCount = data.countByOwner(target.getUUID());
            int targetLimit = mc.smpessentials.config.SmpConfig.MAX_CLAIMS
                    + mc.smpessentials.tier.TierService.getBonusClaims(
                            mc.smpessentials.tier.TierService.getTier(target.getUUID(),
                                    ((net.minecraft.server.level.ServerLevel) target.level()).getServer()));
            if (targetCount + senderCount > targetLimit)
                return -3;
        }

        data.transferClaims(sender.getUUID(), target.getUUID());
        return senderCount;
    }

    public enum Result {
        SUCCESS,
        ALREADY_CLAIMED,
        REACHED_CAP,
        SPAWN_PROTECTED
    }
}
