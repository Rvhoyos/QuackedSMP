package mc.smpessentials.bluemap;

import mc.smpessentials.util.TextUtil;

import java.util.Locale;
import java.util.regex.Matcher;

/**
 * Builds the HTML that goes into marker popups and labels.
 *
 * BlueMap's detail and html fields are injected into the web app verbatim, and much of what we
 * put in them is player authored (region names, player names, shop items). Everything must be
 * escaped here, and colour codes translated only after escaping, so markup in a name can never
 * become markup on the map.
 */
public final class MarkerHtml {
    private MarkerHtml() {
    }

    public static String escape(String text) {
        if (text == null)
            return "";
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Renders legacy colour codes as spans. The raw text is split on the codes first and every
     * literal slice is escaped on its way out, so the only markup in the result is the spans
     * this method wrote itself.
     */
    public static String colored(String text) {
        if (text == null || text.isEmpty())
            return "";

        StringBuilder out = new StringBuilder();
        Matcher m = TextUtil.AMPERSAND.matcher(text);
        int last = 0;
        int openSpans = 0;

        while (m.find()) {
            out.append(escape(text.substring(last, m.start())));
            String css = styleFor(Character.toLowerCase(m.group(1).charAt(0)));
            if (css == null) {
                while (openSpans-- > 0)
                    out.append("</span>");
                openSpans = 0;
            } else {
                out.append("<span style=\"").append(css).append("\">");
                openSpans++;
            }
            last = m.end();
        }

        out.append(escape(text.substring(last)));
        while (openSpans-- > 0)
            out.append("</span>");
        return out.toString();
    }

    private static String styleFor(char code) {
        return switch (code) {
            case '0' -> "color:#000000";
            case '1' -> "color:#0000AA";
            case '2' -> "color:#00AA00";
            case '3' -> "color:#00AAAA";
            case '4' -> "color:#AA0000";
            case '5' -> "color:#AA00AA";
            case '6' -> "color:#FFAA00";
            case '7' -> "color:#AAAAAA";
            case '8' -> "color:#555555";
            case '9' -> "color:#5555FF";
            case 'a' -> "color:#55FF55";
            case 'b' -> "color:#55FFFF";
            case 'c' -> "color:#FF5555";
            case 'd' -> "color:#FF55FF";
            case 'e' -> "color:#FFFF55";
            case 'f' -> "color:#FFFFFF";
            case 'l' -> "font-weight:bold";
            case 'm' -> "text-decoration:line-through";
            case 'n' -> "text-decoration:underline";
            case 'o' -> "font-style:italic";
            // k (obfuscated) has no sensible web equivalent, so it just styles nothing.
            case 'k' -> "opacity:0.75";
            default -> null; // r, reset
        };
    }

    public static String itemName(String itemId) {
        if (itemId == null || itemId.isBlank())
            return "";
        String path = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty())
                continue;
            if (!out.isEmpty())
                out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return escape(out.toString());
    }
}
