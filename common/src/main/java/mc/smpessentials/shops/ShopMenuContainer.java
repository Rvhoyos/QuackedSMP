package mc.smpessentials.shops;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.Level;

// Marker container for the shop buy GUI; identified in AbstractContainerMenuMixin.
public class ShopMenuContainer extends SimpleContainer {

    private final BlockPos shopPos;
    private final ResourceKey<Level> shopDim;
    private int selectedQuantity = 1;

    public ShopMenuContainer(int size, BlockPos shopPos, ResourceKey<Level> dim) {
        super(size);
        this.shopPos = shopPos;
        this.shopDim = dim;
    }

    public BlockPos getShopPos() { return shopPos; }
    public ResourceKey<Level> getShopDim() { return shopDim; }
    public int getSelectedQuantity() { return selectedQuantity; }
    public void setSelectedQuantity(int qty) { this.selectedQuantity = qty; }
}
