package mc.smpessentials.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// Registers BlueMap API hooks at startup and keeps the marker manager in sync.
public final class BlueMapIntegration {
    private BlueMapIntegration() {
    }

    private static boolean isLoaded = false;
    private static BlueMapMarkerManager markerManager;
    private static MinecraftServer server;

    private static int tickCounter = 0;
    // Safety net only: anything that changes the world marks its layer dirty through MarkerRefresh,
    // so this full sweep exists to catch whatever changes without telling us.
    private static final int UPDATE_INTERVAL_TICKS = 12000; // 10 minutes

    public static void onServerStart(MinecraftServer s) {
        server = s;
        if (isLoaded) {
            ensureCustomDimConfigs(s);
        }
        if (markerManager != null) {
            markerManager.updateAll(s);
        }
    }

    // Called by DimManager when a new custom dim is created at runtime.
    public static void onDimCreated(MinecraftServer s, ResourceKey<Level> dimKey) {
        if (!isLoaded) return;
        if (writeMapConfig(s, dimKey)) {
            scheduleBlueMapReload(s);
        }
    }

    // Called by DimManager when a custom dim is destroyed at runtime.
    // Deletes the BlueMap map config file and triggers a reload so the map disappears.
    public static void onDimDeleted(MinecraftServer s, ResourceKey<Level> dimKey) {
        if (!isLoaded) return;
        String dimId = dimKey.identifier().toString();
        String mapId = dimId.replace(":", "_");
        Path configFile = Path.of("config/bluemap/maps", mapId + ".conf");
        try {
            if (Files.deleteIfExists(configFile)) {
                SmpUtilsMod.LOGGER.info("[BlueMap] Deleted map config for removed dim: {}", dimId);
                scheduleBlueMapReload(s);
            }
        } catch (IOException e) {
            SmpUtilsMod.LOGGER.warn("[BlueMap] Failed to delete map config for {}: {}", dimId, e.getMessage());
        }
    }

    public static void onServerTick(MinecraftServer s) {
        if (!isLoaded || markerManager == null)
            return;

        var due = MarkerRefresh.pollDue();
        if (!due.isEmpty()) {
            markerManager.updateDue(s, due);
            tickCounter = 0;
            return;
        }

        tickCounter++;
        if (tickCounter >= UPDATE_INTERVAL_TICKS) {
            tickCounter = 0;
            markerManager.updateAll(s);
        }
    }

    // bluemap_enable is only read here at startup; changing it at runtime requires a restart.
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
                if (server != null) {
                    ensureCustomDimConfigs(server);
                    markerManager.updateAll(server);
                }
            });

            BlueMapAPI.onDisable(api -> {
                SmpUtilsMod.LOGGER.info("BlueMap disabled, cleaning up markers.");
                if (markerManager != null) {
                    markerManager.cleanup();
                    markerManager = null;
                }
                MarkerRefresh.reset();
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

    // Writes BlueMap map configs for any custom (non-vanilla) dims that are missing one.
    // Triggers a BlueMap reload if any new configs were written.
    private static void ensureCustomDimConfigs(MinecraftServer s) {
        boolean wrote = false;
        for (ServerLevel level : s.getAllLevels()) {
            ResourceKey<Level> dim = level.dimension();
            if (dim.equals(Level.OVERWORLD) || dim.equals(Level.NETHER) || dim.equals(Level.END)) continue;
            if (writeMapConfig(s, dim)) wrote = true;
        }
        if (wrote) {
            scheduleBlueMapReload(s);
        }
    }

    // Writes a BlueMap map config file for the given dimension if one does not already exist.
    // Returns true if a new file was written.
    private static boolean writeMapConfig(MinecraftServer s, ResourceKey<Level> dimKey) {
        try {
            String dimId = dimKey.identifier().toString(); // e.g. "quacksmp:myworld"
            String mapId = dimId.replace(":", "_");        // e.g. "quacksmp_myworld"
            String dimName = dimId.contains(":") ? dimId.substring(dimId.indexOf(':') + 1) : dimId;

            // Resolve config dir relative to the JVM working directory (server root).
            // server.getWorldPath() returns a path ending in "." which gives wrong parent/filename.
            // Never create this directory. BlueMap writes its default overworld/nether/end configs
            // only when it finds no maps directory, so creating it first silently suppresses every
            // vanilla map and the server comes up with custom dims as the only thing on the web map.
            // If it is not there yet, BlueMap has not initialised, and onEnable will call back here.
            Path mapsDir = Path.of("config/bluemap/maps");
            if (!Files.isDirectory(mapsDir)) return false;

            Path configFile = mapsDir.resolve(mapId + ".conf");
            if (Files.exists(configFile)) return false;

            String worldFolder = readWorldFolderName(mapsDir, s);
            String content = "world: \"" + worldFolder + "\"\n"
                    + "dimension: \"" + dimId + "\"\n"
                    + "name: \"" + dimName + "\"\n";
            Files.writeString(configFile, content, StandardCharsets.UTF_8);
            SmpUtilsMod.LOGGER.info("[BlueMap] Wrote map config for custom dim: {}", dimId);
            return true;
        } catch (IOException e) {
            SmpUtilsMod.LOGGER.warn("[BlueMap] Failed to write map config for {}: {}", dimKey.identifier(), e.getMessage());
            return false;
        }
    }

    // Reads the world save path from an existing BlueMap map config (e.g. world.conf), so an admin's
    // own setup wins. Falls back to the live save folder when no config is readable, which happens on
    // a fresh install because BlueMap writes its default configs on a later background thread.
    private static String readWorldFolderName(Path mapsDir, MinecraftServer s) {
        try (var stream = Files.list(mapsDir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!p.toString().endsWith(".conf")) continue;
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    line = line.strip();
                    if (!line.startsWith("world:")) continue;
                    String val = line.substring(6).strip();
                    if (val.startsWith("\"") && val.endsWith("\"")) val = val.substring(1, val.length() - 1);
                    if (!val.isBlank()) return val;
                }
            }
        } catch (IOException ignored) {}
        return worldSavePath(s);
    }

    // BlueMap's "world" setting is a path to the save folder, not just its name. The folder is named
    // by the level-name server property, so it must never be assumed to be "world". Emitted relative
    // to the server working directory to match BlueMap's own generated configs, with forward slashes
    // so the value stays valid on Windows.
    private static String worldSavePath(MinecraftServer s) {
        Path saveDir = s.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path serverDir = Path.of("").toAbsolutePath().normalize();
        Path result = saveDir.startsWith(serverDir) ? serverDir.relativize(saveDir) : saveDir;
        return result.toString().replace('\\', '/');
    }

    private static void scheduleBlueMapReload(MinecraftServer s) {
        s.execute(() ->
            s.getCommands().performPrefixedCommand(
                s.createCommandSourceStack(), "bluemap reload"
            )
        );
    }
}
