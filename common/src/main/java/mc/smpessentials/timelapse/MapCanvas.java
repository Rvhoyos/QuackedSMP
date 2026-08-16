package mc.smpessentials.timelapse;

import net.minecraft.world.level.material.MapColor;

import java.awt.image.BufferedImage;

/**
 * A top-down pixel buffer for one map render. Owns the world-block to pixel
 * mapping (including any downsampling) and the vanilla filled-map relief
 * shading. Plot surface columns into it, then call {@link #toImage()}.
 */
final class MapCanvas {

    private final int width;
    private final int height;
    private final int minBlockX;
    private final int minBlockZ;
    private final int blocksPerPixel;

    // Packed 0xRRGGBB base colors; 0 means unplotted (transparent).
    private final int[] rgb;
    private final int[] heights;

    private MapCanvas(int width, int height, int minBlockX, int minBlockZ, int blocksPerPixel) {
        this.width          = width;
        this.height         = height;
        this.minBlockX      = minBlockX;
        this.minBlockZ      = minBlockZ;
        this.blocksPerPixel = blocksPerPixel;
        this.rgb     = new int[width * height];
        this.heights = new int[width * height];
    }

    /**
     * Builds a canvas covering {@code bounds}, downsampled so its longer side
     * does not exceed {@code maxImageDim} pixels.
     */
    static MapCanvas covering(ChunkBounds bounds, int maxImageDim) {
        int wBlocks = bounds.widthBlocks();
        int hBlocks = bounds.heightBlocks();
        int bpp = Math.max(1, (Math.max(wBlocks, hBlocks) + maxImageDim - 1) / maxImageDim);
        return new MapCanvas(Math.max(1, wBlocks / bpp), Math.max(1, hBlocks / bpp),
                bounds.minBlockX(), bounds.minBlockZ(), bpp);
    }

    /**
     * Plots one surface column. When several blocks fall in the same pixel cell
     * (downsampling), the highest one wins so terrain relief survives.
     *
     * @param baseColor packed {@code 0xRRGGBB}; {@link BiomeTint#NONE} is ignored
     */
    void plot(int worldX, int worldZ, int baseColor, int surfaceY) {
        if (baseColor == BiomeTint.NONE) return;
        int px = (worldX - minBlockX) / blocksPerPixel;
        int pz = (worldZ - minBlockZ) / blocksPerPixel;
        if (px < 0 || px >= width || pz < 0 || pz >= height) return;

        int idx = pz * width + px;
        if (rgb[idx] != 0 && surfaceY <= heights[idx]) return;
        rgb[idx]     = baseColor;
        heights[idx] = surfaceY;
    }

    /**
     * Rasterizes to an ARGB image. Each column is lit relative to the column to
     * its north (higher = brighter, lower = darker), matching vanilla maps.
     * Unplotted pixels are fully transparent.
     */
    BufferedImage toImage() {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int idx = z * width + x;
                image.setRGB(x, z, rgb[idx] == 0 ? 0 : shade(rgb[idx], brightnessAt(x, z, idx)));
            }
        }
        return image;
    }

    private MapColor.Brightness brightnessAt(int x, int z, int idx) {
        if (z == 0) return MapColor.Brightness.NORMAL;
        int northIdx = idx - width;
        if (rgb[northIdx] == 0) return MapColor.Brightness.NORMAL;
        int dy = heights[idx] - heights[northIdx];
        if (dy > 0) return MapColor.Brightness.HIGH;
        if (dy < 0) return MapColor.Brightness.LOW;
        return MapColor.Brightness.NORMAL;
    }

    // Applies the vanilla brightness modifier (per-channel scale out of 255) and
    // makes the pixel fully opaque.
    private static int shade(int color, MapColor.Brightness brightness) {
        int m = brightness.modifier;
        int r = (color >> 16 & 0xFF) * m / 255;
        int g = (color >> 8  & 0xFF) * m / 255;
        int b = (color       & 0xFF) * m / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
