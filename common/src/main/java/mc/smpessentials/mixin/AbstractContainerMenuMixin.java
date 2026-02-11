package mc.smpessentials.mixin;

import mc.smpessentials.config.ConfigGui;
import mc.smpessentials.config.ConfigMenuContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void smp$interceptClick(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if ((Object) this instanceof ChestMenu chestMenu) {
            // Check if it's our config menu
            if (((ChestMenuMixin) chestMenu).getContainer() instanceof ConfigMenuContainer configContainer) {
                // It is! Cancel vanilla logic (so they don't take items)
                ci.cancel();

                // Handle our logic
                if (player instanceof ServerPlayer sp && slotId >= 0 && slotId < configContainer.getContainerSize()) {
                    ConfigGui.onClick(sp, configContainer, slotId);
                }
            }
        }
    }
}
