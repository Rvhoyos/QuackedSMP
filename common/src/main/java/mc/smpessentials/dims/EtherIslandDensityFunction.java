package mc.smpessentials.dims;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import com.mojang.serialization.MapCodec;

/**
 * Generates floating islands as 3D blobs.
 *
 * Simplex noise decides which cells of an island grid hold an island, and hashes of the cell give
 * that island its own radius, thickness, and centre Y. Every island is an ellipsoid: the horizontal
 * cone from vanilla EndIslandDensityFunction with a vertical falloff added, so at the island's
 * centre plane the shape is unchanged while size and height are now separate parameters.
 *
 * Vanilla's version is a SimpleFunction reading only x and z, which is why islands built on it run
 * the full height of the world.
 */
public final class EtherIslandDensityFunction implements DensityFunction {

    // Never encoded: this type is not registered in BuiltInRegistries.DENSITY_FUNCTION_TYPE, and
    // ether dims are rebuilt from DimSavedData at startup rather than from serialized worldgen.
    public static final KeyDispatchDataCodec<EtherIslandDensityFunction> CODEC =
            KeyDispatchDataCodec.of(MapCodec.unit(new EtherIslandDensityFunction(0L)));

    // An island's value peaks at 100 at its centre and reaches 0 at its surface, then is clamped
    // and rescaled into the density range vanilla's end islands use.
    private static final float PEAK        = 100.0f;
    private static final float VALUE_MIN   = -100.0f;
    private static final float VALUE_MAX   = 80.0f;
    private static final double SOLID_AT   = 8.0;
    private static final double VALUE_SCALE = 128.0;

    private final SimplexNoise islandNoise;
    private final long seed;
    private final EtherIslandParams params;

    private final float threshold;
    private final int spacing;
    private final float minSteepness;
    private final float steepnessRange;
    private final float minThickness;
    private final float thicknessRange;
    private final float minCenterY;
    private final float centerYRange;
    private final int scanCells;

    // Islands found for the last column, so the grid scan runs once per column instead of once per
    // sampled position. NoiseChunk fills every Y for a fixed x/z before moving on, so this hits
    // nearly always. Safe because mapChildren hands each NoiseChunk its own copy, the same
    // arrangement vanilla's NoiseChunk.Cache2D relies on.
    private final int[] islandDx;
    private final int[] islandDz;
    private final float[] islandSteepness;
    private final float[] islandCenterY;
    private final float[] islandHalfThickness;
    private int memoGridX;
    private int memoGridZ;
    private int memoCount = -1;

    public EtherIslandDensityFunction(long seed) {
        this(seed, EtherIslandParams.DEFAULTS);
    }

    public EtherIslandDensityFunction(long seed, EtherIslandParams params) {
        this(seed, params.clamped(), createNoise(seed));
    }

    private EtherIslandDensityFunction(long seed, EtherIslandParams params, SimplexNoise islandNoise) {
        this.seed = seed;
        this.params = params;
        this.islandNoise = islandNoise;

        this.threshold = params.threshold();
        this.spacing = params.spacing();

        // Radius in blocks becomes steepness: steepness = 100 / (radius / spacing), so a smaller
        // radius is a steeper cone. Min radius therefore gives max steepness.
        this.minSteepness = PEAK / Math.max(params.maxRadius() / spacing, 1f);
        this.steepnessRange = PEAK / Math.max(params.minRadius() / spacing, 1f) - minSteepness;

        this.minThickness = params.minThickness();
        this.thicknessRange = params.maxThickness() - params.minThickness();
        this.minCenterY = params.minCenterY();
        this.centerYRange = params.maxCenterY() - params.minCenterY();

        // Only scan far enough to reach the widest island this config can produce. One grid cell is
        // 2 * spacing blocks wide.
        int needed = (int) Math.ceil(params.maxRadius() / (2f * spacing)) + 1;
        this.scanCells = Math.min(needed, EtherIslandParams.MAX_SCAN_CELLS);

        int capacity = (2 * scanCells + 1) * (2 * scanCells + 1);
        this.islandDx = new int[capacity];
        this.islandDz = new int[capacity];
        this.islandSteepness = new float[capacity];
        this.islandCenterY = new float[capacity];
        this.islandHalfThickness = new float[capacity];
    }

    private static SimplexNoise createNoise(long seed) {
        RandomSource random = new LegacyRandomSource(seed);
        random.consumeCount(17292);
        return new SimplexNoise(random);
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        int x = Math.floorDiv(context.blockX(), spacing);
        int z = Math.floorDiv(context.blockZ(), spacing);
        int gridX = Math.floorDiv(x, 2);
        int gridZ = Math.floorDiv(z, 2);
        if (memoCount < 0 || gridX != memoGridX || gridZ != memoGridZ) {
            scanColumn(gridX, gridZ);
        }

        int fracX = Math.floorMod(x, 2);
        int fracZ = Math.floorMod(z, 2);
        int blockY = context.blockY();

        float best = VALUE_MIN;
        for (int i = 0; i < memoCount; i++) {
            float localX = fracX - islandDx[i] * 2;
            float localZ = fracZ - islandDz[i] * 2;
            float horizontal = Mth.sqrt(localX * localX + localZ * localZ) * islandSteepness[i];
            float vertical = (blockY - islandCenterY[i]) * (PEAK / islandHalfThickness[i]);
            float value = PEAK - Mth.sqrt(horizontal * horizontal + vertical * vertical);
            best = Math.max(best, Mth.clamp(value, VALUE_MIN, VALUE_MAX));
        }
        return (best - SOLID_AT) / VALUE_SCALE;
    }

    // Collects the islands in the scan window around this column, each with the size, height, and
    // thickness hashed from its own cell so all of that island's columns agree on them.
    private void scanColumn(int gridX, int gridZ) {
        int found = 0;
        for (int dx = -scanCells; dx <= scanCells; dx++) {
            for (int dz = -scanCells; dz <= scanCells; dz++) {
                long cellX = gridX + dx;
                long cellZ = gridZ + dz;
                if (islandNoise.getValue(cellX, cellZ) >= threshold) continue;

                islandDx[found] = dx;
                islandDz[found] = dz;
                islandSteepness[found] = roll(cellX, cellZ, 3439f, 147f, minSteepness, steepnessRange);
                islandCenterY[found] = roll(cellX, cellZ, 8117f, 2749f, minCenterY, centerYRange);
                islandHalfThickness[found] = roll(cellX, cellZ, 6151f, 1523f, minThickness, thicknessRange) * 0.5f;
                found++;
            }
        }
        memoGridX = gridX;
        memoGridZ = gridZ;
        memoCount = found;
    }

    // Per-island value in [min, min + range), from the same style of positional hash vanilla uses
    // for island size. Each attribute uses its own primes so they do not correlate.
    private static float roll(long cellX, long cellZ, float primeX, float primeZ, float min, float range) {
        if (range <= 0f) return min;
        float hash = Mth.abs((float) cellX) * primeX + Mth.abs((float) cellZ) * primeZ;
        return hash % range + min;
    }

    @Override
    public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(output, this);
    }

    // Each NoiseChunk gets its own instance so the column memo is never shared between threads.
    // The noise itself is immutable and is passed along rather than rebuilt.
    @Override
    public DensityFunction mapChildren(DensityFunction.Visitor visitor) {
        return new EtherIslandDensityFunction(seed, params, islandNoise);
    }

    @Override
    public double minValue() {
        return (VALUE_MIN - SOLID_AT) / VALUE_SCALE;
    }

    @Override
    public double maxValue() {
        return (VALUE_MAX - SOLID_AT) / VALUE_SCALE;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
