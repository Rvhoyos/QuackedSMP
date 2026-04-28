package mc.smpessentials.claims;

import mc.smpessentials.claims.storage.ClaimedSavedData;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;

// Spawn protection utilities: PvP blocking, entity/explosion/interact protection,
// and unified protected-area checks for mob-grief prevention (claims + spawn radius).
public final class SpawnProtection {

    private SpawnProtection() {}

    // Returns false to cancel PvP damage when either player is inside spawn protection,
    // or when a non-OP player attacks a protected entity inside spawn protection.
    public static boolean onLivingHurt(LivingEntity entity, DamageSource source) {
        Entity attackerEntity = source.getEntity();

        if (SmpConfig.SPAWN_NO_PVP
                && entity instanceof ServerPlayer victim
                && attackerEntity instanceof ServerPlayer attacker) {
            if (isInSpawnProtection(victim) || isInSpawnProtection(attacker)) {
                return false;
            }
        }

        if (attackerEntity instanceof ServerPlayer sp
                && !(entity instanceof Player)
                && ClaimProtection.isProtectedEntity(entity)
                && entity.level() instanceof ServerLevel sl
                && isBlockInSpawnProtection(sl, entity.blockPosition())
                && !sl.getServer().getPlayerList().isOp(sp.nameAndId())) {
            return false;
        }

        return true;
    }

    // Returns false to cancel interaction when a non-OP player targets a protected
    // entity inside the spawn protection area.
    public static boolean onInteractEntity(Player player, Entity entity) {
        if (!(player instanceof ServerPlayer sp)) return true;
        if (!ClaimProtection.isProtectedEntity(entity)) return true;
        ServerLevel level = (ServerLevel) entity.level();
        if (!isBlockInSpawnProtection(level, entity.blockPosition())) return true;
        if (level.getServer().getPlayerList().isOp(sp.nameAndId())) return true;
        sp.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("\u00a7cThis entity is protected by spawn protection."), true);
        return false;
    }

    // Returns false to cancel an explosion that overlaps the spawn protection area.
    public static boolean onExplosionPre(ServerLevel level, Explosion explosion) {
        float r = explosion.radius();
        net.minecraft.world.phys.Vec3 center = explosion.center();
        int y = Mth.floor(center.y);

        int minX = Mth.floor(center.x - r - 2);
        int maxX = Mth.floor(center.x + r + 2);
        int minZ = Mth.floor(center.z - r - 2);
        int maxZ = Mth.floor(center.z + r + 2);

        // Check corners and center of the bounding box.
        return !isBlockInSpawnProtection(level, new BlockPos(minX, y, minZ))
                && !isBlockInSpawnProtection(level, new BlockPos(maxX, y, minZ))
                && !isBlockInSpawnProtection(level, new BlockPos(minX, y, maxZ))
                && !isBlockInSpawnProtection(level, new BlockPos(maxX, y, maxZ))
                && !isBlockInSpawnProtection(level, new BlockPos(Mth.floor(center.x), y, Mth.floor(center.z)));
    }

    // Returns true if the position is inside a claimed chunk or within the vanilla spawn
    // protection radius. Used by enderman, farmland, and other mob-grief mixins.
    public static boolean isProtectedArea(ServerLevel level, BlockPos pos) {
        if (SmpConfig.CLAIMS_ENABLED
                && ClaimedSavedData.get(level).isClaimed(level, ChunkPos.containing(pos))) {
            return true;
        }
        return isBlockInSpawnProtection(level, pos);
    }

    // Returns true if pos falls within the spawn protection square. Radius read from
    // server.properties via DedicatedServer.spawnProtectionRadius(). Returns false for
    // non-overworld dimensions, non-dedicated servers, or radius <= 0.
    public static boolean isBlockInSpawnProtection(ServerLevel level, BlockPos pos) {
        if (level.dimension() != level.getRespawnData().dimension()) return false;
        if (!(level.getServer() instanceof DedicatedServer ds)) return false;
        int radius = ds.spawnProtectionRadius();
        if (radius <= 0) return false;
        BlockPos spawn = level.getRespawnData().pos();
        return Math.abs(pos.getX() - spawn.getX()) <= radius
                && Math.abs(pos.getZ() - spawn.getZ()) <= radius;
    }

    private static boolean isInSpawnProtection(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        return isBlockInSpawnProtection(level, player.blockPosition());
    }
}
