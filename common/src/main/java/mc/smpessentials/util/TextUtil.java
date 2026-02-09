package mc.smpessentials.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.regex.Pattern;

public final class TextUtil {
    private TextUtil() {
    }

    private static final Pattern AMPERSAND = Pattern.compile("(?i)&([0-9a-fk-or])");

    /**
     * Replaces & color codes with Minecraft formatting.
     * e.g. "&aHello" -> Green "Hello"
     * Handles legacy codes by converting them to Section Sign for internal parsing
     * or building components manually.
     * Since we are on Fabric/NeoForge, strict JSON components are preferred, but
     * for simple MVP,
     * we can use LiteralText with legacy char validation or a simple parser.
     *
     * Actually, the simplest way in modern MC is to use `Component.literal` and
     * then regex replace to specific styles,
     * OR just use the legacy character '§' if the server supports it (it usually
     * does for back-compat).
     */
    public static MutableComponent format(String text) {
        if (text == null)
            return Component.empty();
        // Replace & with § (Section Sign)
        String converted = AMPERSAND.matcher(text).replaceAll("§$1");
        return Component.literal(converted);
    }
}
