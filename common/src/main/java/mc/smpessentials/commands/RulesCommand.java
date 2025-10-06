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
        player.sendSystemMessage(Component.literal("— SMP Rules —")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        // Bulleted, short vanilla-friendly rules
        player.sendSystemMessage(Component.literal("• Be respectful").withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("• No griefing or stealing").withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("• Build big, share resources, have fun")
            .withStyle(ChatFormatting.YELLOW));

        // Spacer
        player.sendSystemMessage(Component.empty());

        // Links: let the client auto-detect (clickable if the player's setting allows web links)
        player.sendSystemMessage(Component.literal("Live 3D Map: https://map.quackedmod.wiki/")
            .withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("Wiki:       https://quackedmod.wiki/")
            .withStyle(ChatFormatting.AQUA));

        // Footer hint
        player.sendSystemMessage(Component.literal("(Links are clickable if you enabled “Allow Chat: Web links” in Options → Chat Settings.)")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
