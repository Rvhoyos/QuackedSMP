package mc.smpessentials.shops;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
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

    public static void open(ServerPlayer buyer, ShopEntry shop, ShopOffer offer) {
        MenuProvider provider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Shop: " + ShopService.displayName(offer, shop.itemId()));
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                ShopMenuContainer container = new ShopMenuContainer(27, shop.pos(), shop.dimension());
                ChestMenu menu = ChestMenu.threeRows(containerId, inventory, container);
                populate(container, shop, offer, 1);
                return menu;
            }
        };
        buyer.openMenu(provider);
    }

    private static void populate(ShopMenuContainer container, ShopEntry shop, ShopOffer offer, int quantity) {
        container.setSelectedQuantity(quantity);
        String currencyName = ShopService.getCurrencyName(shop.currencyItemId());

        // Fill with glass panes
        ItemStack filler = new ItemStack(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY));
        filler.set(DataComponents.CUSTOM_NAME, Component.empty());
        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, filler.copy());
        }

        // Item display (slot 4). A copy of the stack the buyer actually receives, so enchantments,
        // firework durations and every other component show up in the tooltip.
        int totalItems = quantity * shop.unit();
        if (offer.isEmpty()) {
            container.setItem(SLOT_ITEM_DISPLAY, createButton(Items.BARRIER, "\u00a7cNothing to sell right now"));
        } else {
            ItemStack variant = offer.variant();
            ItemStack display = variant.copyWithCount(Math.min(totalItems, variant.getMaxStackSize()));
            // Added to the item's own lore rather than replacing it, so a shop can sell a stack that
            // already carries lore of its own.
            ItemLore lore = display.getOrDefault(DataComponents.LORE, ItemLore.EMPTY)
                    .withLineAdded(Component.literal(shop.unit() > 1
                            ? "\u00a77Price: \u00a76" + shop.pricePerItem() + " " + currencyName + " per " + shop.unit()
                            : "\u00a77Price: \u00a76" + shop.pricePerItem() + " " + currencyName + " each"))
                    .withLineAdded(Component.literal(stockLine(shop, offer.available())));
            display.set(DataComponents.LORE, lore);
            container.setItem(SLOT_ITEM_DISPLAY, display);
        }

        // Quantity controls, bulk shops (unit > 1) get only +1/-1 since each unit is already a batch
        if (shop.unit() > 1) {
            container.setItem(SLOT_MINUS_10, filler.copy());
            container.setItem(SLOT_MINUS_1, createButton(Items.STAINED_GLASS_PANE.pick(DyeColor.RED), "\u00a7c-1"));
            container.setItem(SLOT_PLUS_1, createButton(Items.STAINED_GLASS_PANE.pick(DyeColor.GREEN), "\u00a7a+1"));
            container.setItem(SLOT_PLUS_10, filler.copy());
        } else {
            container.setItem(SLOT_MINUS_10, createButton(Items.CONCRETE.pick(DyeColor.RED), "\u00a7c-10"));
            container.setItem(SLOT_MINUS_1, createButton(Items.STAINED_GLASS_PANE.pick(DyeColor.RED), "\u00a7c-1"));
            container.setItem(SLOT_PLUS_1, createButton(Items.STAINED_GLASS_PANE.pick(DyeColor.GREEN), "\u00a7a+1"));
            container.setItem(SLOT_PLUS_10, createButton(Items.CONCRETE.pick(DyeColor.GREEN), "\u00a7a+10"));
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
        ItemStack confirm = new ItemStack(Items.WOOL.pick(DyeColor.LIME));
        if (shop.unit() > 1) {
            confirm.set(DataComponents.CUSTOM_NAME,
                    Component.literal("\u00a7aBuy " + totalItems + " items for " + totalCost + " " + currencyName));
        } else {
            confirm.set(DataComponents.CUSTOM_NAME,
                    Component.literal("\u00a7aBuy " + quantity + " for " + totalCost + " " + currencyName));
        }
        container.setItem(SLOT_CONFIRM, confirm);

        // Cancel button
        ItemStack cancel = new ItemStack(Items.WOOL.pick(DyeColor.RED));
        cancel.set(DataComponents.CUSTOM_NAME, Component.literal("\u00a7cCancel"));
        container.setItem(SLOT_CANCEL, cancel);
    }

    public static void onClick(ServerPlayer player, ShopMenuContainer container, int slotId, ContainerInput containerInput) {
        ServerLevel level = (ServerLevel) player.level();
        ShopData data = ShopData.get(level.getServer());
        var shopOpt = data.getShopAt(container.getShopDim(), container.getShopPos());
        if (shopOpt.isEmpty()) {
            player.closeContainer();
            player.sendSystemMessage(Component.literal("\u00a7cShop no longer exists."));
            return;
        }

        ShopEntry shop = shopOpt.get();
        // Re-resolved every click, so the quantity cap follows a chest edited while the GUI is open.
        ShopOffer offer = ShopService.offerAt(level.getServer().getLevel(shop.dimension()), shop);
        int maxQty = Math.max(1, shop.spawnShop() ? 2304 / shop.unit() : offer.available() / shop.unit());
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

        populate(container, shop, offer, qty);
        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1f);
    }

    // Counts the offered variant only, not every variant of the shop's item, because that is what
    // this purchase can actually draw from.
    private static String stockLine(ShopEntry shop, int available) {
        if (shop.spawnShop()) return "\u00a77Stock: \u00a7fUnlimited";
        if (shop.unit() > 1) return "\u00a77Stock: \u00a7f" + available / shop.unit() + " units (" + available + " items)";
        return "\u00a77Stock: \u00a7f" + available;
    }

    private static ItemStack createButton(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }
}
