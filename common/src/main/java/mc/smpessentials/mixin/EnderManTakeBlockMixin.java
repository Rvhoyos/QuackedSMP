package mc.smpessentials.mixin;

import mc.smpessentials.claims.SpawnProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Prevents endermen from picking up blocks in claimed chunks and spawn protection.
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public abstract class EnderManTakeBlockMixin {

    @Shadow @Final private EnderMan enderman;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void blockPickupInProtectedArea(CallbackInfoReturnable<Boolean> cir) {
        if (enderman.level() instanceof ServerLevel sl
                && SpawnProtection.isProtectedArea(sl, enderman.blockPosition())) {
            cir.setReturnValue(false);
        }
    }
}
