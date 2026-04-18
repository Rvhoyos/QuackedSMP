package mc.smpessentials.mixin;

import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.shops.ShopData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Allows non-OP players to interact with shop chests inside vanilla spawn protection.
// Without this, ServerGamePacketListenerImpl.handleUseItemOn calls mayInteract() which
// checks isUnderSpawnProtection() BEFORE the NeoForge/Fabric event fires, blocking
// all interaction and preventing the shop buy GUI from opening.
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "mayInteract", at = @At("HEAD"), cancellable = true)
    private void smp$allowShopInteraction(Entity entity, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!SmpConfig.SHOPS_ENABLED) return;
        if (!(entity instanceof Player)) return;
        ServerLevel self = (ServerLevel) (Object) this;
        if (ShopData.get(self.getServer()).getShopAt(self.dimension(), pos).isPresent()) {
            cir.setReturnValue(true);
        }
    }
}
