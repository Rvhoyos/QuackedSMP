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
        String serverName = mc.smpessentials.config.SmpConfig.SERVER_NAME;
        if (serverName == null || serverName.isBlank()) serverName = "QuackedSMP";
        String msg = mc.smpessentials.config.SmpConfig.WELCOME_MESSAGE
                .replace("{server}", serverName)
                .replace("{player}", player.getName().getString());
        player.sendSystemMessage(mc.smpessentials.util.TextUtil.format(msg));
    }
}
