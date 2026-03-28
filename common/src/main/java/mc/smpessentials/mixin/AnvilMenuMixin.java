package mc.smpessentials.mixin;

import mc.smpessentials.chatfilter.ChatFilter;
import mc.smpessentials.chatfilter.ChatFilterSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Filters anvil item renames through the chat filter.
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {

    // Required by ItemCombinerMenu's constructor
    private AnvilMenuMixin(MenuType<?> menuType, int containerId, Inventory inventory,
            ContainerLevelAccess access, ItemCombinerMenuSlotDefinition slotDef) {
        super(menuType, containerId, inventory, access, slotDef);
    }

    @ModifyVariable(method = "setItemName", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String quackedsmp$filterAnvilName(String itemName) {
        // 'this.player' is inherited from ItemCombinerMenu
        if (!(this.player instanceof ServerPlayer))
            return itemName;
        MinecraftServer server = ((ServerLevel) this.player.level()).getServer();
        if (server != null) {
            ChatFilterSavedData data = ChatFilter.getData(server);
            return ChatFilter.filterText(itemName, data);
        }
        return itemName;
    }
}
