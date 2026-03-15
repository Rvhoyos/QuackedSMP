package mc.smpessentials.skills;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds and opens a Written Book GUI showing skill progress.
 * 4 pages, one per category.
 */
public final class SkillBookGui {

    private SkillBookGui() {
    }

    public static void open(ServerPlayer player) {
        ServerLevel sl = (ServerLevel) player.level();
        SkillData data = SkillData.get(sl);
        Map<SkillType, Double> xpMap = data.getTypedXpMap(player.getUUID());

        List<Filterable<Component>> pages = new ArrayList<>();

        // One page per category
        for (SkillType.Category cat : SkillType.Category.values()) {
            Component page = buildCategoryPage(cat, xpMap, data, player);
            pages.add(Filterable.passThrough(page));
        }

        // Build the book content (already resolved = true)
        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough("Skills"),
                "QuackedSMP",
                0,
                pages,
                true);

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);

        // Open the book for the player (server-side)
        player.openItemGui(book, net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    private static Component buildCategoryPage(SkillType.Category cat, Map<SkillType, Double> xpMap,
            SkillData data, ServerPlayer player) {
        int parentLevel = SkillManager.parentLevel(cat, xpMap);

        MutableComponent page = Component.empty();

        // Header
        page.append(Component.literal(cat.symbol() + " " + cat.displayName())
                .withStyle(Style.EMPTY.withBold(true).withColor(categoryColor(cat))));
        page.append(Component.literal("\n"));
        page.append(Component.literal("Parent Lv." + parentLevel)
                .withStyle(Style.EMPTY.withColor(0xAAAAAA)));
        page.append(Component.literal("\n\n"));

        // Sub-skills
        for (SkillType skill : cat.skills()) {
            double xp = xpMap.getOrDefault(skill, 0.0);
            int level = SkillManager.levelFromXp(xp);
            double progress = SkillManager.progressFraction(xp);

            // Skill name (clickable for details)
            MutableComponent skillLine = Component.literal(
                    "\u2726 " + capitalize(skill.name()))
                    .withStyle(Style.EMPTY
                            .withColor(categoryColor(cat))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Component.literal("Click for details")))
                            .withClickEvent(new ClickEvent.RunCommand(
                                    "/skills " + skill.name().toLowerCase())));
            page.append(skillLine);
            page.append(Component.literal("\n"));

            // Level + progress bar
            String bar = textProgressBar(progress, 8);
            page.append(Component.literal(" Lv." + level + " " + bar)
                    .withStyle(Style.EMPTY.withColor(0x999999)));
            page.append(Component.literal("\n"));

            // Cooldown status
            long cd = data.getCooldownRemaining(player.getUUID(), skill);
            if (cd > 0) {
                page.append(Component.literal(" \u23F1 " + formatTime(cd))
                        .withStyle(Style.EMPTY.withColor(0xFF5555)));
            } else if (level >= 10) {
                page.append(Component.literal(" \u2714 Ready")
                        .withStyle(Style.EMPTY.withColor(0x55FF55)));
            }
            page.append(Component.literal("\n"));
        }

        return page;
    }

    /** Simple text progress bar (books don't support section signs). */
    private static String textProgressBar(double fraction, int width) {
        int filled = (int) Math.round(fraction * width);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < filled; i++)
            sb.append('\u2588'); // █
        for (int i = filled; i < width; i++)
            sb.append('\u2591'); // ░
        sb.append("]");
        return sb.toString();
    }

    private static int categoryColor(SkillType.Category cat) {
        return switch (cat) {
            case INDUSTRIAL -> 0xFFAA00; // Gold
            case NATURE -> 0x55FF55; // Green
            case COMBAT -> 0xFF5555; // Red
            case KNOWLEDGE -> 0xFF55FF; // Pink
        };
    }

    private static String capitalize(String name) {
        if (name == null || name.isEmpty())
            return name;
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    private static String formatTime(long seconds) {
        if (seconds >= 3600)
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        if (seconds >= 60)
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }
}
