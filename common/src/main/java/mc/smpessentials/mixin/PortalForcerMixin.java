package mc.smpessentials.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.BlockUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Prevents nether portals from auto-generating inside the overworld spawn protection radius.
 * Players can still transit through existing portals in the area; only auto-generation is blocked.
 */
@Mixin(PortalForcer.class)
public abstract class PortalForcerMixin {

    @Shadow private ServerLevel level;

    @Inject(method = "createPortal", at = @At("HEAD"), cancellable = true)
    private void onCreatePortal(BlockPos pos, Direction.Axis axis,
                                CallbackInfoReturnable<Optional<BlockUtil.FoundRectangle>> cir) {
        // ---- Spawn protection (overworld only) ----
        if (this.level.dimension().equals(Level.OVERWORLD)) {
            MinecraftServer server = this.level.getServer();
            int radius = server instanceof DedicatedServer ds ? ds.spawnProtectionRadius() : 0;
            if (radius > 0) {
                BlockPos spawn = this.level.getRespawnData().pos();
                if (Math.abs(pos.getX() - spawn.getX()) <= radius
                        && Math.abs(pos.getZ() - spawn.getZ()) <= radius) {
                    cir.setReturnValue(Optional.empty());
                    return;
                }
            }
        }

        // ---- Claim protection (all dimensions) ----
        // Block auto-generation if the target chunk is claimed by someone other than
        // the travelling player (and the player is not trusted in that claim).
        java.util.UUID playerUuid = mc.smpessentials.claims.PortalEntityCapture.get();
        if (playerUuid != null) {
            net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(pos);
            mc.smpessentials.claims.storage.ClaimedSavedData claims =
                    mc.smpessentials.claims.storage.ClaimedSavedData.get(this.level);
            claims.getClaim(this.level, chunkPos).ifPresent(claimData -> {
                java.util.UUID owner = claimData.owner();
                if (!owner.equals(playerUuid)) {
                    mc.smpessentials.claims.storage.WhitelistSavedData whitelist =
                            mc.smpessentials.claims.storage.WhitelistSavedData.get(this.level);
                    if (!whitelist.isTrusted(owner, playerUuid)) {
                        cir.setReturnValue(Optional.empty());
                    }
                }
            });
        }
    }
}
