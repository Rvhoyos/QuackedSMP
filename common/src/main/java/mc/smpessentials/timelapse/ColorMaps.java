package mc.smpessentials.timelapse;

import mc.smpessentials.SmpUtilsMod;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Loads the vanilla grass and foliage colormaps into {@link GrassColor} and
 * {@link FoliageColor}. These are client-only textures, so on a dedicated server
 * the tables are empty and {@code Biome.getGrassColor}/{@code getFoliageColor}
 * return white. We bundle the colormaps and initialize the tables once, off the
 * client, so biome tinting works server-side.
 */
final class ColorMaps {

    private static final int SIZE = 256;

    private static volatile boolean loaded = false;

    private ColorMaps() {}

    /** Initializes the vanilla color tables on first call; a no-op afterwards. */
    static synchronized void ensureLoaded() {
        if (loaded) return;
        try {
            GrassColor.init(read("/timelapse/colormap/grass.png"));
            FoliageColor.init(read("/timelapse/colormap/foliage.png"));
            loaded = true;
        } catch (Exception e) {
            // Leave the tables at their defaults (white); grass/foliage just won't tint.
            SmpUtilsMod.LOGGER.warn("[Timelapse] Could not load biome colormaps: {}", e.getMessage());
        }
    }

    // Reads a 256x256 colormap into the row-major 0xRRGGBB layout the vanilla
    // color tables index by (temperature, humidity).
    private static int[] read(String resource) throws Exception {
        try (InputStream in = ColorMaps.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing colormap resource " + resource);
            BufferedImage img = ImageIO.read(in);
            if (img.getWidth() != SIZE || img.getHeight() != SIZE) {
                throw new IllegalStateException("Colormap " + resource + " is not " + SIZE + "x" + SIZE);
            }
            int[] pixels = new int[SIZE * SIZE];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    pixels[y * SIZE + x] = img.getRGB(x, y) & 0xFFFFFF;
                }
            }
            return pixels;
        }
    }
}
