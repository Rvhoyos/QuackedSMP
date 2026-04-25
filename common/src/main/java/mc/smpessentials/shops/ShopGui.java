package mc.smpessentials.shops;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

// 3-row chest buy GUI with quantity selector and confirmation.
public final class ShopGui {
    private ShopGui() {}

    // Slot layout
    private static final int SLOT_ITEM_DISPLAY = 4;
    private static final int SLOT_MINUS_10 = 10;
    private static final int SLOT_MINUS_1 = 11;
    private static final int SLOT_QTY_DISPLAY = 13;
    private static final int SLOT_PLUS_1 = 15;
    private static final int SLOT_PLUS_10 = 16;
    private static final int SLOT_CONFIRM = 21;
    private static final int SLOT_CANCEL = 23;

    public static void open(ServerPlayer buyer, ShopEntry shop, int stock) {
        MenuProvider provider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                Item item = resolveItem(shop.itemId());
                String itemName = item != null ? new ItemStack(item).getHoverName().getString() : shop.itemId();
                return Component.literal("Shop: " + itemName);
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                ShopMenuContainer container = new ShopMenuContainer(27, shop.pos(), shop.dimension());
                ChestMenu menu = ChestMenu.threeRows(containerId, inventory, container);
                populate(container, shop, stock, 1);
                return menu;
            }
        };
        buyer.openMenu(provider);
    }

    private static void populate(ShopMenuContainer container, ShopEntry shop, int stock, int quantity) {
        container.setSelectedQuantity(quantity);
        String currencyName = ShopService.getCurrencyName(shop.currencyItemId());

        // Fill with glass panes
        ItemStack filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        filler.set(DataComponents.CUSTOM_NAME, Component.empty());
        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, filler.copy());
        }

        // Item display (slot 4)
        Item saleItem = resolveItem(shop.itemId());
        int totalItems = quantity * shop.unit();
        if (saleItem != null) {
            ItemStack display = new ItemStack(saleItem, Math.min(totalItems, saleItem.getDefaultMaxStackSize()));
            List<Component> lore = new ArrayList<>();
            if (shop.unit() > 1) {
                lore.add(Component.literal("\u00a77Price: \u00a76" + shop.pricePerItem() + " " + currencyName + " per " + shop.unit()));
            } else {
                lore.add(Component.literal("\u00a77Price: \u00a76" + shop.pricePerItem() + " " + currencyName + " each"));
            }
            if (shop.spawnShop()) {
                lore.add(Component.literal("\u00a77Stock: \u00a7fUnlimited"));
            } else if (shop.unit() > 1) {
                lore.add(Component.literal("\u00a77Stock: \u00a7f" + stock / shop.unit() + " units (" + stock + " items)"));
            } else {
                lore.add(Component.literal("\u00a77Stock: \u00a7f" + stock));
            }
            display.set(DataComponents.LORE, new ItemLore(lore));
            container.setItem(SLOT_ITEM_DISPLAY, display);
        }

        // Quantity controls — bulk shops (unit > 1) get only +1/-1 since each unit is already a batch
        if (shop.unit() > 1) {
            container.setItem(SLOT_MINUS_10, filler.copy());
            container.setItem(SLOT_MINUS_1, createButton(Items.RED_STAINED_GLASS_PANE, "\u00a7c-1"));
            container.setItem(SLOT_PLUS_1, createButton(Items.GREEN_STAINED_GLASS_PANE, "\u00a7a+1"));
            container.setItem(SLOT_PLUS_10, filler.copy());
        } else {
            container.setItem(SLOT_MINUS_10, createButton(Items.RED_CONCRETE, "\u00a7c-10"));
            container.setItem(SLOT_MINUS_1, createButton(Items.RED_STAINED_GLASS_PANE, "\u00a7c-1"));
            container.setItem(SLOT_PLUS_1, createButton(Items.GREEN_STAINED_GLASS_PANE, "\u00a7a+1"));
            container.setItem(SLOT_PLUS_10, createButton(Items.GREEN_CONCRETE, "\u00a7a+10"));
        }

        // Quantity + cost display (slot 13)
        long totalCost = (long) shop.pricePerItem() * quantity;
        ItemStack qtyItem = new ItemStack(Items.PAPER, Math.min(totalItems, 64));
        if (shop.unit() > 1) {
            qtyItem.set(DataComponents.CUSTOM_NAME,
                    Component.literal("\u00a7fQuantity: " + quantity + " units (" + totalItems + " items)"));
        } else {
            qtyItem.set(DataComponents.CUSTOM_NAME, Component.literal("\u00a7fQuantity: " + quantity));
        }
        List<Component> costLore = new ArrayList<>();
        costLore.add(Component.literal("\u00a77Total: \u00a76" + totalCost + " " + currencyName));
        qtyItem.set(DataComponents.LORE, new ItemLore(costLore));
        container.setItem(SLOT_QTY_DISPLAY, qtyItem);

        // Confirm button
        ItemStack confirm = new ItemStack(Items.LIME_WOOL);
        if (shop.unit() > 1) {
            confirm.set(DataComponents.CUSTOM_NAME,
                    Component.literal("\u00a7aBuy " + totalItems + " items for " + totalCost + " " + currencyName));
        } else {
            confirm.set(DataComponents.CUSTOM_NAME,
                    Component.literal("\u00a7aBuy " + quantity + " for " + totalCost + " " + currencyName));
        }
        container.setItem(SLOT_CONFIRM, confirm);

        // Cancel button
        ItemStack cancel = new ItemStack(Items.RED_WOOL);
        cancel.set(DataComponents.CUSTOM_NAME, Component.literal("\u00a7cCancel"));
        container.setItem(SLOT_CANCEL, cancel);
    }

    public static void onClick(ServerPlayer player, ShopMenuContainer container, int slotId, ClickType clickType) {
        ServerLevel level = (ServerLevel) player.level();
        ShopData data = ShopData.get(level.getServer());
        var shopOpt = data.getShopAt(container.getShopDim(), container.getShopPos());
        if (shopOpt.isEmpty()) {
            player.closeContainer();
            player.sendSystemMessage(Component.literal("\u00a7cShop no longer exists."));
            return;
        }

        ShopEntry shop = shopOpt.get();
        int stock = ShopService.getStockCount(level.getServer().getLevel(shop.dimension()), shop);
        int maxQty = Math.max(1, shop.spawnShop() ? 2304 / shop.unit() : stock / shop.unit());
        int qty = container.getSelectedQuantity();

        switch (slotId) {
            case SLOT_MINUS_10 -> { if (shop.unit() <= 1) qty = Math.max(1, qty - 10); }
            case SLOT_MINUS_1 -> qty = Math.max(1, qty - 1);
            case SLOT_PLUS_1 -> qty = Math.min(maxQty, qty + 1);
            case SLOT_PLUS_10 -> { if (shop.unit() <= 1) qty = Math.min(maxQty, qty + 10); }
            case SLOT_CONFIRM -> {
                player.closeContainer();
                ShopService.buyItems(player, container.getShopDim(), container.getShopPos(), qty);
                return;
            }
            case SLOT_CANCEL -> {
                player.closeContainer();
                return;
            }
            default -> { return; } // filler slots: no-op
        }

        populate(container, shop, stock, qty);
        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1f);
    }

    private static ItemStack createButton(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static Item resolveItem(String id) {
        try {
            return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        } catch (Exception e) {
            return null;
        }
    }
}
