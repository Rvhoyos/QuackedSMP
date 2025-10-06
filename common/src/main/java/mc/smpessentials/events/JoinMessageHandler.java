package mc.smpessentials.events;

import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handles join notifications.
 *
 * Instead of broadcasting "Player joined", we quietly greet the player privately.
 */
public final class JoinMessageHandler {
    private JoinMessageHandler() {}

    /** Register once from common init. */
    public static void init() {
        // Architectury unified join event.
        PlayerEvent.PLAYER_JOIN.register((ServerPlayer player) -> {
            player.sendSystemMessage(
                Component.literal("Welcome, " + player.getName().getString() + "!")
            );
        });
    }
}
