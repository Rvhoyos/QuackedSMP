package mc.smpessentials.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapAPI;
import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;

public final class BlueMapIntegration {
    private BlueMapIntegration() {
    }

    private static boolean isLoaded = false;
    private static BlueMapMarkerManager markerManager;
    private static MinecraftServer server;

    public static void onServerStart(MinecraftServer s) {
        server = s;
        if (markerManager != null) {
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
