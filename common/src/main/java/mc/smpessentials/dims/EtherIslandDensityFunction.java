package mc.smpessentials.dims;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import com.mojang.serialization.MapCodec;

// Generates isolated floating islands using simplex noise to place cone-shaped landmasses.
// Adapted from vanilla EndIslandDensityFunction but without the central island or exclusion zone,
// producing evenly distributed islands everywhere.
public final class EtherIslandDensityFunction implements DensityFunction.SimpleFunction {

    public static final KeyDispatchDataCodec<EtherIslandDensityFunction> CODEC =
            KeyDispatchDataCodec.of(MapCodec.unit(new EtherIslandDensityFunction(0L)));

    // Defaults matching a good ether feel — tuned to be more separated than FLOATING_ISLANDS
    public static final float DEFAULT_THRESHOLD = -0.85f;
    public static final float DEFAULT_MIN_RADIUS = 40f;
    public static final float DEFAULT_MAX_RADIUS = 90f;
    public static final int DEFAULT_SPACING = 8;

    private final SimplexNoise islandNoise;
    private final float threshold;
    private final float minSteepness;
    private final float steepnessRange;
    private final int spacing;

    public EtherIslandDensityFunction(long seed) {
        this(seed, DEFAULT_THRESHOLD, DEFAULT_MIN_RADIUS, DEFAULT_MAX_RADIUS, DEFAULT_SPACING);
    }

    public EtherIslandDensityFunction(long seed, float threshold, float minRadius, float maxRadius, int spacing) {
        RandomSource random = new LegacyRandomSource(seed);
        random.consumeCount(17292);
        this.islandNoise = new SimplexNoise(random);
        this.threshold = threshold;
        // Convert radius (blocks) to steepness: steepness = 100 / (radius / spacing)
        // Higher steepness = smaller island, so min radius -> max steepness
        float maxSteep = 100f / Math.max(minRadius / spacing, 1f);
        float minSteep = 100f / Math.max(maxRadius / spacing, 1f);
        this.minSteepness = minSteep;
        this.steepnessRange = maxSteep - minSteep;
        this.spacing = spacing;
    }

    private float getHeightValue(int x, int z) {
        int gridX = x / 2;
        int gridZ = z / 2;
        int fracX = x % 2;
        int fracZ = z % 2;

        // No central island -- start with minimum so only noise-placed islands appear
        float height = -100f;

        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                long sampleX = gridX + dx;
                long sampleZ = gridZ + dz;

                // No exclusion zone -- islands can spawn everywhere
                if (islandNoise.getValue(sampleX, sampleZ) < threshold) {
                    // Pseudo-random steepness per island position (same hash as vanilla)
                    float hash = Mth.abs((float) sampleX) * 3439f + Mth.abs((float) sampleZ) * 147f;
                    float steepness = steepnessRange > 0f
                            ? (hash % steepnessRange) + minSteepness
                            : minSteepness;

                    float localX = fracX - dx * 2;
                    float localZ = fracZ - dz * 2;
                    float islandHeight = 100f - Mth.sqrt(localX * localX + localZ * localZ) * steepness;
                    islandHeight = Mth.clamp(islandHeight, -100f, 80f);
                    height = Math.max(height, islandHeight);
                }
            }
        }

        return height;
    }

    @Override
    public double compute(DensityFunction.FunctionContext ctx) {
        return (getHeightValue(ctx.blockX() / spacing, ctx.blockZ() / spacing) - 8.0) / 128.0;
    }

    @Override
    public double minValue() {
        return -0.84375;
    }

    @Override
    public double maxValue() {
        return 0.5625;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
