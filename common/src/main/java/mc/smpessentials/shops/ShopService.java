package mc.smpessentials.shops;

import mc.smpessentials.claims.ClaimAccess;
import mc.smpessentials.claims.SpawnProtection;
import mc.smpessentials.commands.CommandRegistrar;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ShopService {
    private ShopService() {}

    private static final int RAYCAST_RANGE = 5;

    // Called from platform event handlers BEFORE the claim check.
    // Returns SUCCESS to consume the event (opens buy GUI for non-owners).
    // Returns PASS to let the event continue (owner opens real chest, or not a shop).
    public static InteractionResult onRightClickBlock(ServerPlayer player, Level level, BlockPos pos) {
        if (!SmpConfig.SHOPS_ENABLED) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.PASS;

        ShopData data = ShopData.get(sl.getServer());
        Optional<ShopEntry> shopOpt = data.getShopAt(sl.dimension(), pos);
        if (shopOpt.isEmpty()) return InteractionResult.PASS;

        ShopEntry shop = shopOpt.get();
        if (player.getUUID().equals(shop.owner()) && !shop.spawnShop()) return InteractionResult.PASS;

        ShopGui.open(player, shop, offerAt(sl, shop));
        return InteractionResult.SUCCESS;
    }

    // Called from platform block break handlers after the break is allowed.
    public static void onBlockBreak(ServerLevel level, BlockPos pos) {
        if (!SmpConfig.SHOPS_ENABLED) return;
        ShopData data = ShopData.get(level.getServer());
        if (data.removeShop(level.dimension(), pos)) {
            mc.smpessentials.SmpUtilsMod.LOGGER.info("[Shops] Shop removed at {} in {}", pos, level.dimension().identifier());
        }
    }

    // Creates a shop on the chest the player is looking at.
    // unit is the number of items per purchase unit (1 = single items, 16 = batches of 16).
    public static int createShop(ServerPlayer player, int price, String currencyItemId, int unit) {
        if (price <= 0) {
            player.sendSystemMessage(Component.literal("\u00a7cPrice must be at least 1."));
            return 0;
        }

        if (unit < 1) {
            player.sendSystemMessage(Component.literal("\u00a7cUnit size must be at least 1."));
            return 0;
        }

        Item currencyItem = resolveItem(currencyItemId);
        if (currencyItem == null || currencyItem == Items.AIR) {
            player.sendSystemMessage(Component.literal("\u00a7cUnknown currency item: " + currencyItemId));
            return 0;
        }

        ServerLevel level = (ServerLevel) player.level();
        BlockHitResult hit = raycastChest(player);
        if (hit == null) {
            player.sendSystemMessage(Component.literal("\u00a7cLook at a chest to create a shop."));
            return 0;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof ChestBlock)) {
            player.sendSystemMessage(Component.literal("\u00a7cThat's not a chest."));
            return 0;
        }

        // Check ownership: must own the claim or be in unclaimed territory (or be OP)
        if (!ClaimAccess.canModify(player, level, ChunkPos.containing(pos))) {
            player.sendSystemMessage(Component.literal("\u00a7cYou can't create a shop in someone else's claim."));
            return 0;
        }

        // Check if already a shop
        ShopData data = ShopData.get(level.getServer());
        if (data.getShopAt(level.dimension(), pos).isPresent()) {
            player.sendSystemMessage(Component.literal("\u00a7cThis chest is already a shop. Use /shop delete first."));
            return 0;
        }

        // Get chest container and find majority item
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container container)) {
            player.sendSystemMessage(Component.literal("\u00a7cCould not access chest."));
            return 0;
        }

        String majorityItem = findMajorityItem(container);
        if (majorityItem == null) {
            player.sendSystemMessage(Component.literal("\u00a7cChest is empty. Fill it with items to sell."));
            return 0;
        }

        boolean spawnShop = SpawnProtection.isBlockInSpawnProtection(level, pos);
        if (spawnShop && !CommandRegistrar.isOp(player.createCommandSourceStack())) {
            player.sendSystemMessage(Component.literal("\u00a7cOnly operators can create shops in spawn protection."));
            return 0;
        }

        ShopEntry entry = new ShopEntry(level.dimension(), pos, player.getUUID(), majorityItem, price, currencyItemId, spawnShop, unit);
        data.addShop(entry);

        String itemName = displayName(ShopOffer.resolve(container, entry), majorityItem);
        String currencyName = getCurrencyName(currencyItemId);
        int stock = spawnShop ? -1 : countItemInContainer(container, majorityItem);

        String msg = "\u00a7aShop created! Selling \u00a7f" + itemName
                + " \u00a7afor \u00a76" + price + " " + currencyName
                + (unit > 1 ? " \u00a7aper " + unit + " items." : "\u00a7a each.");
        if (spawnShop) {
            msg += " \u00a77(Infinite stock)";
        } else {
            msg += " \u00a77(" + stock + " in stock)";
        }
        player.sendSystemMessage(Component.literal(msg));
        return 1;
    }

    public static int deleteShop(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        BlockHitResult hit = raycastChest(player);
        if (hit == null) {
            player.sendSystemMessage(Component.literal("\u00a7cLook at a shop chest to delete it."));
            return 0;
        }

        BlockPos pos = hit.getBlockPos();
        ShopData data = ShopData.get(level.getServer());
        Optional<ShopEntry> shopOpt = data.getShopAt(level.dimension(), pos);
        if (shopOpt.isEmpty()) {
            player.sendSystemMessage(Component.literal("\u00a7cThat chest is not a shop."));
            return 0;
        }

        ShopEntry shop = shopOpt.get();
        boolean isOp = CommandRegistrar.isOp(player.createCommandSourceStack());
        if (!shop.owner().equals(player.getUUID()) && !isOp) {
            player.sendSystemMessage(Component.literal("\u00a7cYou don't own that shop."));
            return 0;
        }

        data.removeShop(level.dimension(), pos);
        player.sendSystemMessage(Component.literal("\u00a7aShop removed."));
        return 1;
    }

    // quantity = number of units to buy. Each unit contains shop.unit() items.
    public static boolean buyItems(ServerPlayer buyer, ResourceKey<Level> dim, BlockPos pos, int quantity) {
        ServerLevel level = ((ServerLevel) buyer.level()).getServer().getLevel(dim);
        if (level == null) return false;

        ShopData data = ShopData.get(((ServerLevel) buyer.level()).getServer());
        Optional<ShopEntry> shopOpt = data.getShopAt(dim, pos);
        if (shopOpt.isEmpty()) return false;

        ShopEntry shop = shopOpt.get();
        int totalItems = quantity * shop.unit();

        if (buyer.getUUID().equals(shop.owner()) && !shop.spawnShop()) {
            buyer.sendSystemMessage(Component.literal("\u00a7cYou can't buy from your own shop."));
            return false;
        }

        Container chest = chestAt(level, shop);
        if (!shop.spawnShop() && chest == null) {
            buyer.sendSystemMessage(Component.literal("\u00a7cShop chest is inaccessible."));
            return false;
        }

        // The offer is one variant of the shop's item, so what is previewed is what is handed over.
        ShopOffer offer = ShopOffer.resolve(chest, shop);
        if (offer.isEmpty()) {
            buyer.sendSystemMessage(Component.literal("\u00a7cThis shop has nothing to sell right now."));
            return false;
        }
        if (offer.available() < totalItems) {
            buyer.sendSystemMessage(Component.literal("\u00a7cOnly " + offer.available() + " in stock."));
            return false;
        }

        // Payment check: price is per unit
        long totalCost = (long) shop.pricePerItem() * quantity;
        String currencyId = shop.currencyItemId();
        int currencyHeld = countCurrencyInInventory(buyer, currencyId);
        String currencyName = getCurrencyName(currencyId);
        if (currencyHeld < totalCost) {
            buyer.sendSystemMessage(Component.literal(
                    "\u00a7cYou need \u00a76" + totalCost + " " + currencyName + " \u00a7cbut only have \u00a76" + currencyHeld + "\u00a7c."));
            return false;
        }

        List<ItemStack> payout;
        if (shop.spawnShop()) {
            // Infinite stock, so the chest is a template to copy rather than stock to consume.
            removeCurrencyFromInventory(buyer, currencyId, (int) totalCost);
            payout = offer.mint(totalItems);
        } else {
            // Capacity check: totalItems removed from chest frees slots for currency
            int capacity = calculateCurrencyCapacity(chest, currencyId, offer.variant(), totalItems);
            if (capacity < totalCost) {
                buyer.sendSystemMessage(Component.literal("\u00a7cShop chest is too full to accept payment."));
                return false;
            }

            removeCurrencyFromInventory(buyer, currencyId, (int) totalCost);
            payout = offer.take(chest, totalItems);
            addCurrencyToContainer(chest, currencyId, (int) totalCost, level, pos);
            chest.setChanged();
        }

        for (ItemStack stack : payout) {
            if (!buyer.getInventory().add(stack)) {
                buyer.drop(stack, false);
            }
        }

        String itemName = offer.variant().getHoverName().getString();
        buyer.sendSystemMessage(Component.literal(
                "\u00a7aPurchased \u00a7f" + totalItems + "x " + itemName
                        + " \u00a7afor \u00a76" + totalCost + " " + currencyName + "\u00a7a."));
        return true;
    }

    // Every variant of the sale item in the chest, in individual items rather than units. This is the
    // total the panel and map report; what a single purchase can draw from is ShopOffer.available.
    public static int getStockCount(ServerLevel level, ShopEntry shop) {
        if (shop.spawnShop()) return Integer.MAX_VALUE;
        Container chest = chestAt(level, shop);
        if (chest == null) return 0;
        int count = countItemInContainer(chest, shop.itemId());
        ShopStockCache.record(shop, count);
        return count;
    }

    /** What the shop hands over next, resolved from its chest. Empty when the chest is unreachable. */
    static ShopOffer offerAt(ServerLevel level, ShopEntry shop) {
        Container chest = chestAt(level, shop);
        // Somebody is standing at the shop, so this is the best chance the panel and map readers get
        // to learn its stock before the chunk unloads again.
        if (chest != null && !shop.spawnShop()) {
            ShopStockCache.record(shop, countItemInContainer(chest, shop.itemId()));
        }
        return ShopOffer.resolve(chest, shop);
    }

    // Null when the chunk is unloaded. Removes the shop entry when the chest itself is gone, which
    // happens whenever a chest is destroyed without the block break handler seeing it.
    private static Container chestAt(ServerLevel level, ShopEntry shop) {
        if (level == null || !level.isLoaded(shop.pos())) return null;
        if (level.getBlockEntity(shop.pos()) instanceof Container container) return container;
        if (!(level.getBlockState(shop.pos()).getBlock() instanceof ChestBlock)) {
            ShopData.get(level.getServer()).removeShop(level.dimension(), shop.pos());
            mc.smpessentials.SmpUtilsMod.LOGGER.info("[Shops] Auto-removed orphaned shop at {} (chest destroyed)", shop.pos());
        }
        return null;
    }

    /**
     * Stock for readers that are not standing at the shop (admin panel, map markers). Falls back
     * to the last seen count when the chunk is unloaded, flagging it as not live rather than
     * reporting the zero {@link #getStockCount} has to return in that case.
     */
    public static ShopStockCache.Reading readStock(ServerLevel level, ShopEntry shop) {
        if (shop.spawnShop())
            return new ShopStockCache.Reading(0, true, true);
        if (level != null && level.isLoaded(shop.pos()))
            return new ShopStockCache.Reading(getStockCount(level, shop), true, false);
        return ShopStockCache.lastSeen(shop)
                .map(c -> new ShopStockCache.Reading(c, false, false))
                .orElse(new ShopStockCache.Reading(0, false, false));
    }

    public static int shopInfo(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        BlockHitResult hit = raycastChest(player);
        if (hit == null) {
            player.sendSystemMessage(Component.literal("\u00a7cLook at a chest to view shop info."));
            return 0;
        }

        ShopData data = ShopData.get(level.getServer());
        Optional<ShopEntry> shopOpt = data.getShopAt(level.dimension(), hit.getBlockPos());
        if (shopOpt.isEmpty()) {
            player.sendSystemMessage(Component.literal("\u00a7cThat chest is not a shop."));
            return 0;
        }

        ShopEntry shop = shopOpt.get();
        String itemName = displayName(offerAt(level, shop), shop.itemId());
        int stock = getStockCount(level, shop);
        String ownerName = resolvePlayerName(player, shop.owner());

        String currencyName = getCurrencyName(shop.currencyItemId());
        player.sendSystemMessage(Component.literal("\u00a76--- Shop Info ---"));
        player.sendSystemMessage(Component.literal("\u00a77Item: \u00a7f" + itemName));
        player.sendSystemMessage(Component.literal("\u00a77Price: \u00a76" + shop.pricePerItem() + " " + currencyName
                + (shop.unit() > 1 ? " per " + shop.unit() : "")));
        player.sendSystemMessage(Component.literal("\u00a77Stock: \u00a7f" + (shop.spawnShop() ? "Unlimited" : String.valueOf(stock))));
        player.sendSystemMessage(Component.literal("\u00a77Owner: \u00a7f" + ownerName));
        return 1;
    }

    // --- Helpers ---

    static BlockHitResult raycastChest(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = start.add(look.x * RAYCAST_RANGE, look.y * RAYCAST_RANGE, look.z * RAYCAST_RANGE);
        BlockHitResult hit = player.level()
                .clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        return hit;
    }

    static String findMajorityItem(Container container) {
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            counts.merge(id, stack.getCount(), Integer::sum);
        }
        if (counts.isEmpty()) return null;
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    // Every variant of the shop's item counts as stock, which is what the panel and map report.
    static int countItemInContainer(Container container, String itemId) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (ShopOffer.matches(stack, itemId)) total += stack.getCount();
        }
        return total;
    }

    /** The offer's own name, falling back to the plain item when no variant could be resolved. */
    static String displayName(ShopOffer offer, String itemId) {
        if (!offer.isEmpty()) return offer.variant().getHoverName().getString();
        Item item = resolveItem(itemId);
        return item != null ? new ItemStack(item).getHoverName().getString() : itemId;
    }

    // Calculates how many currency items the container can accept, accounting for slots that will be
    // freed by removing sale items. Walks in the same order as ShopOffer.take, so it frees the same
    // slots the sale is about to empty.
    private static int calculateCurrencyCapacity(Container container, String currencyId,
                                                  ItemStack variant, int removeCount) {
        Item currency = resolveItem(currencyId);
        if (currency == null) return 0;
        int maxStack = currency.getDefaultMaxStackSize();
        int capacity = 0;
        int removing = removeCount;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                capacity += maxStack;
            } else if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(currencyId)) {
                capacity += maxStack - stack.getCount();
            } else if (removing > 0 && ItemStack.isSameItemSameComponents(stack, variant)) {
                int take = Math.min(removing, stack.getCount());
                removing -= take;
                if (take == stack.getCount()) {
                    capacity += maxStack;
                }
            }
        }
        return capacity;
    }

    private static void addCurrencyToContainer(Container container, String currencyId, int amount,
                                                  ServerLevel level, BlockPos pos) {
        Item currency = resolveItem(currencyId);
        if (currency == null) return;
        int remaining = amount;
        // First try to merge into existing currency stacks
        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = container.getItem(i);
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(currencyId)
                    && stack.getCount() < stack.getMaxStackSize()) {
                int add = Math.min(remaining, stack.getMaxStackSize() - stack.getCount());
                stack.grow(add);
                remaining -= add;
            }
        }
        // Then fill empty slots
        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
            if (container.getItem(i).isEmpty()) {
                int batch = Math.min(remaining, currency.getDefaultMaxStackSize());
                container.setItem(i, new ItemStack(currency, batch));
                remaining -= batch;
            }
        }
        // Should never happen, capacity is checked before purchase
        if (remaining > 0) {
            mc.smpessentials.SmpUtilsMod.LOGGER.warn("[Shops] Currency overflow of {} at {}, this shouldn't happen", remaining, pos);
        }
    }

    static int countCurrencyInInventory(ServerPlayer player, String currencyId) {
        Item currency = resolveItem(currencyId);
        if (currency == null || currency == Items.AIR) return 0;
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(currency)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    // Economy commands still use physical emeralds only
    static int countEmeralds(ServerPlayer player) {
        return countCurrencyInInventory(player, "minecraft:emerald");
    }

    private static void removeCurrencyFromInventory(ServerPlayer player, String currencyId, int amount) {
        Item currency = resolveItem(currencyId);
        if (currency == null || currency == Items.AIR) return;
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || !stack.is(currency)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            if (stack.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
            remaining -= take;
        }
    }

    static String getCurrencyName(String currencyId) {
        Item item = resolveItem(currencyId);
        if (item == null) return currencyId;
        return new ItemStack(item).getHoverName().getString();
    }

    static Item resolveItem(String id) {
        try {
            return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        } catch (Exception e) {
            return null;
        }
    }

    private static String resolvePlayerName(ServerPlayer viewer, java.util.UUID uuid) {
        ServerPlayer target = ((ServerLevel) viewer.level()).getServer().getPlayerList().getPlayer(uuid);
        if (target != null) return target.getGameProfile().name();
        return uuid.toString().substring(0, 8) + "...";
    }
}
