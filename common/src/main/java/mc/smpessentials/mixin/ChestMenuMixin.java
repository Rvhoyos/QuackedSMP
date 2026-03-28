package mc.smpessentials.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes ChestMenu.container so AbstractContainerMenuMixin can detect the config GUI backing.
@Mixin(ChestMenu.class)
public interface ChestMenuMixin {
    @Accessor("container")
    Container getContainer();
}
