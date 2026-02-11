package mc.smpessentials.config;

import net.minecraft.world.SimpleContainer;

/**
 * Marker container for the Config GUI.
 * Used to identify our menu in the AbstractContainerMenu mixin.
 */
public class ConfigMenuContainer extends SimpleContainer {

    public ConfigMenuContainer(int size) {
        super(size);
    }
}
