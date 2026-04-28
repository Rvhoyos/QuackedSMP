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

// Cancels block destruction by fire inside claims (gated by PROTECT_FIRE_CLAIMS) and
// the spawn protection area (always-on). Fabric's checkBurnOut has no Direction
// parameter (NeoForge patches one in).
@Mixin(FireBlock.class)
public abstract class FabricFireBlockMixin {

    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void blockBurnInClaim(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
        if (level instanceof ServerLevel sl) {
            if (SmpConfig.CLAIMS_ENABLED && SmpConfig.PROTECT_FIRE_CLAIMS
                    && ClaimedSavedData.get(sl).isClaimed(sl, new ChunkPos(pos))) {
                ci.cancel();
                return;
            }
            if (mc.smpessentials.claims.SpawnProtection.isBlockInSpawnProtection(sl, pos)) {
                ci.cancel();
            }
        }
    }
}
