package mc.smpessentials.claims;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The region tag vocabulary: the words a player may put in a [tag] prefix, and the icon each
 * one resolves to. Icon names are the file stems the panel icon export produces, so this class
 * names art without depending on the optional bluemap package.
 */
public final class RegionTags {
    private RegionTags() {
    }

    // Insertion order is the order /claim tags prints. Aliases map to a shared icon, but no two
    // distinct concepts share one, so every tag reads as its own silhouette on the map.
    private static final Map<String, String> TAG_TO_ICON = new LinkedHashMap<>();

    private static void tag(String icon, String... aliases) {
        for (String a : aliases)
            TAG_TO_ICON.put(a, icon);
    }

    static {
        tag("chest", "shop", "store", "market");
        tag("emerald", "bank", "treasury");
        tag("sword", "arena", "pvp");
        tag("sapling", "farm", "garden");
        tag("house", "base", "home");
        tag("flag", "guild", "clan");
        tag("bookshelf", "library");
        tag("furnace", "forge", "industry");
        tag("gear", "lab", "redstone");
        tag("portal", "portal", "hub");
        tag("glowstone-portal", "nether");
        tag("end-portal", "end");
        tag("dragon-head", "boss", "dragon");
        tag("hazard-stripes", "danger");
        tag("tnt", "demolition");
        tag("tombstone", "graveyard");
        tag("skull", "memorial");
        tag("megaphone", "event");
        tag("ballot", "vote");
        tag("voice-chat", "voice", "vc");
        tag("discord", "discord");
        tag("loot", "vault", "loot");
        tag("shulker-box", "storage");
        tag("xp-orb", "grinder", "xp");
        tag("wolf", "pets");
        tag("map-scroll", "explore", "atlas");
        tag("slime-ball", "slime");
        tag("medal", "champion");
        tag("duck", "duck");
        tag("crown", "capital");
    }

    public static boolean isTag(String word) {
        return word != null && TAG_TO_ICON.containsKey(word.toLowerCase(Locale.ROOT));
    }

    public static Optional<String> iconFor(String tag) {
        if (tag == null)
            return Optional.empty();
        return Optional.ofNullable(TAG_TO_ICON.get(tag.toLowerCase(Locale.ROOT)));
    }

    public static List<String> all() {
        return List.copyOf(TAG_TO_ICON.keySet());
    }

    /**
     * Closest tag to a misspelling, for the "did you mean" on an unknown tag. Only offers a
     * suggestion when the edit distance is small enough that it is likely a typo rather than a
     * different word entirely.
     */
    public static Optional<String> closest(String word) {
        if (word == null || word.isBlank())
            return Optional.empty();
        String w = word.toLowerCase(Locale.ROOT);
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String candidate : TAG_TO_ICON.keySet()) {
            int d = editDistance(w, candidate);
            if (d < bestDist) {
                bestDist = d;
                best = candidate;
            }
        }
        int limit = Math.max(1, w.length() / 3);
        return bestDist <= limit ? Optional.ofNullable(best) : Optional.empty();
    }

    // Damerau (optimal string alignment) rather than plain Levenshtein: swapping two letters is
    // the most common typo there is, and Levenshtein scores it 2, which puts "shpo" out of range
    // of "shop" and loses the suggestion exactly when it is most useful.
    private static int editDistance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++)
            d[i][0] = i;
        for (int j = 0; j <= b.length(); j++)
            d[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
