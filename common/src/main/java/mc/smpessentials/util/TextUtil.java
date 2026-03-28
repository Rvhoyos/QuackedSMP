package mc.smpessentials.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.regex.Pattern;

public final class TextUtil {
    private TextUtil() {
    }

    private static final Pattern AMPERSAND = Pattern.compile("(?i)&([0-9a-fk-or])");

    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");

    // Replaces & color codes and parses Markdown-style links: [Text](URL).
    public static MutableComponent format(String text) {
        if (text == null)
            return Component.empty();

        MutableComponent root = Component.empty();
        java.util.regex.Matcher matcher = LINK_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            // Append text before the link
            if (matcher.start() > lastEnd) {
                root.append(formatLegacy(text.substring(lastEnd, matcher.start())));
            }

            // Create the link component
            String label = matcher.group(1);
            String url = matcher.group(2);

            MutableComponent linkComponent = formatLegacy(label)
                    .withStyle(style -> style
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(java.net.URI.create(url)))
                            .withHoverEvent(
                                    new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(url))));

            root.append(linkComponent);
            lastEnd = matcher.end();
        }

        // Append remaining text
        if (lastEnd < text.length()) {
            root.append(formatLegacy(text.substring(lastEnd)));
        }

        return root;
    }

    private static MutableComponent formatLegacy(String text) {
        // Replace & with § (Section Sign)
        return Component.literal(AMPERSAND.matcher(text).replaceAll("§$1"));
    }
}
