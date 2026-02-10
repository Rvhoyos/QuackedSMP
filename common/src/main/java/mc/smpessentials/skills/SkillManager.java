package mc.smpessentials.skills;

import mc.smpessentials.config.SmpConfig;

/**
 * Static helper for XP math, level calculation, and perk scaling.
 * Max level is always 100. XP curve is exponential.
 */
public final class SkillManager {

    public static final int MAX_LEVEL = 100;
    private static final double BASE_XP = 100.0;

    private SkillManager() {
    }

    /** XP required to go from (level-1) to level. */
    public static long xpForLevel(int level) {
        if (level <= 0)
            return 0;

        // Piecewise XP Curve:
        // Lv 1-10: Easy early game (flat 50 XP per level)
        if (level <= 10) {
            return 50;
        }

        // Lv 11+: Standard exponential curve
        // Formula: 100 * (level^1.5)
        return (long) Math.floor(BASE_XP * Math.pow(level, SmpConfig.SKILL_XP_EXPONENT));
    }

    /** Total cumulative XP needed to reach a given level. */
    public static long totalXpForLevel(int level) {
        long total = 0;
        for (int i = 1; i <= level; i++) {
            total += xpForLevel(i);
        }
        return total;
    }

    /** Calculate a player's level from their total XP. */
    public static int levelFromXp(double totalXp) {
        long cumulative = 0;
        for (int lvl = 1; lvl <= MAX_LEVEL; lvl++) {
            cumulative += xpForLevel(lvl);
            if (totalXp < cumulative)
                return lvl - 1;
        }
        return MAX_LEVEL;
    }

    /** XP progress within the current level as a fraction 0.0-1.0. */
    public static double progressFraction(double totalXp) {
        int level = levelFromXp(totalXp);
        if (level >= MAX_LEVEL)
            return 1.0;
        long base = totalXpForLevel(level);
        long needed = xpForLevel(level + 1);
        if (needed == 0)
            return 1.0;
        return Math.min(1.0, (totalXp - base) / (double) needed);
    }

    /**
     * Calculate the parent category level.
     * Parent level = average of children's levels.
     */
    public static int parentLevel(SkillType.Category cat, java.util.Map<SkillType, Double> xpMap) {
        SkillType[] children = cat.skills();
        int sum = 0;
        for (SkillType s : children) {
            sum += levelFromXp(xpMap.getOrDefault(s, 0.0));
        }
        return sum / children.length;
    }

    /**
     * Perk scaling: returns a value between 0 and maxCap proportional to level.
     * At level 100, returns maxCap. At level 0, returns 0.
     */
    public static double perkScale(int level, double maxCap) {
        return maxCap * ((double) level / MAX_LEVEL);
    }

    /** Build a text progress bar: ████░░░░░░ */
    public static String progressBar(double fraction, int width) {
        int filled = (int) Math.round(fraction * width);
        StringBuilder sb = new StringBuilder();
        sb.append("\u00a7a"); // green
        for (int i = 0; i < filled; i++)
            sb.append('\u2588'); // █
        sb.append("\u00a78"); // dark gray
        for (int i = filled; i < width; i++)
            sb.append('\u2591'); // ░
        return sb.toString();
    }
}
