
package mc.smpessentials.claims;

import net.minecraft.world.level.block.BaseFireBlock;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.ExplosionEvent;
import dev.architectury.event.events.common.InteractionEvent;
import mc.smpessentials.claims.storage.ClaimedSavedData;
import mc.smpessentials.claims.storage.WhitelistSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.chat.HoverEvent;

public final class ClaimProtection {
    private ClaimProtection() {
    }

    public static void init() {
        // 1. Block Break
        BlockEvent.BREAK.register((level, pos, state, player, exp) -> {
            if (player instanceof ServerPlayer sp) {
                return ClaimAccess.canModify(sp, (ServerLevel) level, new ChunkPos(pos)) ? EventResult.pass()
                        : EventResult.interruptFalse();
            } else {
                // Non-player break (Mobs, Physics)
                if (level instanceof ServerLevel sl && ClaimedSavedData.get(sl).isClaimed(sl, new ChunkPos(pos))) {
                    // Allow: Friendly Mobs Harvesting (Villagers, Foxes, Bees)
                    // Note: Architectury 'player' param might be null for mobs.
                    // To strictly allow mobs, we'd need the entity.
                    // If we can't get the entity, we must block to stop Wither/Enderman.
                    // However, Villager farming often calls 'breakBlock'.
                    // Risk: If we block 'null' player, we block Villagers.
                    // Mitigation: Villagers usually Replant (PLACE). The harvesting (BREAK) is the
                    // issue.
                    // Without entity context, we can filter by Block Type?
                    // Allow breaking Crops/Leaves?
                    if (state.is(BlockTags.CROPS) || state.is(BlockTags.LEAVES)) {
                        return EventResult.pass();
                    }

                    return EventResult.interruptFalse();
                }
                return EventResult.pass();
            }
        });

        // 2. Block Place
        // 2. Block Place
        BlockEvent.PLACE.register((level, pos, state, placer) -> {
            if (placer instanceof ServerPlayer sp) {
                ServerLevel sl = (ServerLevel) level;
                ChunkPos cp = new ChunkPos(pos);
                if (ClaimedSavedData.get(sl).isClaimed(sl, cp)) {
                    // Claimed: Check permissions
                    return ClaimAccess.canModify(sp, sl, cp) ? EventResult.pass()
                            : EventResult.interruptFalse();
                } else {
                    // Wilderness: Check anti-grief (Lava Casting)
                    if (state.getFluidState().getType() == net.minecraft.world.level.material.Fluids.LAVA) {
                        if (!mc.smpessentials.config.SmpConfig.ALLOW_LAVA_WILDERNESS) {
                            // sp.displayClientMessage(Component.literal("Lava is restricted in
                            // wilderness."), true);
                            return EventResult.interruptFalse();
                        }
                    }
                    return EventResult.pass();
                }
            } else {
                // Environment/Automated placement
                if (level instanceof ServerLevel sl && ClaimedSavedData.get(sl).isClaimed(sl, new ChunkPos(pos))) {
                    // 1. Friendly Mobs (Allow Farming/Grazing)
                    if (placer.getType() == EntityType.SHEEP)
                        return EventResult.pass(); // Eat Grass
                    if (placer instanceof Villager)
                        return EventResult.pass(); // Keep Villager as class if it works (for professions?), or use
                                                   // EntityType.VILLAGER
                    if (placer.getType() == EntityType.FOX)
                        return EventResult.pass(); // Berries
                    if (placer.getType() == EntityType.BEE)
                        return EventResult.pass(); // Pollinate/Crops
                    if (placer.getType() == EntityType.TURTLE)
                        return EventResult.pass(); // Lay Eggs
                    if (placer instanceof ArmorStand)
                        return EventResult.pass(); // Keep ArmorStand

                    // 2. Gravity (Sand/Gravel)
                    if (state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock)
                        return EventResult.pass();

                    // 3. Crops/Growth (Natural)
                    if (state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock)
                        return EventResult.pass();
                    if (state.getBlock() instanceof net.minecraft.world.level.block.SugarCaneBlock)
                        return EventResult.pass();
                    if (state.getBlock() instanceof net.minecraft.world.level.block.CactusBlock)
                        return EventResult.pass();

                    // 4. Hazardous: Block Fire, Lava, Snow/Ice forming
                    if (state.getBlock() instanceof BaseFireBlock)
                        return EventResult.interruptFalse();
                    if (state.getFluidState().getType() == net.minecraft.world.level.material.Fluids.LAVA)
                        return EventResult.interruptFalse();
                    if (state.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock)
                        return EventResult.interruptFalse();
                    if (state.getBlock() instanceof net.minecraft.world.level.block.IceBlock)
                        return EventResult.interruptFalse();

                    // 5. Water Flow: Smart Check
                    // If it is Water, allow ONLY if it flows from a neighbor IN THE SAME CLAIM.
                    if (state.getFluidState().getType() == net.minecraft.world.level.material.Fluids.WATER) {
                        return isSourceSameClaim(sl, pos) ? EventResult.pass() : EventResult.interruptFalse();
                    }

                    // Default: Block mysterious environmental changes (Endermen, Withering, etc.)
                    return EventResult.interruptFalse();
                }
                return EventResult.pass();
            }
        });

        // 3. Right-Click Block
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (!(player instanceof ServerPlayer sp))
                return InteractionResult.PASS;
            return ClaimAccess.canModify(sp, (ServerLevel) sp.level(), new ChunkPos(pos)) ? InteractionResult.PASS
                    : InteractionResult.FAIL;
        });

        // ... (Explosion, Entity Interact, Entity Hurt) ...
        // (Keeping existing code below line 63)

        // 4. Explosion Protection (Wilderness Danger, Home Safety)
        // usage of PRE event lets us cancel before calculation, saving performance.
        // We estimate strict impact based on radius.
        ExplosionEvent.PRE.register((level, explosion) -> {
            if (!(level instanceof ServerLevel sl))
                return EventResult.pass();

            // In 1.21+ Explosion is an interface. Methods are mapped as center() and
            // radius() or similar.
            // If compilation fails we will verify names relative to Mapping set.
            // Using standard variable names for now.
            try {
                // Architectury/Fabric usually maps these to accessor style methods or getX
                // Checking standard accessors first.
                // Note: Vec3 center = explosion.center(); float radius = explosion.radius();
                // If these method names are wrong, the compiler will tell us 'cannot find
                // symbol'.

                // We'll use a helper to extract data to keep this lambda clean if we need
                // reflection later?
                // No, let's try direct access.

                // However, we need to know the method names.
                // Previous grep showed: `e` -> `radius`, `f` -> `center`.
                // Mod loaders usually map these to `radius()` and `center()` or `getPower()`.

                // Let's assume `center()` and `radius()` aka `getRadius()`?
                // Vanilla `Explosion` usually has `radius`.
                return shouldCancelExplosion(sl, explosion) ? EventResult.interruptFalse() : EventResult.pass();
            } catch (Exception e) {
                // Fallback or log?
                return EventResult.pass();
            }
        });

        // 5. Entity Interaction (Armor Stands, Item Frames, Animals)
        InteractionEvent.INTERACT_ENTITY.register((player, entity, hand) -> {
            if (!(player instanceof ServerPlayer sp))
                return EventResult.pass();
            if (isProtectedEntity(entity) && !canInteractEntity(sp, entity)) {
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });

        // 6. Entity Attack/Damage (Killing animals, breaking frames)
        // 6. Entity Attack/Damage (Killing animals, breaking frames)
        EntityEvent.LIVING_HURT.register((entity, source, amount) -> {
            if (entity.level().isClientSide)
                return EventResult.pass();

            Entity attackerEntity = source.getEntity();

            // 1. PvP Protection (Victim-based)
            if (entity instanceof ServerPlayer victim && attackerEntity instanceof ServerPlayer attacker) {
                ServerLevel sl = (ServerLevel) victim.level();
                ChunkPos pos = new ChunkPos(victim.blockPosition());

                // If outside any claim, vanilla PvP rules apply
                if (!ClaimedSavedData.get(sl).isClaimed(sl, pos))
                    return EventResult.pass();

                // If inside a claim:
                // "If u are trusted or owner, cant take damage. Otherwise free game."
                if (ClaimAccess.canModify(victim, sl, pos)) {
                    // Victim is Trusted/Owner -> Safe Zone (No Damage)
                    return EventResult.interruptFalse();
                } else {
                    // Victim is Stranger -> Arena/Intruder (Damage Allowed)
                    return EventResult.pass();
                }
            }

            // 2. PvE / Griefing (Player vs Animals/Frames)
            if (attackerEntity instanceof ServerPlayer sp) {
                // Guard against griefing protected entities (non-players)
                if (isProtectedEntity(entity) && !canInteractEntity(sp, entity)) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });

        // 7. Fire Spread & Fluid Place prevention (Best effort via Block Place/Modify
        // if Architectury supports specific events needed)
        // Note: Architectury's BlockEvent.PLACE often covers fire spread or fluid flow
        // if triggered by block updates.
        // For distinct fire spread, additional mixins might be needed if events don't
        // fire.
        // Assuming 'wild fire' is okay, but 'claim fire' is bad.
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
        if (player.hasPermissions(2))
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
