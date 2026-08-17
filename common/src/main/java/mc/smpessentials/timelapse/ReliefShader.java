package mc.smpessentials.timelapse;

import java.awt.image.BufferedImage;

/**
 * Lights a finished map buffer with continuous hillshading and rasterizes it to
 * an image. Vanilla filled maps snap each column to one of three brightness
 * steps by comparing it to its north neighbour, which stair-steps slopes. This
 * instead builds a surface normal from the local height gradient and lights it
 * with a fixed north-west-above sun, so relief reads as smooth terrain.
 *
 * Operates on the same two buffers {@link MapCanvas} owns: packed
 * {@code 0xRRGGBB} base colours ({@code 0} means unplotted, kept transparent)
 * and per-cell surface heights.
 */
final class ReliefShader {

    // Fixed sun: north-west and above (west = -x, north = -z, up = +y).
    private static final double LX = -0.5, LZ = -0.5, LY = 1.0;
    private static final double L_LEN = Math.sqrt(LX * LX + LZ * LZ + LY * LY);
    // Brightness for perfectly flat ground; slopes brighten/darken around it.
    private static final double FLAT_DOT = LY / L_LEN;

    // Multiplier = BASE + STRENGTH*(dot - FLAT_DOT), clamped. Tuned to sit near
    // vanilla's feel (flat ~0.9, lit faces up to 1.0, shadowed faces down to 0.6).
    private static final double BASE = 0.90;
    private static final double STRENGTH = 0.75;
    private static final double MIN = 0.60;
    private static final double MAX = 1.05;

    private final int width;
    private final int height;
    private final int[] rgb;
    private final int[] heights;
    private final int blocksPerPixel;

    ReliefShader(int width, int height, int[] rgb, int[] heights, int blocksPerPixel) {
        this.width          = width;
        this.height         = height;
        this.rgb            = rgb;
        this.heights        = heights;
        this.blocksPerPixel = blocksPerPixel;
    }

    BufferedImage shade() {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int idx = z * width + x;
                image.setRGB(x, z, rgb[idx] == 0 ? 0 : litColor(x, z, idx));
            }
        }
        return image;
    }

    private int litColor(int x, int z, int idx) {
        int hC = heights[idx];
        // Slope in blocks-per-block; a pixel spans blocksPerPixel blocks.
        double span = 2.0 * blocksPerPixel;
        double dzdx = (heightOr(x + 1, z, hC) - heightOr(x - 1, z, hC)) / span;
        double dzdz = (heightOr(x, z + 1, hC) - heightOr(x, z - 1, hC)) / span;

        // Heightfield normal (-dh/dx, -dh/dz, 1), lit by the fixed sun.
        double nLen = Math.sqrt(dzdx * dzdx + dzdz * dzdz + 1.0);
        double dot = (-dzdx * LX + -dzdz * LZ + LY) / (nLen * L_LEN);

        double m = clamp(BASE + STRENGTH * (dot - FLAT_DOT), MIN, MAX);
        int r = clampByte((int) (((rgb[idx] >> 16) & 0xFF) * m));
        int g = clampByte((int) (((rgb[idx] >> 8) & 0xFF) * m));
        int b = clampByte((int) ((rgb[idx] & 0xFF) * m));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    // Neighbour height, falling back to the centre for edges and unplotted cells
    // so voids and borders read as flat rather than as cliffs.
    private int heightOr(int x, int z, int fallback) {
        if (x < 0 || x >= width || z < 0 || z >= height) return fallback;
        int idx = z * width + x;
        return rgb[idx] == 0 ? fallback : heights[idx];
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int clampByte(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
