package mc.smpessentials.claims;

import mc.smpessentials.claims.storage.ClaimedSavedData;
import mc.smpessentials.claims.storage.WhitelistSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

/** Centralizes "can this player act here?" */
public final class ClaimAccess {
    private ClaimAccess() {}

    public static boolean canModify(ServerPlayer actor, ServerLevel level, ChunkPos chunk) {
        // OPs bypass
        if (actor.getServer().getPlayerList().isOp(actor.getGameProfile())) return true;

        var claims = ClaimedSavedData.get(level);
        var cd = claims.getClaim(level, chunk);
        if (cd.isEmpty()) return true; // unclaimed: allowed

        UUID owner = cd.get().owner();
        return WhitelistSavedData.get(level).isTrusted(owner, actor.getUUID());
    }
}
