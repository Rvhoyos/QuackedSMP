package mc.smpessentials.mixin;

import mc.smpessentials.claims.storage.ClaimedSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Blocks fire spread into claimed chunks (gated by PROTECT_FIRE_CLAIMS) and throttles it in wilderness.
// The vanilla spread formula adds +40 to ignite odds before the roll, so dividing
// odds is ineffective. Instead, we randomly return 0 to skip spread positions
// entirely (the tick() loop gates on l1 > 0). This scales against multiple sources.
//
// The checkBurnOut injection lives in platform-specific mixins (FabricFireBlockMixin /
// NeoForgeFireBlockMixin) because NeoForge patches that method to add a Direction param.
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    // Returns 0 for claimed chunks so fire never spreads in.
    // For unclaimed wilderness when toggle is off, returns 0 fifteen out of
    // sixteen times so only ~6% of spread positions are considered per tick.
    @Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private void throttleFireSpread(LevelReader level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!(level instanceof ServerLevel sl)) return;
        ChunkPos cp = new ChunkPos(pos);
        boolean claimed = ClaimedSavedData.get(sl).isClaimed(sl, cp);
        if (mc.smpessentials.config.SmpConfig.CLAIMS_ENABLED && mc.smpessentials.config.SmpConfig.PROTECT_FIRE_CLAIMS && claimed) {
            cir.setReturnValue(0);
            return;
        }
        if (!mc.smpessentials.config.SmpConfig.ALLOW_FIRE_WILDERNESS && !claimed
                && java.util.concurrent.ThreadLocalRandom.current().nextInt(16) != 0) {
            cir.setReturnValue(0);
        }
    }
}
