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

/**
 * Intercepts all container slot clicks to route interactions with the
 * in-game configuration GUI ({@link mc.smpessentials.config.ConfigGui}).
 *
 * <p>When the open container is a {@link ChestMenu} backed by a
 * {@link mc.smpessentials.config.ConfigMenuContainer}, vanilla slot logic is
 * cancelled entirely (preventing item movement) and the click is delegated to
 * {@code ConfigGui.onClick} to handle button presses.
 */
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
                    ConfigGui.onClick(sp, configContainer, slotId, clickType);
                }
            }
        }
    }
}
