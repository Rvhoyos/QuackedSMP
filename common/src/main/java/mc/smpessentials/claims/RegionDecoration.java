package mc.smpessentials.claims;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A region name carries two kinds of decoration that must never reach name resolution: an
 * optional leading [tag] and & colour codes. Both are parsed and stripped here, in one place,
 * so /visit, tab completion and name uniqueness all agree on what a name actually is.
 *
 * @param tag   the recognized tag word, if the name started with one
 * @param plain the name with tag and colour codes removed, what /visit and uniqueness use
 * @param body  the name with the tag removed but colour codes kept, what gets rendered
 */
public record RegionDecoration(Optional<String> tag, String plain, String body) {

    // Matches a leading [word] token. Whether the word is a real tag is decided separately, so
    // an unknown one can be reported instead of silently swallowed.
    private static final Pattern LEADING_TAG = Pattern.compile("^\\[([A-Za-z0-9_-]+)\\]\\s*");

    public static RegionDecoration parse(String raw) {
        if (raw == null)
            return new RegionDecoration(Optional.empty(), "", "");

        String rest = raw.trim();
        Optional<String> tag = Optional.empty();

        Matcher m = LEADING_TAG.matcher(rest);
        if (m.find() && RegionTags.isTag(m.group(1))) {
            tag = Optional.of(m.group(1).toLowerCase(Locale.ROOT));
            rest = rest.substring(m.end());
        }

        String body = rest.trim();
        return new RegionDecoration(tag, stripColors(body), body);
    }

    /** Tag and colour removed. The canonical form a name is matched by. */
    public static String strip(String raw) {
        return parse(raw).plain();
    }

    public static String stripColors(String text) {
        return text == null ? "" : mc.smpessentials.util.TextUtil.AMPERSAND.matcher(text).replaceAll("");
    }

    /**
     * The unrecognized [word] a name starts with, if any. Used to reject an obvious tag typo at
     * command time rather than storing a name whose brackets silently mean nothing.
     */
    public static Optional<String> unknownTag(String raw) {
        if (raw == null)
            return Optional.empty();
        Matcher m = LEADING_TAG.matcher(raw.trim());
        if (m.find() && !RegionTags.isTag(m.group(1)))
            return Optional.of(m.group(1));
        return Optional.empty();
    }
}
