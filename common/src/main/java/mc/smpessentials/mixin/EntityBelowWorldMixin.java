package mc.smpessentials.mixin;

import mc.smpessentials.dims.EtherFallthrough;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
 * Vanilla discards any entity that falls past the bottom of the world. In ether dims, player-made
 * entities are sent to the overworld instead, matching what happens to a player who falls through.
 * This is the exact moment vanilla gives up on the entity, so nothing is polled per tick.
 */
@Mixin(Entity.class)
public abstract class EntityBelowWorldMixin {

    @Inject(method = "onBelowWorld", at = @At("HEAD"), cancellable = true)
    private void onBelowWorld(CallbackInfo ci) {
        if (EtherFallthrough.transferOnVoidFall((Entity) (Object) this)) {
            ci.cancel();
        }
    }
}
