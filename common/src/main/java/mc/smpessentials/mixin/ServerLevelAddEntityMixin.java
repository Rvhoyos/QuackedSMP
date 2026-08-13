package mc.smpessentials.mixin;

import mc.smpessentials.claims.SpawnProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keeps hostile mobs out of the spawn protection radius. addEntity is the single funnel
// for every non-player entity add (natural spawn, spawner, spawn egg, raid, breeding,
// teleport), so blocking here covers all spawn sources. Returning false discards the
// entity before it enters the world.
@Mixin(ServerLevel.class)
public abstract class ServerLevelAddEntityMixin {

    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void smp$blockHostilesInSpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (SpawnProtection.shouldBlockHostileSpawn(self, entity)) {
            cir.setReturnValue(false);
        }
    }
}
