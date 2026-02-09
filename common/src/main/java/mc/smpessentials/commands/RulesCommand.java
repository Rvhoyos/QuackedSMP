package mc.smpessentials.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class RulesCommand {
    private RulesCommand() {
    }

    /**
     * Called from your CommandRegistrar: .executes(ctx ->
     * RulesCommand.execute(ctx.getSource()))
     */
    public static int execute(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException(); // throws CommandSyntaxException if console/CB
            sendRules(player);
            return 1;
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
    }

    private static void sendRules(ServerPlayer player) {
        // Header
        player.sendSystemMessage(Component.literal("— Server Rules —")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        // Bulleted, short vanilla-friendly rules
        // Configured Rules
        for (String rule : mc.smpessentials.config.SmpConfig.RULES) {
            player.sendSystemMessage(mc.smpessentials.util.TextUtil.format(rule));
        }

        // Spacer
        player.sendSystemMessage(Component.empty());

        // Footer hint
        player.sendSystemMessage(Component.literal("/home /claim(s) /trust /untrust /spawn /smp help")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        // Credit (Small)
        player.sendSystemMessage(Component.literal("QuackedSMP by quackedmod.wiki")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

    }
}
