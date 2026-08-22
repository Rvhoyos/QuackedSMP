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
 *
 * Every MIN and MAX below is a saturation or break point of the code that reads it, not a taste
 * call, and each one carries the reason it sits where it does. The panel gets these same values
 * from /api/admin/dims/etherparams, so widening a bound here widens the control there. SPACING_MAX
 * is the one exception and says so.
 */
public record EtherIslandParams(float threshold,
                                float minRadius, float maxRadius,
                                int spacing,
                                float minThickness, float maxThickness,
                                int minCenterY, int maxCenterY) {

    // How far the density function scans for islands, in island grid cells. One cell is
    // 2 * spacing blocks wide, so this bounds the widest island that can be found.
    public static final int MAX_SCAN_CELLS = 16;

    // Below 1 the grid collapses: spacing divides block coordinates in EtherIslandDensityFunction.
    public static final int   SPACING_MIN   = 1;
    // No break point behind this one. Larger spacing is cheaper, not dearer, since the scan window
    // shrinks as cells grow and is capped at MAX_SCAN_CELLS regardless.
    public static final int   SPACING_MAX   = 32;

    // Lowest value the 2D simplex can return, so nothing passes the cell test and no island exists.
    public static final float THRESHOLD_MIN = -1.0f;
    // Saturation point: at 1.0 every cell in the scan window is an island, so nothing above it can
    // change the terrain. Cost rises smoothly on the way there, with no cliff, because compute()
    // walks every island the column scan found. Measured at default radius and spacing, one chunk
    // of columns over Y 0 to 255, against the default of -0.85:
    //   -0.85 -> 6 islands/column, 1.0x     -0.6 -> 15, 2.1x      -0.4 -> 53, 7.8x
    //   -0.2  -> 77, 11.6x                   0.0 -> 109, 16.2x     1.0 -> 225, 31.6x
    // So this is a slider that can make chunk generation 30 times dearer. That is the operator's
    // call to make, not a reason to hide the top of the range.
    public static final float THRESHOLD_MAX = 1.0f;

    // Radius only matters down to the spacing value: below it, the max(radius / spacing, 1) clamp
    // in EtherIslandDensityFunction saturates and every smaller radius generates the same island.
    // One block is the smallest value that is not already saturated at every spacing.
    public static final float RADIUS_MIN    = 1.0f;
    // The widest island any legal spacing can reach, since the scan window is MAX_SCAN_CELLS cells
    // of 2 * spacing blocks each. Anything above this is unreachable at every spacing, and
    // GeneratorConfig warns per-spacing when a radius exceeds what the chosen spacing can reach.
    public static final float RADIUS_MAX    = 2f * SPACING_MAX * MAX_SCAN_CELLS;

    // Thickness feeds PEAK / halfThickness, which divides by zero at 0. One block is the thinnest
    // island that can exist.
    public static final float THICKNESS_MIN = 1.0f;
    // An island cannot be taller than the band it generates in.
    public static final float THICKNESS_MAX = 256.0f;

    // The FLOATING_ISLANDS noise range ether dims generate in.
    public static final int   CENTER_Y_MIN  = 0;
    public static final int   CENTER_Y_MAX  = 256;

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
