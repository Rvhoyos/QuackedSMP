package mc.smpessentials.mixin;

import mc.smpessentials.dims.EtherVerticalTravel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
 * Both ends of the vertical trip between the overworld and an ether dim, for everything that is not
 * a player. Players have their own per-tick path in EtherVerticalTravel.
 *
 * Down: vanilla discards any entity that falls past the bottom of the world, so player-made entities
 * are sent to the overworld at the exact moment vanilla gives up on them, and nothing is polled.
 *
 * Up: vanilla has no equivalent for the top of the world, so the check rides checkBelowWorld, which
 * already runs once per entity per tick from baseTick and already reads getY(). That makes this one
 * extra comparison rather than a sweep of its own.
 */
@Mixin(Entity.class)
public abstract class EntityVerticalBoundsMixin {

    @Inject(method = "onBelowWorld", at = @At("HEAD"), cancellable = true)
    private void onBelowWorld(CallbackInfo ci) {
        if (EtherVerticalTravel.transferOnVoidFall((Entity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "checkBelowWorld", at = @At("TAIL"))
    private void checkAboveWorld(CallbackInfo ci) {
        EtherVerticalTravel.transferOnSkyRise((Entity) (Object) this);
    }
}
