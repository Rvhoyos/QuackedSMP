package mc.smpessentials.mixin;

import mc.smpessentials.antixray.AntiXrayEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// When a solid block becomes non-solid, sends real block states for its neighbors
// to all tracking players. Without this, neighbors that were obfuscated at chunk-send
// time remain stale on the client after world changes (TNT, pistons, water, etc.).
@Mixin(ServerLevel.class)
public abstract class BlockUpdateRevealMixin {

    @Inject(method = "sendBlockUpdated", at = @At("TAIL"))
    private void quackedsmp$revealOnBlockChange(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        if (oldState.canOcclude() && !newState.canOcclude()) {
            AntiXrayEngine.revealNeighbors((ServerLevel) (Object) this, pos);
        }
    }
}
