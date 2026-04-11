package mc.smpessentials.voicechat;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Optional Simple Voice Chat integration. Detects the API at runtime via Class.forName
// and tracks which players have a voice chat client connected for age-gating.
public final class VoicechatIntegration {

    private VoicechatIntegration() {
    }

    private static boolean available = false;
    private static MinecraftServer server;
    private static final Set<UUID> connectedClients = ConcurrentHashMap.newKeySet();

    // Called once at startup. voicechat_enable is read only here; restart required to toggle.
    public static void init() {
        if (!SmpConfig.VOICECHAT_ENABLE) {
            SmpUtilsMod.LOGGER.info("Voice chat integration disabled in config.");
            return;
        }
        try {
            Class.forName("de.maxhenkel.voicechat.api.VoicechatPlugin");
            available = true;
            SmpUtilsMod.LOGGER.info("Simple Voice Chat API found — age-gating voice chat.");
        } catch (ClassNotFoundException e) {
            SmpUtilsMod.LOGGER.info("Simple Voice Chat not found, skipping voice chat integration.");
        }
    }

    // Must be called from both platform server-started events.
    public static void onServerStart(MinecraftServer s) {
        server = s;
    }

    public static boolean isAvailable() {
        return available;
    }

    // Returns null before server has started.
    public static MinecraftServer getServer() {
        return server;
    }

    public static void onPlayerVoicechatConnected(UUID uuid) {
        connectedClients.add(uuid);
    }

    public static void onPlayerVoicechatDisconnected(UUID uuid) {
        connectedClients.remove(uuid);
    }

    public static boolean hasVoicechatClient(UUID uuid) {
        return connectedClients.contains(uuid);
    }
}
