package mc.smpessentials.kits;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import mc.smpessentials.config.ConfigData;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.tier.TierService;
import mc.smpessentials.util.TextUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class KitCommands {
    private KitCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> kitSubtree() {
        return Commands.literal("kit")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .executes(ctx -> listKits(ctx.getSource()))
                .then(Commands.literal("list")
                        .executes(ctx -> listKits(ctx.getSource())))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> claimKit(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name"))));
    }

    private static int listKits(CommandSourceStack src) {
        ServerPlayer player = (ServerPlayer) src.getEntity();
        MinecraftServer server = src.getServer();

        if (!SmpConfig.KITS_ENABLED) {
            player.sendSystemMessage(Component.literal("\u00a77Kits are currently disabled."));
            return 1;
        }

        List<ConfigData.KitDef> available = KitService.getAvailableKits(player.getUUID(), server);

        if (available.isEmpty()) {
            player.sendSystemMessage(Component.literal("\u00a77No kits available."));
            return 1;
        }

        long cooldownRemaining = KitService.getRemainingCooldown(player.getUUID(), server);
        String cooldownStatus = cooldownRemaining > 0
                ? "\u00a7c" + KitService.formatDuration(cooldownRemaining)
                : "\u00a7aReady!";

        player.sendSystemMessage(Component.literal("\u00a76\u00a7l== Available Kits =="));
        player.sendSystemMessage(Component.literal("\u00a77Cooldown: " + cooldownStatus));

        for (ConfigData.KitDef kit : available) {
            String tierLabel = kit.minTier > 0
                    ? " \u00a78[\u00a7d" + TierService.getTierName(kit.minTier) + "\u00a78]"
                    : "";
            player.sendSystemMessage(
                    Component.literal("\u00a7e /smp kit " + kit.name)
                            .append(Component.literal(" \u00a77- "))
                            .append(TextUtil.format(kit.displayName))
                            .append(Component.literal(tierLabel)));
        }
        return 1;
    }

    private static int claimKit(CommandSourceStack src, String name) {
        if (mc.smpessentials.hardcore.HardcoreSavedData.denyIfInSession(src)) return 0;
        ServerPlayer player = (ServerPlayer) src.getEntity();
        MinecraftServer server = src.getServer();

        if (!SmpConfig.KITS_ENABLED) {
            player.sendSystemMessage(Component.literal("\u00a77Kits are currently disabled."));
            return 0;
        }

        if (name.equalsIgnoreCase("list")) {
            return listKits(src);
        }

        long remaining = KitService.getRemainingCooldown(player.getUUID(), server);
        if (remaining > 0) {
            player.sendSystemMessage(Component.literal(
                    "\u00a7cYou must wait " + KitService.formatDuration(remaining)
                            + " before claiming another kit."));
            return 0;
        }

        ConfigData.KitDef kit = KitService.findKit(name, player.getUUID(), server);
        if (kit == null) {
            player.sendSystemMessage(Component.literal(
                    "\u00a7cKit '" + name + "' not found. Use \u00a7e/smp kit list \u00a7cto see available kits."));
            return 0;
        }

        KitService.giveKit(player, kit);

        player.sendSystemMessage(
                Component.literal("\u00a7a\u2714 Kit claimed: ")
                        .append(TextUtil.format(kit.displayName)));
        return 1;
    }
}
