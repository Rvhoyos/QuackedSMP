package mc.smpessentials.ageverify;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers {@code /verify} and {@code /smp verify}. {@code confirm} marks the player as 18+
 * and enables voice chat; {@code deny} opts out. Either subcommand can be re-run to change.
 */
public final class AgeVerifyCommand {

    private AgeVerifyCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(verifySubtree());
    }

    /** Returns the verify subtree for use as a standalone command or as a subcommand (e.g. /smp verify). */
    public static LiteralArgumentBuilder<CommandSourceStack> verifySubtree() {
        return Commands.literal("verify")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .then(Commands.literal("confirm")
                        .executes(ctx -> confirm(ctx.getSource())))
                .then(Commands.literal("deny")
                        .executes(ctx -> deny(ctx.getSource())));
    }

    static int confirm(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer sp))
            return 0;
        AgeVerifyData.get(source.getServer()).setVerified(sp.getUUID());
        sp.sendSystemMessage(Component.literal("\u00a7aAge confirmed! Voice chat is now enabled for you."));
        return 1;
    }

    static int deny(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer sp))
            return 0;
        AgeVerifyData.get(source.getServer()).setDenied(sp.getUUID());
        sp.sendSystemMessage(Component.literal(
                "\u00a7cUnderstood. Voice chat will remain disabled for you. If you ever change your mind, type \u00a7a/verify confirm\u00a7c."));
        return 1;
    }
}
