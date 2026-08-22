package mc.smpessentials.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import mc.smpessentials.SmpUtilsMod;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the marker icons: uploads them into each map's AssetStorage once when BlueMap comes up,
 * and hands out the URLs afterwards.
 *
 * The SVGs are generated from the admin panel's pixel icons by the exportIcons Gradle task, so
 * the map and the panel never fork the art. BlueMap's frontend has a strict CSP that rejects
 * data: URIs and external URLs, so icons have to be served from its own asset storage.
 */
public final class IconAssets {

    private static final String RESOURCE_DIR = "/bluemap/icons/";

    // map id -> (icon name -> url). Resolved at upload time so marker building never touches
    // asset storage, which lets it run off the server thread.
    private final Map<String, Map<String, String>> urls = new ConcurrentHashMap<>();

    /** Uploads every icon a marker might use to every map. Called once, when BlueMap enables. */
    public void uploadAll(BlueMapAPI api, Iterable<String> iconNames) {
        for (BlueMapMap map : api.getMaps()) {
            Map<String, String> perMap = urls.computeIfAbsent(map.getId(), k -> new ConcurrentHashMap<>());
            for (String icon : iconNames) {
                String asset = "quacksmp_" + icon + ".svg";
                try {
                    // Always overwrite rather than skipping when it already exists: these files are
                    // generated from the panel art and ship inside the jar, so whatever the running
                    // version carries is by definition the correct one. Skipping would leave an old
                    // build's icons on the map forever.
                    byte[] svg = read(icon);
                    if (svg == null)
                        continue;
                    try (OutputStream os = map.getAssetStorage().writeAsset(asset)) {
                        os.write(svg);
                    }
                    perMap.put(icon, map.getAssetStorage().getAssetUrl(asset));
                } catch (Exception e) {
                    SmpUtilsMod.LOGGER.error("[BlueMap] Failed to upload icon {} for map {}: {}",
                            icon, map.getId(), e.getMessage());
                }
            }
        }
    }

    /** URL for an icon on a map, or null when it was never uploaded. */
    public String url(BlueMapMap map, String iconName) {
        Map<String, String> perMap = urls.get(map.getId());
        return perMap == null ? null : perMap.get(iconName);
    }

    public void clear() {
        urls.clear();
    }

    private static byte[] read(String iconName) {
        try (InputStream in = IconAssets.class.getResourceAsStream(RESOURCE_DIR + iconName + ".svg")) {
            if (in == null) {
                SmpUtilsMod.LOGGER.warn("[BlueMap] Missing icon resource: {}{}.svg", RESOURCE_DIR, iconName);
                return null;
            }
            return in.readAllBytes();
        } catch (Exception e) {
            SmpUtilsMod.LOGGER.error("[BlueMap] Failed to read icon {}: {}", iconName, e.getMessage());
            return null;
        }
    }

}
