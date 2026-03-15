package mc.smpessentials.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin that exposes the private {@code container} field of
 * {@link ChestMenu}. Used by {@link AbstractContainerMenuMixin} to detect
 * whether the open chest is backed by a
 * {@link mc.smpessentials.config.ConfigMenuContainer}.
 */
@Mixin(ChestMenu.class)
public interface ChestMenuMixin {
    @Accessor("container")
    Container getContainer();
}
