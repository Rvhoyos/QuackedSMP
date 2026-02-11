package mc.smpessentials.events;

import dev.architectury.event.events.common.TickEvent;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.util.TextUtil;
import net.minecraft.server.MinecraftServer;

import java.util.List;

public final class MessageScheduler {
    private MessageScheduler() {
    }

    private static int ticks = 0;
    private static int index = 0;

    public static void init() {
        TickEvent.SERVER_POST.register((MinecraftServer server) -> {
            tick(server);
        });
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
                System.out.println("[QuackedSMP] Broadcasting Periodic Message (" + (index - 1) + "/" + msgs.size()
                        + "): " + line);
                server.getPlayerList().broadcastSystemMessage(TextUtil.format(line), false);
                return;
            }
        }
    }
}
