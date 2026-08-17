package mc.smpessentials.timelapse;

import java.awt.image.BufferedImage;

/**
 * A top-down pixel buffer for one map render. Owns the world-block to pixel
 * mapping and accumulates the plotted columns; the lighting and rasterization
 * are delegated to {@link ReliefShader}.
 *
 * Renders 1 block = 1 pixel whenever the image fits the render's heap budget.
 * Only when it would not fit does it downsample by the minimum factor needed:
 * blocks that then share a pixel have their colours averaged so the map stays
 * smooth, while the cell keeps the tallest column's height so terrain relief
 * survives. Plot surface columns into it, then call {@link #toImage()}.
 */
final class MapCanvas {

    // Peak heap per output pixel while rendering: rgb int(4) + heights int(4) +
    // count short(2) held here, plus the ARGB image(4) that ReliefShader
    // allocates in toImage() while this canvas is still live. Single source of
    // this constant; the dashboard panel mirrors it for its RAM estimate.
    static final int BYTES_PER_PIXEL = 14;

    private static final int COUNT_MAX = Short.MAX_VALUE;

    private final int width;
    private final int height;
    private final int minBlockX;
    private final int minBlockZ;
    private final int blocksPerPixel;

    // Packed 0xRRGGBB running-mean colours; 0 means unplotted (transparent).
    private final int[] rgb;
    // Tallest surface Y per cell (for relief shading).
    private final int[] heights;
    // Samples averaged into each cell so far.
    private final short[] count;

    private MapCanvas(int width, int height, int minBlockX, int minBlockZ, int blocksPerPixel) {
        this.width          = width;
        this.height         = height;
        this.minBlockX      = minBlockX;
        this.minBlockZ      = minBlockZ;
        this.blocksPerPixel = blocksPerPixel;
        this.rgb     = new int[width * height];
        this.heights = new int[width * height];
        this.count   = new short[width * height];
    }

    /**
     * Builds a canvas covering {@code bounds} at 1 block = 1 pixel, stepping up
     * blocks-per-pixel only as far as needed to keep the image within
     * {@code budgetBytes} of heap. A world that fits renders 1:1.
     */
    static MapCanvas covering(ChunkBounds bounds, long budgetBytes) {
        long wBlocks = bounds.widthBlocks();
        long hBlocks = bounds.heightBlocks();
        int bpp = 1;
        while ((wBlocks / bpp) * (hBlocks / bpp) * BYTES_PER_PIXEL > budgetBytes) bpp++;
        return new MapCanvas((int) Math.max(1, wBlocks / bpp), (int) Math.max(1, hBlocks / bpp),
                bounds.minBlockX(), bounds.minBlockZ(), bpp);
    }

    /**
     * Plots one surface column, averaging its colour into the pixel cell and
     * keeping the cell's tallest height.
     *
     * @param baseColor packed {@code 0xRRGGBB}; {@link BiomeTint#NONE} is ignored
     */
    void plot(int worldX, int worldZ, int baseColor, int surfaceY) {
        if (baseColor == BiomeTint.NONE) return;
        int px = (worldX - minBlockX) / blocksPerPixel;
        int pz = (worldZ - minBlockZ) / blocksPerPixel;
        if (px < 0 || px >= width || pz < 0 || pz >= height) return;

        int idx = pz * width + px;
        int n = count[idx];
        if (n == 0) {
            rgb[idx]     = baseColor;
            heights[idx] = surfaceY;
            count[idx]   = 1;
            return;
        }
        rgb[idx] = mean(rgb[idx], n, baseColor);
        if (surfaceY > heights[idx]) heights[idx] = surfaceY;
        if (n < COUNT_MAX) count[idx] = (short) (n + 1);
    }

    /** Blocks each pixel covers: 1 is full 1:1, higher means this render downsampled. */
    int blocksPerPixel() { return blocksPerPixel; }

    /** Rasterizes to an ARGB image with hillshaded relief; unplotted pixels stay transparent. */
    BufferedImage toImage() {
        return new ReliefShader(width, height, rgb, heights, blocksPerPixel).shade();
    }

    // Folds one more sample into a per-channel running mean of n prior samples.
    private static int mean(int accum, int n, int sample) {
        int r = (((accum >> 16) & 0xFF) * n + ((sample >> 16) & 0xFF)) / (n + 1);
        int g = (((accum >> 8)  & 0xFF) * n + ((sample >> 8)  & 0xFF)) / (n + 1);
        int b = (((accum)       & 0xFF) * n + ((sample)       & 0xFF)) / (n + 1);
        return r << 16 | g << 8 | b;
    }
}
