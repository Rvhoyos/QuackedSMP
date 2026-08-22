package mc.smpessentials.welcomebook;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/** /guide, and the same subtree under /smp so both spellings work. */
public final class WelcomeBookCommand {

    private WelcomeBookCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(guideSubtree());
    }

    /** Returns the guide subtree for use as a standalone command or as a subcommand. */
    public static LiteralArgumentBuilder<CommandSourceStack> guideSubtree() {
        return Commands.literal("guide")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    WelcomeBookService.giveWithFeedback(player);
                    return 1;
                });
    }
}
