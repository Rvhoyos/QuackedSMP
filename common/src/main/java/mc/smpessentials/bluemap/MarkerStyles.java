package mc.smpessentials.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import mc.smpessentials.SmpUtilsMod;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Installs the stylesheet the marker popups need.
 *
 * BlueMap draws markers in a layer that is pointer-events: none and re-enables events only on a
 * POI's icon, so a link inside a popup is inert no matter how it is written. The web app loads
 * every url listed under "styles" in settings.json, and registerStyle is what adds one, so the
 * only way to make a popup interactive is to ship a stylesheet.
 */
public final class MarkerStyles {

    private static final String RESOURCE = "/bluemap/quacksmp-markers.css";

    // Relative to the web root, which is also how the web app requests it.
    private static final String ASSET = "assets/quacksmp-markers.css";

    private MarkerStyles() {
    }

    /** Writes the stylesheet into the web root and registers it. Called once, when BlueMap enables. */
    public static void install(BlueMapAPI api) {
        try (InputStream in = MarkerStyles.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                SmpUtilsMod.LOGGER.warn("[BlueMap] Missing stylesheet resource: {}", RESOURCE);
                return;
            }

            // Always overwrite: the file ships in the jar, so the running version is by definition
            // the correct one. Same reasoning as the icons.
            Path target = api.getWebApp().getWebRoot().resolve(ASSET);
            Files.createDirectories(target.getParent());
            Files.write(target, in.readAllBytes());

            api.getWebApp().registerStyle(ASSET);
        } catch (Exception e) {
            SmpUtilsMod.LOGGER.error("[BlueMap] Failed to install marker stylesheet: {}", e.toString());
        }
    }
}
