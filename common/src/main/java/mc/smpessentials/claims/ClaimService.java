package mc.smpessentials.claims;

import mc.smpessentials.claims.model.ClaimData;
import mc.smpessentials.claims.model.WarpAnchor;
import mc.smpessentials.claims.storage.ClaimedSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.core.BlockPos;

import java.util.List;
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

    // Names the region the player is standing in and captures their exact spot as the /visit anchor.
    public static NameResult nameClaim(ServerPlayer player, ServerLevel level, String name) {
        if (name == null || name.isBlank())
            return NameResult.EMPTY;
        WarpAnchor anchor = new WarpAnchor(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
        return switch (ClaimManager.get(level).nameRegion(player.chunkPosition(), player.getUUID(), name, anchor)) {
            case OK -> NameResult.OK;
            case NAME_TAKEN -> NameResult.NAME_TAKEN;
            case NOT_OWNER -> NameResult.NOT_OWNER;
        };
    }

    // Named regions the player may /visit (owner, trusted, or OP), sorted case-insensitively.
    public static List<String> visitableNames(ServerPlayer player) {
        return RegionNames.visitable(ClaimedSavedData.get((ServerLevel) player.level()), player);
    }

    public static RegionNames.VisitResult resolveVisit(ServerPlayer player, String name) {
        return RegionNames.resolveVisit(ClaimedSavedData.get((ServerLevel) player.level()), player, name);
    }

    public enum NameResult {
        OK, NOT_OWNER, NAME_TAKEN, EMPTY
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
            if (pos.equals(ChunkPos.containing(spawn)))
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
        mgr.resolveMerge(pos, me);
        return Result.SUCCESS;
    }

    public enum Result {
        SUCCESS,
        ALREADY_CLAIMED,
        REACHED_CAP,
        SPAWN_PROTECTED
    }
}
