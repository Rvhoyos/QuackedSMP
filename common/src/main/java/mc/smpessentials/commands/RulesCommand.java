package mc.smpessentials.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class RulesCommand {
    private RulesCommand() {}

    /** Called from your CommandRegistrar: .executes(ctx -> RulesCommand.execute(ctx.getSource())) */
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
        player.sendSystemMessage(Component.literal("- 1. Be respectful in chat.").withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("- 2. No griefing or stealing from other players.").withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("- 3. Build big, have fun")
            .withStyle(ChatFormatting.YELLOW));

        // Spacer
        player.sendSystemMessage(Component.empty());


        // Footer hint
        player.sendSystemMessage(Component.literal("/home /claim(s) /trust /untrust /spawn (Commands Provided by QuackedSMP)")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        player.sendSystemMessage(Component.literal("Wiki: https://quackedmod.wiki/")
            .withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.BOLD));

    }
}
