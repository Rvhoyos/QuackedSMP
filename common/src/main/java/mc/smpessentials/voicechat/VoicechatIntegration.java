package mc.smpessentials.voicechat;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;

/**
 * Optional Simple Voice Chat integration guard.
 * Follows the same pattern as BlueMapIntegration: uses Class.forName to safely
 * detect the API at runtime, only enabling the feature when both the config
 * flag is true and the mod is present.
 */
public final class VoicechatIntegration {

    private VoicechatIntegration() {
    }

    private static boolean available = false;
    private static MinecraftServer server;

    /**
     * Called once during mod startup. Uses {@link Class#forName} to safely detect the
     * Simple Voice Chat API at runtime without a hard dependency. Sets {@link #available}
     * to {@code true} only if both the config flag and the API class are present.
     *
     * <p><b>Hot-toggle not supported.</b> The {@code voicechat_enable} config flag is only
     * read here, at startup. The {@code available} boolean is set once and never re-evaluated.
     * Changing the flag at runtime updates the in-memory value but the Simple Voice Chat
     * plugin is already registered (or not) with the mod's plugin system — that cannot be
     * undone without a restart. A server restart is required for the toggle to take effect.
     */
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

    /**
     * Stores the running {@link MinecraftServer} instance so that event handlers in
     * {@link QuackedVoicechatPlugin} can access {@link mc.smpessentials.ageverify.AgeVerifyData}.
     * Must be called from both the Fabric and NeoForge server-started events.
     */
    public static void onServerStart(MinecraftServer s) {
        server = s;
    }

    /** Returns {@code true} if Simple Voice Chat is present and the integration is enabled. */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Returns the running {@link MinecraftServer}, or {@code null} before the server has started.
     * Used by {@link QuackedVoicechatPlugin} to resolve {@link mc.smpessentials.ageverify.AgeVerifyData}.
     */
    public static MinecraftServer getServer() {
        return server;
    }
}
