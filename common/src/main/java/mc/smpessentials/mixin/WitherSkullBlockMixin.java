package mc.smpessentials.mixin;

import mc.smpessentials.claims.SpawnProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WitherSkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Blocks wither summoning inside the spawn protection area. No OP bypass.
@Mixin(WitherSkullBlock.class)
public abstract class WitherSkullBlockMixin {

    @Inject(method = "checkSpawn(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/SkullBlockEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private static void blockWitherInSpawn(Level level, BlockPos pos, SkullBlockEntity blockEntity, CallbackInfo ci) {
        if (level instanceof ServerLevel sl && SpawnProtection.isBlockInSpawnProtection(sl, pos)) {
            ci.cancel();
        }
    }
}
