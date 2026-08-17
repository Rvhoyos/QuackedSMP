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
        player.sendSystemMessage(mc.smpessentials.util.TextUtil.motd(
                mc.smpessentials.config.SmpConfig.WELCOME_MESSAGE, player));
    }
}
