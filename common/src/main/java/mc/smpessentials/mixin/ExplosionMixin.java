package mc.smpessentials.mixin;

import mc.smpessentials.claims.ClaimProtection;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerExplosion.class)
public abstract class ExplosionMixin {
    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void onExplode(CallbackInfo ci) {
        Explosion explosion = (Explosion) (Object) this;
        if (!ClaimProtection.onExplosionPre(null, explosion)) {
            ci.cancel();
        }
    }
}
