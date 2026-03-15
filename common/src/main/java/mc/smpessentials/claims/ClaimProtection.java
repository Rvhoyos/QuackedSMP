package mc.smpessentials.claims;

import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.BaseFireBlock;

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

/**
 * Entry point for all claim-protection event checks.
 *
 * <p>Every method is called from a mixin or platform event handler and returns a
 * {@code boolean} (or {@link InteractionResult}) that the caller uses to cancel
 * or allow the vanilla action.  No game logic beyond the protection decision lives here;
 * persistence is handled by {@link mc.smpessentials.claims.storage.ClaimedSavedData}
 * and access decisions by {@link ClaimAccess}.
 *
 * <p>Return-value convention used by callers:
 * <ul>
 *   <li>{@code true}  — allow the action to proceed.</li>
 *   <li>{@code false} — cancel / deny the action.</li>
 * </ul>
 */
public final class ClaimProtection {
    private ClaimProtection() {
    }

    /** Reserved for future initialisation; currently a no-op. */
    public static void init() {
    }

    /**
     * Called before a player breaks a block.
     *
     * @return {@code true} if the break is allowed, {@code false} to cancel it.
     */
    public static boolean onBlockBreak(Level level, BlockPos pos, BlockState state, Player player) {
        if (player instanceof ServerPlayer sp) {
            return ClaimAccess.canModify(sp, (ServerLevel) level, new ChunkPos(pos));
        }
        return true;
    }

    /**
     * Called before a block is placed.
     *
     * <p>For player placers: delegates to {@link ClaimAccess#canModify} on claimed chunks;
     * additionally blocks lava placement in the wilderness when
     * {@link mc.smpessentials.config.SmpConfig#ALLOW_LAVA_WILDERNESS} is false.
     *
     * <p>For non-player placers (pistons, dispensers, mob block-placement): allows
     * natural mob behaviours (sheep, foxes, turtles, bees, villagers, falling blocks,
     * growable blocks, sugar cane, cactus) while blocking fire, lava, snow, and
     * unrecognised foreign block placements inside claims.
     *
     * @return {@code true} to allow the placement, {@code false} to cancel it.
     */
    public static boolean onBlockPlace(Level level, BlockPos pos, BlockState state, Entity placer) {
        if (placer instanceof ServerPlayer sp) {
            ServerLevel sl = (ServerLevel) level;
            ChunkPos cp = new ChunkPos(pos);
            if (ClaimedSavedData.get(sl).isClaimed(sl, cp)) {
                return ClaimAccess.canModify(sp, sl, cp);
            } else {
                if (state.getFluidState().getType() == net.minecraft.world.level.material.Fluids.LAVA) {
                    if (!mc.smpessentials.config.SmpConfig.ALLOW_LAVA_WILDERNESS) {
                        return false;
                    }
                }
                return true;
            }
        } else {
            if (level instanceof ServerLevel sl && ClaimedSavedData.get(sl).isClaimed(sl, new ChunkPos(pos))) {
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

                if (state.getBlock() instanceof BaseFireBlock)
                    return false;
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

    /**
     * Called when a player right-clicks a block (open chest, use lever, etc.).
     *
     * @return {@link InteractionResult#FAIL} to deny the interaction inside a claim the
     *         player cannot modify; {@link InteractionResult#PASS} otherwise.
     */
    public static InteractionResult onRightClickBlock(Player player, BlockPos pos) {
        if (!(player instanceof ServerPlayer sp))
            return InteractionResult.PASS;
        return ClaimAccess.canModify(sp, (ServerLevel) sp.level(), new ChunkPos(pos)) ? InteractionResult.PASS
                : InteractionResult.FAIL;
    }

    /**
     * Called before an explosion is processed.
     *
     * <p>Cancels the explosion (returns {@code false}) if its blast radius overlaps any
     * claimed chunk. Errors during the check are logged and default to denial for safety.
     *
     * @return {@code true} to allow the explosion, {@code false} to cancel it.
     */
    public static boolean onExplosionPre(Level level, Explosion explosion) {
        if (!(level instanceof ServerLevel sl))
            return true;
        try {
            return !shouldCancelExplosion(sl, explosion);
        } catch (Exception e) {
            mc.smpessentials.SmpUtilsMod.LOGGER.error("[QuackedSMP] Claim explosion check failed, denying for safety", e);
            return false;
        }
    }

    /**
     * Called when a player right-clicks (interacts with) an entity.
     *
     * <p>Only protects entities for which {@link #isProtectedEntity} returns {@code true}
     * (animals, armor stands, hanging entities, villagers — not monsters or players).
     *
     * @return {@code true} to allow the interaction, {@code false} to deny it.
     */
    public static boolean onInteractEntity(Player player, Entity entity) {
        if (!(player instanceof ServerPlayer sp))
            return true;
        if (isProtectedEntity(entity) && !canInteractEntity(sp, entity)) {
            return false;
        }
        return true;
    }

    /**
     * Called at the head of {@code LivingEntity.hurtServer} via mixin.
     *
     * <p>PvP policy: a player is safe from PvP damage while standing in a chunk they can
     * modify (i.e., their own claim or a claim they are trusted in). Attacks on strangers
     * inside claimed land are allowed. Non-player entities that are {@link #isProtectedEntity
     * protected} are also shielded from player attacks inside claims.
     *
     * @return {@code true} to let the damage proceed, {@code false} to cancel it.
     */
    public static boolean onLivingHurt(LivingEntity entity, DamageSource source, float amount) {
        if (entity.level().isClientSide())
            return true;
        Entity attackerEntity = source.getEntity();

        if (entity instanceof ServerPlayer victim && attackerEntity instanceof ServerPlayer attacker) {
            ServerLevel sl = (ServerLevel) victim.level();
            ChunkPos pos = new ChunkPos(victim.blockPosition());
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

    private static boolean isProtectedEntity(Entity e) {
        // Protect: Animals, Armor Stands, Hanging entities (Item Frames, Paintings),
        // Villagers
        // Allow: Monsters
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
        ChunkPos pos = new ChunkPos(target.blockPosition());
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

    private static boolean isSourceSameClaim(ServerLevel level, BlockPos pos) {
        var data = ClaimedSavedData.get(level);
        ChunkPos currentChunk = new ChunkPos(pos);
        boolean isCurrentClaimed = data.isClaimed(level, currentChunk);

        // Check 6 neighbors (Up/Down too for waterfalls)
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            // If neighbor has water, it might be the source
            if (level.getFluidState(neighbor).getType() == net.minecraft.world.level.material.Fluids.WATER) {
                ChunkPos neighborChunk = new ChunkPos(neighbor);
                // If neighbor is in a different claim (or wilderness vs claim), then risk!
                // Allowed: Same Claim ID (simplified here as "Is Claimed" + "Can Modify"
                // logic?)
                // Actually: We don't track Claim IDs yet?
                // Wait, audit said "No granular permissions".
                // BUT `ClaimedSavedData` knows WHO owns it.
                // If `getOwner` matches, it's safe.

                java.util.UUID currentOwner = data.getClaim(level, currentChunk)
                        .map(mc.smpessentials.claims.model.ClaimData::owner).orElse(null);
                java.util.UUID neighborOwner = data.getClaim(level, neighborChunk)
                        .map(mc.smpessentials.claims.model.ClaimData::owner).orElse(null);

                // Smart Check:
                // 1. If owners match (both null, or both same UUID) -> Safe.
                // 2. If owners differ -> Unsafe flow.
                if (java.util.Objects.equals(currentOwner, neighborOwner)) {
                    // Found a valid source from same owner (or both wild).
                    // Since we know 'pos' IS claimed (checked before calling), currentOwner is NOT
                    // null.
                    // So neighbor must be owned by same person.
                    return true;
                }
            }
        }
        return false;
    }
}
