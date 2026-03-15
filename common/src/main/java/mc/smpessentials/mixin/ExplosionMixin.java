package mc.smpessentials.mixin;

import mc.smpessentials.claims.ClaimProtection;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts {@link ServerExplosion#explode()} to enforce claim protection.
 * If any block that would be destroyed by the explosion falls within a claimed
 * chunk, the entire explosion is cancelled (return value set to 0).
 */
@Mixin(ServerExplosion.class)
public abstract class ExplosionMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void onExplode(CallbackInfoReturnable<Integer> cir) {
        Explosion explosion = (Explosion) (Object) this;
        if (!ClaimProtection.onExplosionPre(this.level, explosion)) {
            cir.setReturnValue(0);
        }
    }
}
