package mc.smpessentials.events;

import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.util.TextUtil;
import net.minecraft.server.MinecraftServer;

import java.util.List;

public final class MessageScheduler {
    private MessageScheduler() {
    }

    private static int ticks = 0;
    private static int index = 0;
    private static int panelTicks = 0;

    public static void onServerTick(MinecraftServer server) {
        tick(server);
        tickPanel(server);
    }

    // Independent broadcast of the web-panel link. Shares nothing with the periodic-message
    // rotation above: its own interval and its own gate (public download on + URL set + toggle).
    private static void tickPanel(MinecraftServer server) {
        try {
            if (!SmpConfig.panelLinkAvailable() || !SmpConfig.PANEL_MESSAGE_ENABLED)
                return;
            if (SmpConfig.PANEL_MESSAGE_INTERVAL <= 0)
                return;

            panelTicks++;
            if (panelTicks < SmpConfig.PANEL_MESSAGE_INTERVAL * 20)
                return;
            panelTicks = 0;

            String msg = SmpConfig.panelMessageResolved();
            if (msg == null || msg.isBlank())
                return;
            server.getPlayerList().broadcastSystemMessage(TextUtil.format(msg), false);
        } catch (Exception ignored) {
            // Never let a bad panel URL or format string break the server tick.
        }
    }

    private static void tick(MinecraftServer server) {
        if (SmpConfig.PERIODIC_MESSAGES.isEmpty())
            return;
        if (SmpConfig.MESSAGE_INTERVAL <= 0)
            return;

        ticks++;
        // Interval is in seconds * 20 ticks
        int intervalTicks = SmpConfig.MESSAGE_INTERVAL * 20;

        if (ticks >= intervalTicks) {
            ticks = 0;
            broadcastNext(server);
        }
    }

    private static void broadcastNext(MinecraftServer server) {
        List<String> msgs = SmpConfig.PERIODIC_MESSAGES;
        if (msgs.isEmpty())
            return;

        // Try to find a non-empty message, preventing infinite loop if all are empty
        for (int i = 0; i < msgs.size(); i++) {
            if (index >= msgs.size()) {
                index = 0;
            }

            String line = msgs.get(index);
            index++;

            if (line != null && !line.trim().isEmpty()) {
                // Periodic message broadcast
                server.getPlayerList().broadcastSystemMessage(TextUtil.format(line), false);
                return;
            }
        }
    }
}
