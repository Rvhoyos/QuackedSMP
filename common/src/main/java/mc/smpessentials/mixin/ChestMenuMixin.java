package mc.smpessentials.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChestMenu.class)
public interface ChestMenuMixin {
    @Accessor("container")
    Container getContainer();
}
