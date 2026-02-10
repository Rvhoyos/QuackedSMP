package mc.smpessentials.skills;

/**
 * All 12 sub-skills grouped under 4 parent categories.
 * Parent level = average of its children's levels.
 */
public enum SkillType {
    // ---- Industrial ----
    MINING(Category.INDUSTRIAL),
    EXCAVATION(Category.INDUSTRIAL),
    WOODCUTTING(Category.INDUSTRIAL),

    // ---- Nature ----
    FARMING(Category.NATURE),
    FISHING(Category.NATURE),
    AGILITY(Category.NATURE),

    // ---- Combat ----
    MELEE(Category.COMBAT),
    ARCHERY(Category.COMBAT),
    DEFENSE(Category.COMBAT),

    // ---- Knowledge ----
    ENCHANTING(Category.KNOWLEDGE),
    ALCHEMY(Category.KNOWLEDGE),
    TRADING(Category.KNOWLEDGE);

    private final Category category;

    SkillType(Category category) {
        this.category = category;
    }

    public Category category() {
        return category;
    }

    public enum Category {
        INDUSTRIAL("Industrial", "\u00a76", "\u26cf"), // ⛏
        NATURE("Nature", "\u00a7a", "\u2764"), // ❤
        COMBAT("Combat", "\u00a7c", "\u2694"), // ⚔
        KNOWLEDGE("Knowledge", "\u00a7d", "\u2726"); // ✦

        private final String displayName;
        private final String color; // §-code
        private final String symbol;

        Category(String displayName, String color, String symbol) {
            this.displayName = displayName;
            this.color = color;
            this.symbol = symbol;
        }

        public String displayName() {
            return displayName;
        }

        public String color() {
            return color;
        }

        public String symbol() {
            return symbol;
        }

        public SkillType[] skills() {
            return java.util.Arrays.stream(SkillType.values())
                    .filter(s -> s.category == this)
                    .toArray(SkillType[]::new);
        }
    }
}
