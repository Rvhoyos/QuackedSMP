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
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
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
        if (player.getUUID().equals(shop.owner())) return InteractionResult.PASS;
        // OPs can open spawn shop chests to restock or change items
        if (shop.spawnShop() && CommandRegistrar.isOp(player.createCommandSourceStack()))
            return InteractionResult.PASS;

        int stock = getStockCount(sl, shop);
        ShopGui.open(player, shop, stock);
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
        if (!ClaimAccess.canModify(player, level, new ChunkPos(pos))) {
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

        Item item = resolveItem(majorityItem);
        String itemName = item != null ? new ItemStack(item).getHoverName().getString() : majorityItem;
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

        if (buyer.getUUID().equals(shop.owner())) {
            buyer.sendSystemMessage(Component.literal("\u00a7cYou can't buy from your own shop."));
            return false;
        }

        Item saleItem = resolveItem(shop.itemId());
        if (saleItem == null || saleItem == Items.AIR) {
            buyer.sendSystemMessage(Component.literal("\u00a7cShop item is invalid."));
            return false;
        }

        // Stock check (in individual items)
        BlockEntity be = level.getBlockEntity(pos);
        if (!shop.spawnShop()) {
            if (!(be instanceof Container container)) {
                buyer.sendSystemMessage(Component.literal("\u00a7cShop chest is inaccessible."));
                return false;
            }
            int available = countItemInContainer(container, shop.itemId());
            if (available < totalItems) {
                buyer.sendSystemMessage(Component.literal("\u00a7cOnly " + available + " in stock."));
                return false;
            }
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

        // Capacity check: totalItems removed from chest frees slots for currency
        if (!shop.spawnShop() && be instanceof Container container) {
            int capacity = calculateCurrencyCapacity(container, currencyId, shop.itemId(), totalItems);
            if (capacity < totalCost) {
                buyer.sendSystemMessage(Component.literal("\u00a7cShop chest is too full to accept payment."));
                return false;
            }

            // Execute transaction
            removeCurrencyFromInventory(buyer, currencyId, (int) totalCost);
            removeItemsFromContainer(container, shop.itemId(), totalItems);
            addCurrencyToContainer(container, currencyId, (int) totalCost, level, pos);
            if (be instanceof ChestBlockEntity cbe) cbe.setChanged();
        } else {
            // Spawn shop (infinite stock, no chest mutation needed)
            removeCurrencyFromInventory(buyer, currencyId, (int) totalCost);
        }

        // Give items to buyer
        int remaining = totalItems;
        while (remaining > 0) {
            int batch = Math.min(remaining, saleItem.getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(saleItem, batch);
            if (!buyer.getInventory().add(stack)) {
                buyer.drop(stack, false);
            }
            remaining -= batch;
        }

        String itemName = new ItemStack(saleItem).getHoverName().getString();
        buyer.sendSystemMessage(Component.literal(
                "\u00a7aPurchased \u00a7f" + totalItems + "x " + itemName
                        + " \u00a7afor \u00a76" + totalCost + " " + currencyName + "\u00a7a."));
        return true;
    }

    public static int getStockCount(ServerLevel level, ShopEntry shop) {
        if (shop.spawnShop()) return Integer.MAX_VALUE;
        if (!level.isLoaded(shop.pos())) return 0;
        BlockEntity be = level.getBlockEntity(shop.pos());
        if (!(be instanceof Container container)) {
            if (!(level.getBlockState(shop.pos()).getBlock() instanceof ChestBlock)) {
                ShopData.get(level.getServer()).removeShop(level.dimension(), shop.pos());
                mc.smpessentials.SmpUtilsMod.LOGGER.info("[Shops] Auto-removed orphaned shop at {} (chest destroyed)", shop.pos());
            }
            return 0;
        }
        return countItemInContainer(container, shop.itemId());
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
        Item item = resolveItem(shop.itemId());
        String itemName = item != null ? new ItemStack(item).getHoverName().getString() : shop.itemId();
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

    static int countItemInContainer(Container container, String itemId) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // Calculates how many currency items the container can accept,
    // accounting for slots that will be freed by removing sale items.
    private static int calculateCurrencyCapacity(Container container, String currencyId,
                                                  String saleItemId, int removeCount) {
        Item currency = resolveItem(currencyId);
        if (currency == null) return 0;
        int maxStack = currency.getDefaultMaxStackSize();
        int capacity = 0;
        int removing = removeCount;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                capacity += maxStack;
            } else {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (id.equals(currencyId)) {
                    capacity += maxStack - stack.getCount();
                } else if (id.equals(saleItemId) && removing > 0) {
                    int take = Math.min(removing, stack.getCount());
                    removing -= take;
                    if (take == stack.getCount()) {
                        capacity += maxStack;
                    }
                }
            }
        }
        return capacity;
    }

    private static void removeItemsFromContainer(Container container, String itemId, int amount) {
        int remaining = amount;
        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            if (stack.isEmpty()) container.setItem(i, ItemStack.EMPTY);
            remaining -= take;
        }
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
        // Should never happen — capacity is checked before purchase
        if (remaining > 0) {
            mc.smpessentials.SmpUtilsMod.LOGGER.warn("[Shops] Currency overflow of {} at {} — this shouldn't happen", remaining, pos);
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

    private static Item resolveItem(String id) {
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
