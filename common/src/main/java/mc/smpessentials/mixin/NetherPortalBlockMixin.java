package mc.smpessentials.mixin;

import mc.smpessentials.dims.DimManager;
import mc.smpessentials.dims.DimSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Set;

/*
 * Intercepts NetherPortalBlock#getPortalDestination to route custom-dim portals.
 * If the frame block is registered to a custom dim: outbound from vanilla, return from within the dim.
 * Obsidian frames always fall through to vanilla. Spawn island/portal is generated on next tick.
 */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {

    // Cancels vanilla portal logic and returns a custom TeleportTransition when the frame block is registered to a custom dim.
    // If the entity is already in that custom dim, routes back to overworld. Otherwise routes to the custom dim.
    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void onGetPortalDestination(ServerLevel level, Entity entity, BlockPos pos,
                                         CallbackInfoReturnable<TeleportTransition> cir) {
        // Capture the triggering player UUID so PortalForcerMixin can check claim trust.
        // Cleared for non-player entities (mobs, vehicles) so the forcer skips the check.
        if (entity instanceof net.minecraft.server.level.ServerPlayer sp) {
            mc.smpessentials.claims.PortalEntityCapture.set(sp.getUUID());
        } else {
            mc.smpessentials.claims.PortalEntityCapture.clear();
        }

        Block frameBlock = findFrameBlock(level, pos);
        if (frameBlock == null || frameBlock == Blocks.OBSIDIAN) return;

        String blockId = BuiltInRegistries.BLOCK.getKey(frameBlock).toString();
        DimSavedData data = DimSavedData.get(level.getServer());
        Optional<String> maybeDimId = data.getDimForPortalBlock(blockId);
        if (maybeDimId.isEmpty()) return;

        // Bidirectional: custom dim ↔ overworld
        ResourceKey<Level> customKey = ResourceKey.create(
                Registries.DIMENSION, Identifier.parse(maybeDimId.get()));
        ResourceKey<Level> destKey = level.dimension().equals(customKey)
                ? Level.OVERWORLD : customKey;

        ServerLevel dest = level.getServer().getLevel(destKey);
        if (dest == null) return;

        // Determine destination position:
        //   Outbound (→ custom dim): record entry pos for the return trip; resolve landing spot.
        //     - Ether: one shared island, always at global spawn origin.
        //     - Non-ether: one return portal per overworld portal; use entry XZ so each overworld
        //       portal links to the matching position in the custom dim.
        //   Inbound  (→ overworld): restore the position the entity entered from.
        final BlockPos origin;
        if (destKey.equals(Level.OVERWORLD)) {
            origin = DimManager.popReturnPos(entity, level.getServer());
        } else {
            BlockPos entryPos = entity.blockPosition();
            DimManager.saveReturnPos(entity, entryPos, level.getServer());
            final int entryX = entryPos.getX(), entryZ = entryPos.getZ();
            boolean isEther = data.getEntry(destKey.identifier().toString())
                    .map(e -> "ether".equals(e.generatorType())).orElse(false);
            origin = isEther
                    ? DimManager.findSpawnOrigin(level.getServer(), dest)
                    : DimManager.findSpawnOrigin(level.getServer(), dest, entryX, entryZ);
            // Ensure spawn structure on next tick — re-query origin inside execute() so that
            // spawn chunks are loaded and the portal lands at the actual terrain surface,
            // not at the Y=80 fallback that findSpawnOrigin returns for unloaded chunks.
            final MinecraftServer fServer = level.getServer();
            data.getEntry(destKey.identifier().toString())
                    .ifPresent(e -> fServer.execute(() -> {
                        if ("ether".equals(e.generatorType())) {
                            BlockPos freshOrigin = DimManager.findSpawnOrigin(fServer, dest);
                            DimManager.ensureSpawnPlatform(dest, freshOrigin);
                        } else {
                            BlockPos freshOrigin = DimManager.findSpawnOrigin(fServer, dest, entryX, entryZ);
                            DimManager.ensureReturnPortal(dest, freshOrigin);
                        }
                    }));
        }

        // Custom dim portals use 1:1 coordinate mapping — no vanilla 8x nether scaling applied.
        BlockPos scaledOrigin = dest.getWorldBorder().clampToBounds(
                origin.getX(), origin.getY(), origin.getZ());

        Vec3 targetPos = new Vec3(scaledOrigin.getX() + 0.5, scaledOrigin.getY(), scaledOrigin.getZ() + 0.5);
        cir.setReturnValue(new TeleportTransition(
                dest, targetPos, Vec3.ZERO,
                entity.getYRot(), entity.getXRot(),
                Set.of(),
                TeleportTransition.PLAY_PORTAL_SOUND));
    }

    /**
     * Walks outward from the portal block along the perpendicular axis to find
     * the first non-air, non-portal block (the frame).
     */
    @Nullable
    private static Block findFrameBlock(ServerLevel level, BlockPos portalPos) {
        Direction.Axis portalAxis = level.getBlockState(portalPos)
                .getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS)
                .orElse(Direction.Axis.X);
        // Portal blocks with AXIS=X span east-west (X-Y plane); pillars are at the east/west ends.
        // Portal blocks with AXIS=Z span north-south (Z-Y plane); pillars are at the north/south ends.
        Direction dir1 = portalAxis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        Direction dir2 = dir1.getOpposite();

        for (Direction dir : new Direction[]{dir1, dir2}) {
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(
                    portalPos.getX(), portalPos.getY(), portalPos.getZ());
            for (int i = 1; i <= 12; i++) {
                mutable.move(dir);
                BlockState state = level.getBlockState(mutable);
                if (!state.isAir() && !state.is(Blocks.NETHER_PORTAL)) {
                    return state.getBlock();
                }
            }
        }
        return null;
    }
}
