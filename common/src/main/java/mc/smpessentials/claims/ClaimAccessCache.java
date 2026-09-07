package mc.smpessentials.claims;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;

import mc.smpessentials.config.SmpConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Answers "may this player break here?" for one bulk operation, keeping the claim answers per chunk.
 *
 * Skill abilities that break blocks the player never hit (Tree Feller, Leaf Blower, Philosopher's
 * Touch) go straight to {@code Level.destroyBlock}, which posts no break event on either loader, so
 * neither the claim hook nor vanilla's own spawn protection ever sees them. Both have to be asked
 * here or not at all.
 *
 * Spawn protection is delegated to vanilla rather than to {@link SpawnProtection} because
 * MinecraftServer.isUnderSpawnProtection carries the OP and empty-ops-list bypasses, so an ability
 * allows exactly what a hand break in the same spot would. It is also the term that cannot be cached
 * per chunk, since a chunk can straddle the edge of the spawn square.
 *
 * Deliberately not ClaimProtection.onBlockBreak, which chats the player on every denial and would
 * print one line per block.
 */
public final class ClaimAccessCache {
    private final ServerLevel level;
    private final ServerPlayer player;
    private final Long2BooleanOpenHashMap claimByChunk = new Long2BooleanOpenHashMap();
    private final boolean spawnProtectionPossible;

    public ClaimAccessCache(ServerLevel level, ServerPlayer player) {
        this.level = level;
        this.player = player;
        this.spawnProtectionPossible = spawnProtectionCanApply(level, player);
    }

    public boolean canModify(BlockPos pos) {
        if (!claimAllows(pos))
            return false;
        if (!this.spawnProtectionPossible)
            return true;
        return !this.level.getServer().isUnderSpawnProtection(this.level, pos, this.player);
    }

    /**
     * Every condition vanilla's isUnderSpawnProtection tests that does not depend on the position,
     * evaluated once per operation. Without this the per-block call allocates a String on every
     * block, because PlayerList.isOp keys StoredUserList by NameAndId.toString(), and Leaf Blower
     * asks about every leaf of every tree a player chops.
     *
     * The position-dependent square is still left to vanilla rather than reimplemented here, since
     * that math is already duplicated in four places (see the spawn protection TODO).
     */
    private static boolean spawnProtectionCanApply(ServerLevel level, ServerPlayer player) {
        if (SpawnProtection.radius(level) <= 0)
            return false;
        var players = level.getServer().getPlayerList();
        return !players.getOps().isEmpty() && !players.isOp(player.nameAndId());
    }

    private boolean claimAllows(BlockPos pos) {
        // ClaimAccess answers from the saved claims whether or not the feature is on, so without
        // this an ability would still honour stale claim data on a server that turned claims off.
        // ClaimProtection.onBlockBreak carries the same guard for the normal break path.
        if (!SmpConfig.CLAIMS_ENABLED)
            return true;
        long key = ChunkPos.pack(pos);
        if (this.claimByChunk.containsKey(key))
            return this.claimByChunk.get(key);
        boolean allowed = ClaimAccess.canModify(this.player, this.level, ChunkPos.unpack(key));
        this.claimByChunk.put(key, allowed);
        return allowed;
    }
}
