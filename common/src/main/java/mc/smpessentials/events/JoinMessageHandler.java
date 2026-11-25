package mc.smpessentials.events;

import net.minecraft.server.level.ServerPlayer;

/**
 * Handles join notifications.
 *
 * Instead of broadcasting "Player joined", we quietly greet the player
 * privately.
 */
public final class JoinMessageHandler {
    private JoinMessageHandler() {
    }

    /** Called by platform-specific join events. */
    public static void onPlayerJoin(ServerPlayer player) {
        String message = mc.smpessentials.config.SmpConfig.WELCOME_MESSAGE;
        // Blanking the message is how an owner turns the greeting off, so send nothing at all
        // rather than an empty line.
        if (message == null || message.isBlank()) return;
        player.sendSystemMessage(mc.smpessentials.util.TextUtil.motd(message, player));
    }
}
