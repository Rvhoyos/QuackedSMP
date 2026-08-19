package mc.smpessentials.dims;

import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The generatorConfig string grammar shared by /dim create, the admin panel, and DimSavedData.
 *
 * Ether:
 *   [threshold f] [radius min max] [spacing i] [thickness min max] [height minY maxY]
 *   [structures true|false] [biomes id[:w] ...]
 *
 * Keys are optional and order independent. {@code biomes} is always last and takes the rest of the
 * string, which is why it is split off before anything else is read.
 *
 * Parsing is forgiving about values (out of range numbers are clamped) but never silent: every
 * clamp, bad number, and unknown key is reported in {@link Ether#problems()}. Commands reject those,
 * startup restore logs them and carries on with the clamped values.
 */
public final class GeneratorConfig {

    private GeneratorConfig() {}

    private static final String BIOMES_KEY = "biomes";
    private static final String STRUCTURES_KEY = "structures";

    // Ether option keys, in the order they are offered as suggestions.
    public static final List<String> ETHER_KEYS =
            List.of("threshold", "radius", "spacing", "thickness", "height", STRUCTURES_KEY, BIOMES_KEY);

    // Structures are on by default: EtherStructureFilter keeps them out of open sky, so they only
    // need turning off for a dimension that should have none at all.
    public static final boolean DEFAULT_STRUCTURES = true;

    // Number of values a key takes. Returns -1 for the greedy biomes clause, 0 for unknown keys.
    public static int etherKeyArity(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "threshold", "spacing", STRUCTURES_KEY -> 1;
            case "radius", "thickness", "height" -> 2;
            case BIOMES_KEY -> -1;
            default -> 0;
        };
    }

    // A config string split into everything before the biomes clause and the biome list itself.
    public record Split(String head, Optional<String> biomes) {}

    // Parsed ether config: shape params, the optional biome list, and anything wrong with the input.
    public record Ether(EtherIslandParams params, boolean structures, Optional<String> biomes,
                        List<String> problems) {

        // Canonical form: every value written out, so a stored config is self describing and
        // re-parsing it yields exactly these params.
        public String toConfigString() {
            StringBuilder sb = new StringBuilder();
            sb.append("threshold ").append(fmt(params.threshold()));
            sb.append(" radius ").append(fmt(params.minRadius())).append(' ').append(fmt(params.maxRadius()));
            sb.append(" spacing ").append(params.spacing());
            sb.append(" thickness ").append(fmt(params.minThickness())).append(' ').append(fmt(params.maxThickness()));
            sb.append(" height ").append(params.minCenterY()).append(' ').append(params.maxCenterY());
            sb.append(' ').append(STRUCTURES_KEY).append(' ').append(structures);
            biomes.ifPresent(b -> sb.append(' ').append(BIOMES_KEY).append(' ').append(b));
            return sb.toString();
        }
    }

    // Splits "<head> biomes <list>" on a whole-token match of "biomes".
    public static Split splitBiomes(String config) {
        String raw = config == null ? "" : config.strip();
        int idx = indexOfToken(raw, BIOMES_KEY);
        if (idx < 0) return new Split(raw, Optional.empty());
        String list = raw.substring(idx + BIOMES_KEY.length()).strip();
        return new Split(raw.substring(0, idx).strip(),
                list.isEmpty() ? Optional.empty() : Optional.of(list));
    }

    public static Ether parseEther(String config) {
        Split split = splitBiomes(config);
        List<String> problems = new ArrayList<>();
        EtherIslandParams d = EtherIslandParams.DEFAULTS;

        float threshold    = d.threshold();
        float minRadius    = d.minRadius(),    maxRadius    = d.maxRadius();
        int   spacing      = d.spacing();
        float minThickness = d.minThickness(), maxThickness = d.maxThickness();
        int   minCenterY   = d.minCenterY(),   maxCenterY   = d.maxCenterY();
        boolean structures = DEFAULT_STRUCTURES;

        String[] tokens = split.head().isBlank() ? new String[0] : split.head().split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            String key = tokens[i].toLowerCase(Locale.ROOT);
            // The biomes clause was split off above, so anything unrecognised here is a typo.
            int arity = etherKeyArity(key);
            if (arity <= 0) {
                problems.add("Unknown option '" + tokens[i] + "'. Valid options: " + String.join(", ", ETHER_KEYS));
                continue;
            }
            if (key.equals(STRUCTURES_KEY)) {
                Boolean flag = readFlag(tokens, i, problems);
                i += 1;
                if (flag != null) structures = flag;
                continue;
            }

            float[] values = readNumbers(tokens, i, key, arity, problems);
            i += arity;
            if (values == null) continue;

            switch (key) {
                case "threshold" -> threshold =
                        inRange(values[0], EtherIslandParams.THRESHOLD_MIN, EtherIslandParams.THRESHOLD_MAX, "threshold", problems);
                case "radius" -> {
                    minRadius = inRange(values[0], EtherIslandParams.RADIUS_MIN, EtherIslandParams.RADIUS_MAX, "radius min", problems);
                    maxRadius = inRange(values[1], EtherIslandParams.RADIUS_MIN, EtherIslandParams.RADIUS_MAX, "radius max", problems);
                }
                case "spacing" -> spacing = (int)
                        inRange(values[0], EtherIslandParams.SPACING_MIN, EtherIslandParams.SPACING_MAX, "spacing", problems);
                case "thickness" -> {
                    minThickness = inRange(values[0], EtherIslandParams.THICKNESS_MIN, EtherIslandParams.THICKNESS_MAX, "thickness min", problems);
                    maxThickness = inRange(values[1], EtherIslandParams.THICKNESS_MIN, EtherIslandParams.THICKNESS_MAX, "thickness max", problems);
                }
                case "height" -> {
                    minCenterY = (int) inRange(values[0], EtherIslandParams.CENTER_Y_MIN, EtherIslandParams.CENTER_Y_MAX, "height min", problems);
                    maxCenterY = (int) inRange(values[1], EtherIslandParams.CENTER_Y_MIN, EtherIslandParams.CENTER_Y_MAX, "height max", problems);
                }
                default -> { }
            }
        }

        EtherIslandParams params = new EtherIslandParams(threshold, minRadius, maxRadius, spacing,
                minThickness, maxThickness, minCenterY, maxCenterY).clamped();

        if (params.maxRadius() > params.maxReachableRadius()) {
            problems.add("radius " + fmt(params.maxRadius()) + " needs spacing of at least "
                    + EtherIslandParams.spacingFor(params.maxRadius()) + "; at spacing " + params.spacing()
                    + " islands are capped at " + fmt(params.maxReachableRadius()) + " blocks");
        }

        return new Ether(params, structures, split.biomes(), List.copyOf(problems));
    }

    // Reads the values following a key. Returns null (with a problem recorded) if any are missing
    // or not numbers, so the caller keeps its previous value for that key.
    private static float[] readNumbers(String[] tokens, int keyIndex, String key, int count,
                                       List<String> problems) {
        if (keyIndex + count >= tokens.length) {
            problems.add("'" + key + "' expects " + count + (count == 1 ? " value" : " values"));
            return null;
        }
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            String token = tokens[keyIndex + 1 + i];
            try {
                out[i] = Float.parseFloat(token);
            } catch (NumberFormatException e) {
                problems.add("'" + key + "' expects a number, got '" + token + "'");
                return null;
            }
        }
        return out;
    }

    // Reads the value after a boolean key. Returns null (with a problem recorded) if it is missing
    // or not a recognised on/off word.
    private static @Nullable Boolean readFlag(String[] tokens, int keyIndex, List<String> problems) {
        if (keyIndex + 1 >= tokens.length) {
            problems.add("'" + STRUCTURES_KEY + "' expects true or false");
            return null;
        }
        String token = tokens[keyIndex + 1].toLowerCase(Locale.ROOT);
        return switch (token) {
            case "true", "on", "yes"  -> Boolean.TRUE;
            case "false", "off", "no" -> Boolean.FALSE;
            default -> {
                problems.add("'" + STRUCTURES_KEY + "' expects true or false, got '" + tokens[keyIndex + 1] + "'");
                yield null;
            }
        };
    }

    private static float inRange(float value, float min, float max, String label, List<String> problems) {
        if (value < min || value > max) {
            problems.add(label + " must be between " + fmt(min) + " and " + fmt(max) + ", got " + fmt(value));
        }
        return Mth.clamp(value, min, max);
    }

    private static int indexOfToken(String raw, String token) {
        for (int from = 0; ; ) {
            int i = raw.indexOf(token, from);
            if (i < 0) return -1;
            int end = i + token.length();
            boolean boundedStart = i == 0 || Character.isWhitespace(raw.charAt(i - 1));
            boolean boundedEnd   = end == raw.length() || Character.isWhitespace(raw.charAt(end));
            if (boundedStart && boundedEnd) return i;
            from = end;
        }
    }

    // Whole numbers print without a trailing .0 so the canonical string stays readable.
    private static String fmt(float value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
    }
}
