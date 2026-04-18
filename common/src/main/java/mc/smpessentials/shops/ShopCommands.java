package mc.smpessentials.shops;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import mc.smpessentials.config.SmpConfig;

public final class ShopCommands {
    private ShopCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shop")
                .requires(src -> SmpConfig.SHOPS_ENABLED && src.getEntity() instanceof ServerPlayer)

                // /shop create <price> [currency_item]
                .then(Commands.literal("create")
                        .then(Commands.argument("price", IntegerArgumentType.integer(1))
                                // /shop create <price> — defaults to minecraft:emerald
                                .executes(ctx -> ShopService.createShop(
                                        ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "price"),
                                        "minecraft:emerald"))
                                // /shop create <price> <currency>
                                .then(Commands.argument("currency", StringArgumentType.string())
                                        .executes(ctx -> ShopService.createShop(
                                                ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "price"),
                                                StringArgumentType.getString(ctx, "currency"))))))

                // /shop delete
                .then(Commands.literal("delete")
                        .executes(ctx -> ShopService.deleteShop(
                                ctx.getSource().getPlayerOrException())))

                // /shop info
                .then(Commands.literal("info")
                        .executes(ctx -> ShopService.shopInfo(
                                ctx.getSource().getPlayerOrException())))

                // /shop list
                .then(Commands.literal("list")
                        .executes(ctx -> listShops(ctx.getSource())))

                // --- Economy subcommands (only visible when economy is enabled) ---

                // /shop deposit <amount>
                .then(Commands.literal("deposit")
                        .requires(src -> SmpConfig.ECONOMY_ENABLED)
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> deposit(
                                        ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "amount")))))

                // /shop withdraw <amount>
                .then(Commands.literal("withdraw")
                        .requires(src -> SmpConfig.ECONOMY_ENABLED)
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> withdraw(
                                        ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "amount")))))

                // /shop transfer <player> <amount>
                .then(Commands.literal("transfer")
                        .requires(src -> SmpConfig.ECONOMY_ENABLED)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> transfer(
                                                ctx.getSource().getPlayerOrException(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amount"))))))

                // /shop balance
                .then(Commands.literal("balance")
                        .requires(src -> SmpConfig.ECONOMY_ENABLED)
                        .executes(ctx -> balance(ctx.getSource().getPlayerOrException())))
        );
    }

    private static int listShops(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var shops = ShopData.get(((net.minecraft.server.level.ServerLevel) player.level()).getServer()).listByOwner(player.getUUID());
        if (shops.isEmpty()) {
            player.sendSystemMessage(Component.literal("\u00a77You don't have any shops."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("\u00a76--- Your Shops (" + shops.size() + ") ---"));
        for (ShopEntry shop : shops) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getValue(net.minecraft.resources.Identifier.parse(shop.itemId()));
            String itemName = item != null ? new net.minecraft.world.item.ItemStack(item).getHoverName().getString() : shop.itemId();
            String dim = shop.dimension().identifier().getPath();
            String currencyName = ShopService.getCurrencyName(shop.currencyItemId());
            player.sendSystemMessage(Component.literal(
                    "\u00a7f" + itemName + " \u00a77@ " + dim
                            + " [" + shop.pos().getX() + ", " + shop.pos().getY() + ", " + shop.pos().getZ() + "]"
                            + " \u00a76" + shop.pricePerItem() + " " + currencyName
                            + (shop.spawnShop() ? " \u00a7a[Spawn]" : "")));
        }
        return 1;
    }

    private static int deposit(ServerPlayer player, int amount) {
        int held = ShopService.countEmeralds(player);
        if (held < amount) {
            player.sendSystemMessage(Component.literal(
                    "\u00a7cYou only have \u00a76" + held + " emeralds\u00a7c."));
            return 0;
        }

        // Remove physical emeralds
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            var stack = player.getInventory().getItem(i);
            if (!stack.is(net.minecraft.world.item.Items.EMERALD)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            if (stack.isEmpty()) player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
            remaining -= take;
        }

        EconomyData.get(((net.minecraft.server.level.ServerLevel) player.level()).getServer()).deposit(player.getUUID(), amount);
        long bal = EconomyData.get(((net.minecraft.server.level.ServerLevel) player.level()).getServer()).getBalance(player.getUUID());
        player.sendSystemMessage(Component.literal(
                "\u00a7aDeposited \u00a76" + amount + " emeralds\u00a7a. Balance: \u00a76" + bal));
        return 1;
    }

    private static int withdraw(ServerPlayer player, int amount) {
        EconomyData econ = EconomyData.get(((net.minecraft.server.level.ServerLevel) player.level()).getServer());
        if (!econ.withdraw(player.getUUID(), amount)) {
            player.sendSystemMessage(Component.literal(
                    "\u00a7cInsufficient balance. You have \u00a76" + econ.getBalance(player.getUUID()) + "\u00a7c."));
            return 0;
        }

        // Give physical emeralds
        int remaining = amount;
        while (remaining > 0) {
            int batch = Math.min(remaining, 64);
            var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, batch);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= batch;
        }

        player.sendSystemMessage(Component.literal(
                "\u00a7aWithdrew \u00a76" + amount + " emeralds\u00a7a. Balance: \u00a76" + econ.getBalance(player.getUUID())));
        return 1;
    }

    private static int transfer(ServerPlayer sender, ServerPlayer recipient, int amount) {
        if (sender.getUUID().equals(recipient.getUUID())) {
            sender.sendSystemMessage(Component.literal("\u00a7cYou can't transfer to yourself."));
            return 0;
        }

        EconomyData econ = EconomyData.get(((net.minecraft.server.level.ServerLevel) sender.level()).getServer());
        if (!econ.transfer(sender.getUUID(), recipient.getUUID(), amount)) {
            sender.sendSystemMessage(Component.literal(
                    "\u00a7cInsufficient balance. You have \u00a76" + econ.getBalance(sender.getUUID()) + "\u00a7c."));
            return 0;
        }

        sender.sendSystemMessage(Component.literal(
                "\u00a7aSent \u00a76" + amount + " emeralds \u00a7ato \u00a7f" + recipient.getGameProfile().name()
                        + "\u00a7a. Balance: \u00a76" + econ.getBalance(sender.getUUID())));
        recipient.sendSystemMessage(Component.literal(
                "\u00a7aReceived \u00a76" + amount + " emeralds \u00a7afrom \u00a7f" + sender.getGameProfile().name()
                        + "\u00a7a. Balance: \u00a76" + econ.getBalance(recipient.getUUID())));
        return 1;
    }

    private static int balance(ServerPlayer player) {
        long bal = EconomyData.get(((net.minecraft.server.level.ServerLevel) player.level()).getServer()).getBalance(player.getUUID());
        player.sendSystemMessage(Component.literal("\u00a77Balance: \u00a76" + bal + " emeralds"));
        return 1;
    }
}
