package mc.smpessentials.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class GeneralCommands {
        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
                dispatcher.register(Commands.literal("smp")
                                .then(Commands.literal("reload")
                                                .requires(s -> s.hasPermission(2)) // OP only
                                                .executes(GeneralCommands::reloadConfig))
                                .then(Commands.literal("help")
                                                .executes(GeneralCommands::sendHelp)));
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
                src.sendSystemMessage(Component.literal("/tpa <player>").withStyle(cmd)
                                .append(Component.literal(" - Request teleport").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/claim").withStyle(cmd)
                                .append(Component.literal(" - Claim current chunk").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/unclaim").withStyle(cmd)
                                .append(Component.literal(" - Unclaim current chunk").withStyle(desc)));
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

                return 1;
        }

        private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
                try {
                        SmpConfig.load();
                        mc.smpessentials.chatfilter.ChatFilterConfig.mergeFromConfig(ctx.getSource().getServer());
                        ctx.getSource().sendSuccess(() -> Component.literal("QuackedSMP config reloaded!"), true);
                        return 1;
                } catch (Exception e) {
                        ctx.getSource().sendFailure(Component.literal("Failed to reload config: " + e.getMessage()));
                        return 0;
                }
        }
}
