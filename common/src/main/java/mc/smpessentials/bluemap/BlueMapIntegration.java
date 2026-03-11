package mc.smpessentials.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;

/**
 * Bootstrapper class that handles hooking into the BlueMap API when the server
 * starts.
 * Registers enablement and disablement listeners securely and maintains the
 * server context
 * required for the BlueMapMarkerManager to access player dimensions and level
 * data.
 */
public final class BlueMapIntegration {
    private BlueMapIntegration() {
    }

    private static boolean isLoaded = false;
    private static BlueMapMarkerManager markerManager;
    private static MinecraftServer server;

    /**
     * Tracks the number of server ticks elapsed between BlueMap updates.
     */
    private static int tickCounter = 0;
    private static final int UPDATE_INTERVAL_TICKS = 12000; // 10 minutes (20 ticks * 60 seconds * 10 mins)

    public static void onServerStart(MinecraftServer s) {
        server = s;
        if (markerManager != null) {
            markerManager.updateAll();
        }
    }

    /**
     * Periodically called by platform tick events (e.g. ServerTickEvent.Post).
     * Triggers a non-blocking background sync of claims and homes to the BlueMap
     * web interface.
     *
     * @param s The running MinecraftServer instance.
     */
    public static void onServerTick(MinecraftServer s) {
        if (!isLoaded || markerManager == null)
            return;

        tickCounter++;
        if (tickCounter >= UPDATE_INTERVAL_TICKS) {
            tickCounter = 0;
            // Run asynchronously if possible, or just call updateAll() which is relatively
            // fast
            // since we optimized the NBT parsing.
            markerManager.updateAll();
        }
    }

    public static void init() {
        if (!SmpConfig.BLUEMAP_ENABLE) {
            SmpUtilsMod.LOGGER.info("BlueMap integration is disabled in config.");
            return;
        }

        try {
            Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
            isLoaded = true;
            SmpUtilsMod.LOGGER.info("BlueMap API found, hooking into BlueMap...");

            BlueMapAPI.onEnable(api -> {
                SmpUtilsMod.LOGGER.info("BlueMap initialized, setting up markers.");
                markerManager = new BlueMapMarkerManager(api);
                markerManager.updateAll();
            });

            BlueMapAPI.onDisable(api -> {
                SmpUtilsMod.LOGGER.info("BlueMap disabled, cleaning up markers.");
                if (markerManager != null) {
                    markerManager.cleanup();
                    markerManager = null;
                }
            });
        } catch (ClassNotFoundException e) {
            SmpUtilsMod.LOGGER.info("BlueMap API not found, skipping integration.");
        }
    }

    public static BlueMapMarkerManager getMarkerManager() {
        return markerManager;
    }

    public static MinecraftServer getServer() {
        return server;
    }
}
