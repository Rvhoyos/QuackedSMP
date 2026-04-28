package mc.smpessentials.mixin;

import mc.smpessentials.config.ConfigGui;
import mc.smpessentials.config.ConfigMenuContainer;
import mc.smpessentials.shops.ShopGui;
import mc.smpessentials.shops.ShopMenuContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Routes slot clicks to custom GUIs when the open container is a marker type.
@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void smp$interceptClick(int slotId, int button, ContainerInput containerInput, Player player, CallbackInfo ci) {
        if ((Object) this instanceof ChestMenu chestMenu) {
            var container = ((ChestMenuMixin) chestMenu).getContainer();
            if (container instanceof ConfigMenuContainer configContainer) {
                ci.cancel();
                if (player instanceof ServerPlayer sp && slotId >= 0 && slotId < configContainer.getContainerSize()) {
                    ConfigGui.onClick(sp, configContainer, slotId, containerInput);
                }
            } else if (container instanceof ShopMenuContainer shopContainer) {
                ci.cancel();
                if (player instanceof ServerPlayer sp && slotId >= 0 && slotId < shopContainer.getContainerSize()) {
                    ShopGui.onClick(sp, shopContainer, slotId, containerInput);
                }
            }
        }
    }
}
