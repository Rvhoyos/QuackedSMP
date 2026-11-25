package mc.smpessentials.dims;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.Interval;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Generates floating islands as 3D blobs.
 *
 * Simplex noise decides which cells of an island grid hold an island, and hashes of the cell give
 * that island its own radius, thickness, and centre Y. Every island is an ellipsoid: the horizontal
 * cone from vanilla EndIslandFunction with a vertical falloff added, so at the island's centre plane
 * the shape is unchanged while size and height are now separate parameters.
 *
 * Vanilla's version reads only x and z, which is why islands built on it run the full height of the
 * world.
 */
public final class EtherIslandDensityFunction implements DensityFunction {

    // Never encoded: this type is not registered in BuiltInRegistries.DENSITY_FUNCTION_TYPE, and
    // ether dims are rebuilt from DimSavedData at startup rather than from serialized worldgen.
    private static final MapCodec<EtherIslandDensityFunction> CODEC =
            MapCodec.unit(() -> new EtherIslandDensityFunction(0L));

    // An island's value peaks at 100 at its centre and reaches 0 at its surface, then is clamped
    // and rescaled into the density range vanilla's end islands use.
    private static final float PEAK        = 100.0f;
    private static final float VALUE_MIN   = -100.0f;
    private static final float VALUE_MAX   = 80.0f;
    private static final float SOLID_AT    = 8.0f;
    private static final float VALUE_SCALE = 128.0f;

    private final long seed;
    private final EtherIslandParams params;

    public EtherIslandDensityFunction(long seed) {
        this(seed, EtherIslandParams.DEFAULTS);
    }

    public EtherIslandDensityFunction(long seed, EtherIslandParams params) {
        this.seed = seed;
        this.params = params.clamped();
    }

    @Override
    public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        return new Sampler(Shape.of(params), createNoise(seed));
    }

    // Same seeding as 26.2, so existing ether dims keep their island layout. The offset is
    // discarded because the 26.2 2D lookup never applied one; vanilla's End does the same.
    private static SimplexNoise createNoise(long seed) {
        RandomSource random = new LegacyRandomSource(seed);
        random.consumeCount(17292);
        return new SimplexNoise(random, true);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return this;
    }

    @Override
    public Interval range() {
        return Interval.of(rescale(VALUE_MIN), rescale(VALUE_MAX));
    }

    @Override
    public @DensityFunction.Axes int domainAxes() {
        return DensityFunction.ALL_AXES;
    }

    @Override
    public MapCodec<EtherIslandDensityFunction> codec() {
        return CODEC;
    }

    private static float rescale(float value) {
        return (value - SOLID_AT) / VALUE_SCALE;
    }

    /** The island grid derived from the params, fixed for the life of a sampler. */
    private record Shape(float threshold, int spacing, float minSteepness, float steepnessRange,
                         float minThickness, float thicknessRange, float minCenterY, float centerYRange,
                         int scanCells) {

        static Shape of(EtherIslandParams params) {
            int spacing = params.spacing();
            // Radius in blocks becomes steepness: steepness = 100 / (radius / spacing), so a smaller
            // radius is a steeper cone. Min radius therefore gives max steepness.
            float minSteepness = PEAK / Math.max(params.maxRadius() / spacing, 1f);
            float steepnessRange = PEAK / Math.max(params.minRadius() / spacing, 1f) - minSteepness;
            // Only scan far enough to reach the widest island this config can produce. One grid cell
            // is 2 * spacing blocks wide.
            int needed = (int) Math.ceil(params.maxRadius() / (2f * spacing)) + 1;
            return new Shape(params.threshold(), spacing, minSteepness, steepnessRange,
                    params.minThickness(), params.maxThickness() - params.minThickness(),
                    params.minCenterY(), params.maxCenterY() - params.minCenterY(),
                    Math.min(needed, EtherIslandParams.MAX_SCAN_CELLS));
        }
    }

    // Shared by every worldgen thread (DensityFunctionCompiler caches one sampler per function), so
    // it holds no mutable state. Per-column memoising lives in a ColumnIslands owned by one call.
    private record Sampler(Shape shape, SimplexNoise islandNoise) implements DensitySampler {

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
            ColumnIslands islands = new ColumnIslands(this);
            for (int z = 0; z < volume.sizeZ(); z++) {
                int blockZ = volume.blockZ(z);
                for (int x = 0; x < volume.sizeX(); x++) {
                    int blockX = volume.blockX(x);
                    islands.moveTo(blockX, blockZ);
                    int index = volume.indexUnchecked(x, 0, z);
                    for (int y = 0; y < volume.sizeY(); y++) {
                        output.set(index + y, islands.valueAt(volume.blockY(y)));
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            ColumnIslands islands = new ColumnIslands(this);
            islands.moveTo(blockX, blockZ);
            return islands.valueAt(blockY);
        }
    }

    /**
     * The islands in the scan window around one column. The grid scan runs only when a column falls
     * in a new grid cell, so a volume walk scans once per cell instead of once per sampled position.
     */
    private static final class ColumnIslands {
        private final Shape shape;
        private final SimplexNoise islandNoise;
        private final int[] islandDx;
        private final int[] islandDz;
        private final float[] islandSteepness;
        private final float[] islandCenterY;
        private final float[] islandHalfThickness;
        private int count = -1;
        private int gridX;
        private int gridZ;
        private int fracX;
        private int fracZ;

        ColumnIslands(Sampler sampler) {
            this.shape = sampler.shape();
            this.islandNoise = sampler.islandNoise();
            int capacity = (2 * shape.scanCells() + 1) * (2 * shape.scanCells() + 1);
            this.islandDx = new int[capacity];
            this.islandDz = new int[capacity];
            this.islandSteepness = new float[capacity];
            this.islandCenterY = new float[capacity];
            this.islandHalfThickness = new float[capacity];
        }

        void moveTo(int blockX, int blockZ) {
            int x = Math.floorDiv(blockX, shape.spacing());
            int z = Math.floorDiv(blockZ, shape.spacing());
            int newGridX = Math.floorDiv(x, 2);
            int newGridZ = Math.floorDiv(z, 2);
            if (count < 0 || newGridX != gridX || newGridZ != gridZ) {
                scan(newGridX, newGridZ);
            }
            fracX = Math.floorMod(x, 2);
            fracZ = Math.floorMod(z, 2);
        }

        float valueAt(int blockY) {
            float best = VALUE_MIN;
            for (int i = 0; i < count; i++) {
                float localX = fracX - islandDx[i] * 2;
                float localZ = fracZ - islandDz[i] * 2;
                float horizontal = Mth.sqrt(localX * localX + localZ * localZ) * islandSteepness[i];
                float vertical = (blockY - islandCenterY[i]) * (PEAK / islandHalfThickness[i]);
                float value = PEAK - Mth.sqrt(horizontal * horizontal + vertical * vertical);
                best = Math.max(best, Mth.clamp(value, VALUE_MIN, VALUE_MAX));
            }
            return rescale(best);
        }

        // Collects the islands in the scan window around this cell, each with the size, height, and
        // thickness hashed from its own cell so all of that island's columns agree on them.
        private void scan(int newGridX, int newGridZ) {
            int found = 0;
            int window = shape.scanCells();
            for (int dx = -window; dx <= window; dx++) {
                for (int dz = -window; dz <= window; dz++) {
                    long cellX = newGridX + dx;
                    long cellZ = newGridZ + dz;
                    if (islandNoise.get(cellX, cellZ) >= shape.threshold()) continue;

                    islandDx[found] = dx;
                    islandDz[found] = dz;
                    islandSteepness[found] = roll(cellX, cellZ, 3439f, 147f, shape.minSteepness(), shape.steepnessRange());
                    islandCenterY[found] = roll(cellX, cellZ, 8117f, 2749f, shape.minCenterY(), shape.centerYRange());
                    islandHalfThickness[found] = roll(cellX, cellZ, 6151f, 1523f, shape.minThickness(), shape.thicknessRange()) * 0.5f;
                    found++;
                }
            }
            gridX = newGridX;
            gridZ = newGridZ;
            count = found;
        }
    }

    // Per-island value in [min, min + range), from the same style of positional hash vanilla uses
    // for island size. Each attribute uses its own primes so they do not correlate.
    private static float roll(long cellX, long cellZ, float primeX, float primeZ, float min, float range) {
        if (range <= 0f) return min;
        float hash = Mth.abs((float) cellX) * primeX + Mth.abs((float) cellZ) * primeZ;
        return hash % range + min;
    }
}
