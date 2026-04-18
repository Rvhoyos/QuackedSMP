package mc.smpessentials.claims;

import mc.smpessentials.claims.storage.ClaimedSavedData;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;

// Spawn protection utilities: PvP blocking (controlled by spawn_no_pvp toggle) and
// unified protected-area checks for mob-grief prevention (claims + spawn radius).
public final class SpawnProtection {

    private SpawnProtection() {}

    // Returns false to cancel PvP damage when either player is inside spawn protection.
    public static boolean onLivingHurt(LivingEntity entity, DamageSource source) {
        if (!SmpConfig.SPAWN_NO_PVP) return true;
        Entity attackerEntity = source.getEntity();
        if (entity instanceof ServerPlayer victim && attackerEntity instanceof ServerPlayer attacker) {
            if (isInSpawnProtection(victim) || isInSpawnProtection(attacker)) {
                return false;
            }
        }
        return true;
    }

    // Returns true if the position is inside a claimed chunk or within the vanilla spawn
    // protection radius. Unified mob-grief check used by enderman mixins. Existing claim-side
    // explosion/fire checks still live in ClaimProtection and will migrate here in a future refactor.
    public static boolean isProtectedArea(ServerLevel level, BlockPos pos) {
        if (SmpConfig.CLAIMS_ENABLED
                && ClaimedSavedData.get(level).isClaimed(level, new ChunkPos(pos))) {
            return true;
        }
        return isBlockInSpawnProtection(level, pos);
    }

    // Block-position variant of the spawn protection check. Unlike isInSpawnProtection(ServerPlayer),
    // this does not require a player reference so it can be called from mob AI hooks.
    public static boolean isBlockInSpawnProtection(ServerLevel level, BlockPos pos) {
        if (level.dimension() != level.getRespawnData().dimension()) return false;
        if (!(level.getServer() instanceof DedicatedServer ds)) return false;
        int radius = ds.spawnProtectionRadius();
        if (radius <= 0) return false;
        BlockPos spawn = level.getRespawnData().pos();
        return Math.abs(pos.getX() - spawn.getX()) <= radius
                && Math.abs(pos.getZ() - spawn.getZ()) <= radius;
    }

    // Checks if a player's block position falls within the spawn protection square.
    // Reads the radius from server.properties via DedicatedServer.spawnProtectionRadius().
    private static boolean isInSpawnProtection(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        if (level.dimension() != level.getRespawnData().dimension()) return false;
        if (!(level.getServer() instanceof DedicatedServer ds)) return false;
        int radius = ds.spawnProtectionRadius();
        if (radius <= 0) return false;

        BlockPos spawn = level.getRespawnData().pos();
        BlockPos pos = player.blockPosition();
        return Math.abs(pos.getX() - spawn.getX()) <= radius
                && Math.abs(pos.getZ() - spawn.getZ()) <= radius;
    }
}
