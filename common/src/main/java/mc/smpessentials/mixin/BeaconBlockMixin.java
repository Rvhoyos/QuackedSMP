package mc.smpessentials.mixin;

import mc.smpessentials.beacons.BeaconManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeaconBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.world.level.block.state.BlockBehaviour.class)
public class BeaconBlockMixin {
    @Inject(method = "onRemove", at = @At("HEAD"))
    public void onStateReplaced(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving,
            CallbackInfo ci) {
        if (state.is(newState.getBlock())) {
            return; // Same block, just state change (metadata)
        }
        // Only run logic if the *previous* block was a BEACON
        // We use Blocks.BEACON (which is a Block), checking against the state's block.
        if (state.is(net.minecraft.world.level.block.Blocks.BEACON) && !level.isClientSide) {
            // Block is being removed/broken
            BeaconManager.get().unregister(pos, level.dimension().location().toString());
        }
    }
}
