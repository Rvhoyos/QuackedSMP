package mc.smpessentials.claims;

import net.minecraft.world.level.Explosion;

import mc.smpessentials.claims.storage.ClaimedSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.damagesource.DamageSource;

// Entry point for claim protection checks. Called from mixins and platform events.
// Returns true to allow, false to deny.
public final class ClaimProtection {
    private ClaimProtection() {
    }

    // No-op, reserved for future initialization.
    public static void init() {
    }

    public static boolean onBlockBreak(Level level, BlockPos pos, BlockState state, Player player) {
        if (!mc.smpessentials.config.SmpConfig.CLAIMS_ENABLED) return true;
        if (player instanceof ServerPlayer sp) {
            boolean allowed = ClaimAccess.canModify(sp, (ServerLevel) level, ChunkPos.containing(pos));
            if (!allowed)
                sp.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("\u00a7cThis area is protected by a land claim."), true);
            return allowed;
        }
        return true;
    }

    public static boolean onBlockPlace(Level level, BlockPos pos, BlockState state, Entity placer) {
        if (!mc.smpessentials.config.SmpConfig.CLAIMS_ENABLED) return true;
        if (placer instanceof ServerPlayer sp) {
            ServerLevel sl = (ServerLevel) level;
            ChunkPos cp = ChunkPos.containing(pos);
            if (ClaimedSavedData.get(sl).isClaimed(sl, cp)) {
                boolean allowed = ClaimAccess.canModify(sp, sl, cp);
                if (!allowed)
                    sp.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal("\u00a7cThis area is protected by a land claim."), true);
                return allowed;
            } else {
                if (state.getFluidState().getType() == net.minecraft.world.level.material.Fluids.LAVA) {
                    if (!mc.smpessentials.config.SmpConfig.ALLOW_LAVA_WILDERNESS) {
                        sp.sendSystemMessage(
                                net.minecraft.network.chat.Component.literal("\u00a7cLava placement is disabled in the wilderness."), true);
                        return false;
                    }
                }
                return true;
            }
        } else {
            if (level instanceof ServerLevel sl && ClaimedSavedData.get(sl).isClaimed(sl, ChunkPos.containing(pos))) {
                if (placer != null) {
                    if (placer.getType() == EntityType.SHEEP)
                        return true;
                    if (placer instanceof Villager)
                        return true;
                    if (placer.getType() == EntityType.FOX)
                        return true;
                    if (placer.getType() == EntityType.BEE)
                        return true;
                    if (placer.getType() == EntityType.TURTLE)
                        return true;
                    if (placer instanceof ArmorStand)
                        return true;
                }

                if (state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock)
                    return true;
                if (state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock)
                    return true;
                if (state.getBlock() instanceof net.minecraft.world.level.block.SugarCaneBlock)
                    return true;
                if (state.getBlock() instanceof net.minecraft.world.level.block.CactusBlock)
                    return true;

                if (state.getFluidState().getType() == net.minecraft.world.level.material.Fluids.LAVA)
                    return false;
                if (state.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock)
                    return false;
                if (state.getBlock() instanceof net.minecraft.world.level.block.IceBlock)
                    return false;

                if (state.getFluidState().getType() == net.minecraft.world.level.material.Fluids.WATER) {
                    return isSourceSameClaim(sl, pos);
                }
                return false;
            }
            return true;
        }
    }

    public static InteractionResult onRightClickBlock(Player player, BlockPos pos) {
        if (!mc.smpessentials.config.SmpConfig.CLAIMS_ENABLED) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp))
            return InteractionResult.PASS;
        boolean allowed = ClaimAccess.canModify(sp, (ServerLevel) sp.level(), ChunkPos.containing(pos));
        if (!allowed)
            sp.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("\u00a7cThis area is protected by a land claim."), true);
        return allowed ? InteractionResult.PASS : InteractionResult.FAIL;
    }

    public static boolean onExplosionPre(Level level, Explosion explosion) {
        if (!mc.smpessentials.config.SmpConfig.CLAIMS_ENABLED) return true;
        if (!mc.smpessentials.config.SmpConfig.PROTECT_EXPLOSIONS) return true;
        if (!(level instanceof ServerLevel sl))
            return true;
        try {
            return !shouldCancelExplosion(sl, explosion);
        } catch (Exception e) {
            mc.smpessentials.SmpUtilsMod.LOGGER.error("[QuackedSMP] Claim explosion check failed, denying for safety", e);
            return false;
        }
    }

    public static boolean onInteractEntity(Player player, Entity entity) {
        if (!mc.smpessentials.config.SmpConfig.CLAIMS_ENABLED) return true;
        if (!(player instanceof ServerPlayer sp))
            return true;
        if (isProtectedEntity(entity) && !canInteractEntity(sp, entity)) {
            sp.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("\u00a7cThis entity is protected by a land claim."), true);
            return false;
        }
        return true;
    }

    public static boolean onLivingHurt(LivingEntity entity, DamageSource source, float amount) {
        if (!mc.smpessentials.config.SmpConfig.CLAIMS_ENABLED) return true;
        if (entity.level().isClientSide()) return true;
        Entity attackerEntity = source.getEntity();

        if (entity instanceof ServerPlayer victim && attackerEntity instanceof ServerPlayer attacker) {
            ServerLevel sl = (ServerLevel) victim.level();
            ChunkPos pos = ChunkPos.containing(victim.blockPosition());
            if (!ClaimedSavedData.get(sl).isClaimed(sl, pos))
                return true;
            if (ClaimAccess.canModify(victim, sl, pos)) {
                return false;
            } else {
                return true;
            }
        }

        if (attackerEntity instanceof ServerPlayer sp) {
            if (isProtectedEntity(entity) && !canInteractEntity(sp, entity)) {
                return false;
            }
        }
        return true;
    }

    // Package-private so SpawnProtection can reuse the same entity classification.
    static boolean isProtectedEntity(Entity e) {
        if (e instanceof Monster)
            return false;
        if (e instanceof Player)
            return false; // PvP handled separately in LIVING_HURT

        return e instanceof LivingEntity || e instanceof ArmorStand || e instanceof HangingEntity;
    }

    private static boolean canInteractEntity(ServerPlayer player, Entity target) {
        if (((ServerLevel) player.level()).getServer().getPlayerList().isOp(player.nameAndId()))
            return true; // OP bypass
        ServerLevel level = (ServerLevel) target.level();
        ChunkPos pos = ChunkPos.containing(target.blockPosition());
        return ClaimAccess.canModify(player, level, pos);
    }

    private static boolean shouldCancelExplosion(ServerLevel level, net.minecraft.world.level.Explosion explosion) {
        // Use mapped names for 1.21: radius() and center()
        float r = explosion.radius();
        net.minecraft.world.phys.Vec3 center = explosion.center();

        // Calculate bounding box of chunks
        int minX = net.minecraft.util.Mth.floor((center.x - r - 2) / 16.0);
        int maxX = net.minecraft.util.Mth.floor((center.x + r + 2) / 16.0);
        int minZ = net.minecraft.util.Mth.floor((center.z - r - 2) / 16.0);
        int maxZ = net.minecraft.util.Mth.floor((center.z + r + 2) / 16.0);

        var data = ClaimedSavedData.get(level);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (data.isClaimed(level, new ChunkPos(x, z))) {
                    return true;
                }
            }
        }
        return false;
    }

    // Returns true if a water-bearing neighbor of pos shares the same claim owner as pos.
    // Called only when pos is already claimed; equal owners (or both wilderness) means the flow
    // stays within one player's land and is safe to allow.
    private static boolean isSourceSameClaim(ServerLevel level, BlockPos pos) {
        var data = ClaimedSavedData.get(level);
        ChunkPos currentChunk = ChunkPos.containing(pos);
        boolean isCurrentClaimed = data.isClaimed(level, currentChunk);

        // Check all 6 neighbors (including Up/Down for waterfalls).
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (level.getFluidState(neighbor).getType() == net.minecraft.world.level.material.Fluids.WATER) {
                ChunkPos neighborChunk = ChunkPos.containing(neighbor);

                java.util.UUID currentOwner = data.getClaim(level, currentChunk)
                        .map(mc.smpessentials.claims.model.ClaimData::owner).orElse(null);
                java.util.UUID neighborOwner = data.getClaim(level, neighborChunk)
                        .map(mc.smpessentials.claims.model.ClaimData::owner).orElse(null);

                if (java.util.Objects.equals(currentOwner, neighborOwner)) {
                    return true;
                }
            }
        }
        return false;
    }
}
