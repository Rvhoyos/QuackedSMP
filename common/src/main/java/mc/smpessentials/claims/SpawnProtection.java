package mc.smpessentials.claims;

import mc.smpessentials.config.SmpConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// Blocks PvP inside the vanilla spawn protection radius.
// Independent of the claims system; controlled by the spawn_no_pvp config toggle.
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
