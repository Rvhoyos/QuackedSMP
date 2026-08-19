package mc.smpessentials.dims;

import net.minecraft.util.Mth;

/**
 * Island shape parameters for ether dimensions.
 *
 * Single source of truth for defaults and valid ranges. The keyword grammar that carries these
 * values in and out of a generatorConfig string lives in {@link GeneratorConfig}, and the density
 * function that turns them into terrain is {@link EtherIslandDensityFunction}.
 *
 * Radii and thicknesses are in blocks. Centre Y values are block heights inside the
 * FLOATING_ISLANDS noise range (0 to 256), which is the range ether dims generate in regardless of
 * the wider build height their dimension type advertises.
 */
public record EtherIslandParams(float threshold,
                                float minRadius, float maxRadius,
                                int spacing,
                                float minThickness, float maxThickness,
                                int minCenterY, int maxCenterY) {

    public static final float THRESHOLD_MIN = -1.0f;
    public static final float THRESHOLD_MAX = 0.0f;
    public static final float RADIUS_MIN    = 5.0f;
    public static final float RADIUS_MAX    = 500.0f;
    public static final int   SPACING_MIN   = 1;
    public static final int   SPACING_MAX   = 32;
    public static final float THICKNESS_MIN = 4.0f;
    public static final float THICKNESS_MAX = 256.0f;
    public static final int   CENTER_Y_MIN  = 0;
    public static final int   CENTER_Y_MAX  = 256;

    // How far the density function scans for islands, in island grid cells. One cell is
    // 2 * spacing blocks wide, so this bounds the widest island that can be found.
    public static final int MAX_SCAN_CELLS = 16;

    public static final EtherIslandParams DEFAULTS =
            new EtherIslandParams(-0.85f, 40f, 90f, 8, 12f, 40f, CENTER_Y_MIN, CENTER_Y_MAX);

    // Clamps every value into range and orders each min/max pair.
    public EtherIslandParams clamped() {
        float rMin = Mth.clamp(minRadius,    RADIUS_MIN,    RADIUS_MAX);
        float rMax = Mth.clamp(maxRadius,    RADIUS_MIN,    RADIUS_MAX);
        float tMin = Mth.clamp(minThickness, THICKNESS_MIN, THICKNESS_MAX);
        float tMax = Mth.clamp(maxThickness, THICKNESS_MIN, THICKNESS_MAX);
        int   yMin = Mth.clamp(minCenterY,   CENTER_Y_MIN,  CENTER_Y_MAX);
        int   yMax = Mth.clamp(maxCenterY,   CENTER_Y_MIN,  CENTER_Y_MAX);
        return new EtherIslandParams(
                Mth.clamp(threshold, THRESHOLD_MIN, THRESHOLD_MAX),
                Math.min(rMin, rMax), Math.max(rMin, rMax),
                Mth.clamp(spacing, SPACING_MIN, SPACING_MAX),
                Math.min(tMin, tMax), Math.max(tMin, tMax),
                Math.min(yMin, yMax), Math.max(yMin, yMax));
    }

    // Largest island radius the scan window can reach at this spacing. Islands asked to be wider
    // than this are truncated, so callers warn instead of generating something the user did not ask for.
    public float maxReachableRadius() {
        return 2f * spacing * MAX_SCAN_CELLS;
    }

    // Smallest spacing that can reach the given radius.
    public static int spacingFor(float radius) {
        return (int) Math.ceil(radius / (2f * MAX_SCAN_CELLS));
    }
}
