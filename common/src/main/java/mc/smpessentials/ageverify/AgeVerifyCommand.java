package mc.smpessentials.ageverify;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers the {@code /verify} command, allowing players to confirm or deny
 * their age in order to enable or opt out of Simple Voice Chat.
 *
 * <ul>
 *   <li>{@code /verify confirm} — marks the player as 18+ and enables voice chat.</li>
 *   <li>{@code /verify deny}    — opts out; voice chat remains disabled. Player can
 *       re-run {@code /verify confirm} at any time to change their response.</li>
 * </ul>
 */
public final class AgeVerifyCommand {

    private AgeVerifyCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("verify")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .then(Commands.literal("confirm")
                        .executes(ctx -> confirm(ctx.getSource())))
                .then(Commands.literal("deny")
                        .executes(ctx -> deny(ctx.getSource()))));
    }

    private static int confirm(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer sp))
            return 0;
        AgeVerifyData.get(source.getServer()).setVerified(sp.getUUID());
        sp.sendSystemMessage(Component.literal("\u00a7aAge confirmed! Voice chat is now enabled for you."));
        return 1;
    }

    private static int deny(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer sp))
            return 0;
        AgeVerifyData.get(source.getServer()).setDenied(sp.getUUID());
        sp.sendSystemMessage(Component.literal(
                "\u00a7cUnderstood. Voice chat will remain disabled for you. If you ever change your mind, type \u00a7a/verify confirm\u00a7c."));
        return 1;
    }
}
