package mc.smpessentials.fabric.mixin;

import mc.smpessentials.claims.storage.ClaimedSavedData;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Cancels block destruction by fire inside claims.
// Fabric's checkBurnOut has no Direction parameter (NeoForge patches one in).
@Mixin(FireBlock.class)
public abstract class FabricFireBlockMixin {

    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void blockBurnInClaim(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
        if (SmpConfig.CLAIMS_ENABLED && level instanceof ServerLevel sl) {
            if (ClaimedSavedData.get(sl).isClaimed(sl, new ChunkPos(pos))) {
                ci.cancel();
            }
        }
    }
}
