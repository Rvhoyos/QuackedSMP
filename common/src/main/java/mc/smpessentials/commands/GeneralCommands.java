package mc.smpessentials.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class GeneralCommands {
        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
                dispatcher.register(Commands.literal("smp")
                                .then(Commands.literal("reload")
                                                .requires(s -> net.minecraft.commands.Commands.LEVEL_GAMEMASTERS
                                                                .check(s.permissions())) // OP only
                                                .executes(GeneralCommands::reloadConfig))
                                .then(Commands.literal("config")
                                                .requires(s -> net.minecraft.commands.Commands.LEVEL_GAMEMASTERS
                                                                .check(s.permissions())) // OP only
                                                .executes(GeneralCommands::openConfig)
                                                .then(Commands.literal("reset")
                                                                .executes(GeneralCommands::resetConfig)))
                                .then(Commands.literal("help")
                                                .executes(GeneralCommands::sendHelp)));

                dispatcher.register(Commands.literal("mute")
                                .requires(s -> net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(s.permissions()))
                                .then(Commands.argument("player",
                                                net.minecraft.commands.arguments.EntityArgument.player())
                                                .then(Commands.argument("minutes",
                                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                                                .integer(1))
                                                                .executes(GeneralCommands::mutePlayer))));

                dispatcher.register(Commands.literal("unmute")
                                .requires(s -> net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(s.permissions()))
                                .then(Commands.argument("player",
                                                net.minecraft.commands.arguments.EntityArgument.player())
                                                .executes(GeneralCommands::unmutePlayer)));
        }

        private static int mutePlayer(CommandContext<CommandSourceStack> ctx) {
                try {
                        ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                        int minutes = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "minutes");

                        var data = mc.smpessentials.chatfilter.ChatFilter.getData(ctx.getSource().getServer());
                        data.mute(target.getUUID(), minutes * 60 * 1000L);

                        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aMuted "
                                        + target.getName().getString() + " for " + minutes + " minutes."), true);
                        target.sendSystemMessage(Component.literal(
                                        "\u00a7cYou have been muted for " + minutes + " minutes by an operator."));
                        return 1;
                } catch (Exception e) {
                        ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
                        return 0;
                }
        }

        private static int unmutePlayer(CommandContext<CommandSourceStack> ctx) {
                try {
                        ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");

                        var data = mc.smpessentials.chatfilter.ChatFilter.getData(ctx.getSource().getServer());
                        data.unmute(target.getUUID());

                        ctx.getSource().sendSuccess(
                                        () -> Component.literal("\u00a7aUnmuted " + target.getName().getString() + "."),
                                        true);
                        target.sendSystemMessage(Component.literal("\u00a7aYou have been unmuted."));
                        return 1;
                } catch (Exception e) {
                        ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
                        return 0;
                }
        }

        private static int openConfig(CommandContext<CommandSourceStack> ctx) {
                if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        mc.smpessentials.config.ConfigGui.open(sp);
                        return 1;
                }
                return 0;
        }

        private static int sendHelp(CommandContext<CommandSourceStack> ctx) {
                net.minecraft.ChatFormatting title = net.minecraft.ChatFormatting.GOLD;
                net.minecraft.ChatFormatting cmd = net.minecraft.ChatFormatting.YELLOW;
                net.minecraft.ChatFormatting desc = net.minecraft.ChatFormatting.WHITE;

                CommandSourceStack src = ctx.getSource();
                src.sendSystemMessage(
                                Component.literal("== QuackedSMP Commands ==").withStyle(title,
                                                net.minecraft.ChatFormatting.BOLD));

                src.sendSystemMessage(Component.literal("/home").withStyle(cmd)
                                .append(Component.literal(" - Teleport to bed/spawn").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/spawn").withStyle(cmd)
                                .append(Component.literal(" - Teleport to world spawn").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/tpr <player>").withStyle(cmd)
                                .append(Component.literal(" - Request teleport").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/tpa accept/deny").withStyle(cmd)
                                .append(Component.literal(" - Accept or deny request").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/claim").withStyle(cmd)
                                .append(Component.literal(" - Claim current chunk").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/unclaim").withStyle(cmd)
                                .append(Component.literal(" - Unclaim current chunk").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/claim map").withStyle(cmd)
                                .append(Component.literal(" - View nearby claims").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/claim info").withStyle(cmd)
                                .append(Component.literal(" - View claim usage").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/trust <player>").withStyle(cmd)
                                .append(Component.literal(" - Trust player globally").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/untrust <player>").withStyle(cmd)
                                .append(Component.literal(" - Revoke trust").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/trustlist").withStyle(cmd)
                                .append(Component.literal(" - List trusted players").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/claims").withStyle(cmd)
                                .append(Component.literal(" - View claim count (VIPs get bonus!)").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/rules").withStyle(cmd)
                                .append(Component.literal(" - Read server rules").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/skills").withStyle(cmd)
                                .append(Component.literal(" - View your skill levels").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/sos").withStyle(cmd)
                                .append(Component.literal(" - Eject untrusted players in your claims")
                                                .withStyle(desc)));

                return 1;
        }

        private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
                try {
                        SmpConfig.load();
                        var res = mc.smpessentials.chatfilter.ChatFilterConfig
                                        .mergeFromConfig(ctx.getSource().getServer());
                        ctx.getSource().sendSuccess(() -> Component.literal("QuackedSMP config reloaded!"), true);
                        if (!res.warnings().isEmpty()) {
                                ctx.getSource().sendSystemMessage(
                                                Component.literal("\u00a7e[Warning] Chat Filter issues:"));
                                for (String w : res.warnings()) {
                                        ctx.getSource().sendSystemMessage(Component.literal("\u00a7e - " + w));
                                }
                        }
                        return 1;
                } catch (mc.smpessentials.config.ConfigIO.ConfigParseException e) {
                        ctx.getSource().sendFailure(
                                        Component.literal("\u00a7c[Error] Malformed JSON config: " + e.getMessage()));
                        return 0;
                } catch (Exception e) {
                        ctx.getSource().sendFailure(Component.literal("Failed to reload config: " + e.getMessage()));
                        e.printStackTrace();
                        return 0;
                }
        }

        private static int resetConfig(CommandContext<CommandSourceStack> ctx) {
                try {
                        mc.smpessentials.config.ConfigIO.resetToFactory();
                        ctx.getSource().sendSuccess(
                                        () -> Component.literal("\u00a7aConfiguration reset to factory defaults!"),
                                        true);
                        return 1;
                } catch (Exception e) {
                        ctx.getSource().sendFailure(Component.literal("Failed to reset config: " + e.getMessage()));
                        e.printStackTrace();
                        return 0;
                }
        }
}
