package mc.smpessentials.shops;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The exact stack a shop hands over on its next purchase, and how many of it the chest holds.
 *
 * A shop is keyed by a bare item id, so one chest can hold several variants of that item: a Fire
 * Aspect sword beside a plain one, a flight duration 3 rocket beside a duration 1. Selling means
 * moving the real stack rather than building a new one from the id, which is what kept stripping
 * enchantments, firework durations and every other component off the buyer's item.
 *
 * Only one variant is sold per purchase, so the buy GUI can preview exactly what arrives.
 */
public record ShopOffer(ItemStack variant, int available) {

    public static final ShopOffer EMPTY = new ShopOffer(ItemStack.EMPTY, 0);

    public boolean isEmpty() {
        return variant.isEmpty();
    }

    /**
     * What the shop sells next, or {@link #EMPTY} when nothing in the chest can fill a whole unit.
     *
     * @param chest the shop's chest, or null when its chunk is unloaded
     */
    public static ShopOffer resolve(Container chest, ShopEntry shop) {
        if (shop.spawnShop()) return template(chest, shop);
        if (chest == null) return EMPTY;

        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack candidate = chest.getItem(i);
            if (!matches(candidate, shop.itemId())) continue;
            if (seenBefore(chest, i, candidate)) continue;
            int total = countVariant(chest, candidate);
            // A variant too small for one unit cannot be sold short, so skip past it.
            if (total >= shop.unit()) return new ShopOffer(candidate.copyWithCount(1), total);
        }
        return EMPTY;
    }

    /** Removes {@code count} items of this variant from the chest and returns the real stacks. */
    public List<ItemStack> take(Container chest, int count) {
        List<ItemStack> taken = new ArrayList<>();
        int remaining = count;
        for (int i = 0; i < chest.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = chest.getItem(i);
            if (!ItemStack.isSameItemSameComponents(stack, variant)) continue;
            ItemStack part = stack.split(Math.min(remaining, stack.getCount()));
            if (stack.isEmpty()) chest.setItem(i, ItemStack.EMPTY);
            remaining -= part.getCount();
            taken.add(part);
        }
        return taken;
    }

    /** Copies of this variant for a spawn shop, split at the variant's own max stack size. */
    public List<ItemStack> mint(int count) {
        List<ItemStack> copies = new ArrayList<>();
        int max = Math.max(1, variant.getMaxStackSize());
        int remaining = count;
        while (remaining > 0) {
            int batch = Math.min(remaining, max);
            copies.add(variant.copyWithCount(batch));
            remaining -= batch;
        }
        return copies;
    }

    /** True when the stack is the shop's item, whatever components it carries. */
    static boolean matches(ItemStack stack, String itemId) {
        if (stack.isEmpty()) return false;
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId);
    }

    // A spawn shop mints copies and never touches the chest, so it needs no unit's worth of stock,
    // only something to copy. The chest is still where the owner records what that is.
    private static ShopOffer template(Container chest, ShopEntry shop) {
        if (chest != null) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack stack = chest.getItem(i);
                if (matches(stack, shop.itemId())) return new ShopOffer(stack.copyWithCount(1), Integer.MAX_VALUE);
            }
        }
        Item item = ShopService.resolveItem(shop.itemId());
        if (item == null || item == Items.AIR) return EMPTY;
        return new ShopOffer(new ItemStack(item), Integer.MAX_VALUE);
    }

    private static boolean seenBefore(Container chest, int slot, ItemStack candidate) {
        for (int i = 0; i < slot; i++) {
            if (ItemStack.isSameItemSameComponents(chest.getItem(i), candidate)) return true;
        }
        return false;
    }

    private static int countVariant(Container chest, ItemStack variant) {
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, variant)) total += stack.getCount();
        }
        return total;
    }
}
