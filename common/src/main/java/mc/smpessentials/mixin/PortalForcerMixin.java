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
        if (!this.level.dimension().equals(Level.OVERWORLD)) return;

        MinecraftServer server = this.level.getServer();
        int radius = server instanceof DedicatedServer ds ? ds.spawnProtectionRadius() : 0;
        if (radius <= 0) return;

        BlockPos spawn = this.level.getRespawnData().pos();
        if (Math.abs(pos.getX() - spawn.getX()) <= radius
                && Math.abs(pos.getZ() - spawn.getZ()) <= radius) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
