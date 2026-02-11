package mc.smpessentials.chatfilter;

import dev.architectury.event.events.common.ChatEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat masking hook and SavedData accessor.
 * Registers a decorate callback that replaces offending word tokens with
 * "****". Includes multi-layer normalization for evasion resistance.
 */
public final class ChatFilter {
    // Letters, digits, and common in-word symbols: _ ' - @ $ ! . + * # % & ? ~ ^
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_'@\\-\\$!\\.\\+\\*#%&\\?~^]+");

    /**
     * Leet-speak substitution map.
     * Sources: Wikipedia "Leet" article, PimDeWitte's Java profanity filter.
     * Only single-char → single-char mappings to keep it practical.
     */
    private static final Map<Character, Character> LEET_MAP = new HashMap<>();
    static {
        LEET_MAP.put('@', 'a');
        LEET_MAP.put('4', 'a');
        LEET_MAP.put('8', 'b');
        LEET_MAP.put('3', 'e');
        LEET_MAP.put('6', 'g');
        LEET_MAP.put('9', 'g');
        LEET_MAP.put('#', 'h');
        LEET_MAP.put('!', 'i');
        LEET_MAP.put('1', 'i');
        LEET_MAP.put('0', 'o');
        LEET_MAP.put('5', 's');
        LEET_MAP.put('$', 's');
        LEET_MAP.put('7', 't');
        LEET_MAP.put('+', 't');
    }

    /**
     * Characters treated as separators inserted between letters to evade filters.
     */
    private static final String SEPARATORS = ".-_*~";

    /** Matches runs of 2+ identical characters for squashing. */
    private static final Pattern REPEATED_CHARS = Pattern.compile("(.)\\1{1,}");

    private ChatFilter() {
    }

    /**
     * Registers the chat decorate event.
     * + Server start autoload
     * Must be called once during common init.
     */
    public static void init() {
        ChatEvent.DECORATE.register(ChatFilter::onDecorate);
        LifecycleEvent.SERVER_STARTED.register(server -> {
            try {
                var res = ChatFilterConfig.mergeFromConfig(server);
                if (!res.warnings().isEmpty()) {
                    System.err.println("[QuackedSMP] Chat Filter Warnings:");
                    for (String w : res.warnings())
                        System.err.println(" - " + w);
                }
            } catch (Exception e) {
                System.err.println("[QuackedSMP] Failed to load chat filter config: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Chat decorate callback. Replaces offending tokens/phrases with "****".
     * Messages beginning with '/' are ignored.
     */
    private static void onDecorate(ServerPlayer player, ChatEvent.ChatComponent component) {
        if (component == null)
            return;

        String raw = component.get().getString();
        if (raw.startsWith("/"))
            return;

        MinecraftServer server = (player != null) ? player.getServer() : null;
        if (server == null)
            return;

        ChatFilterSavedData data = getData(server);
        String masked = maskTokens(raw, data);
        masked = maskPhrases(masked, data);

        if (!masked.equals(raw)) {
            component.set(Component.literal(masked));

            // Track strike and notify OPs
            if (player != null) {
                int strikes = ChatFilterStrikeTracker.increment(player.getUUID());
                String alert = "\u00a7c[Filter] \u00a7e" + player.getName().getString()
                        + " \u00a77(strike #" + strikes + "): \u00a7f" + raw;
                for (ServerPlayer op : server.getPlayerList().getPlayers()) {
                    if (server.getPlayerList().isOp(op.getGameProfile())) {
                        op.sendSystemMessage(Component.literal(alert));
                    }
                }
            }
        }
    }

    /**
     * Retrieves the singleton SavedData instance from the Overworld storage.
     */
    public static ChatFilterSavedData getData(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(ChatFilterSavedData.TYPE);
    }

    /**
     * Replaces any whole-word token present in the SavedData set with "****".
     * Uses a two-pass strategy: basic normalization first, then aggressive
     * normalization as fallback — this minimizes false positives.
     */
    public static String maskTokens(String message, ChatFilterSavedData data) {
        StringBuilder out = new StringBuilder(message);
        Matcher m = WORD.matcher(message);
        int delta = 0;

        while (m.find()) {
            int srcStart = m.start();
            int srcEnd = m.end();

            String token = message.substring(srcStart, srcEnd);
            String norm = normalize(token);

            // Check whitelist first — skip if whitelisted
            if (data.isWhitelisted(norm)) {
                continue;
            }

            boolean blocked = data.containsNormalized(norm);

            // Fallback: aggressive normalization for evasion detection
            if (!blocked) {
                String aggressive = normalizeAggressive(token);
                if (!aggressive.equals(norm) && !data.isWhitelisted(aggressive)) {
                    blocked = data.containsNormalized(aggressive);
                }
            }

            if (blocked) {
                int start = srcStart + delta;
                int end = srcEnd + delta;
                String replacement = "****";
                out.replace(start, end, replacement);
                delta += replacement.length() - (srcEnd - srcStart);
            }
        }
        return out.toString();
    }

    /**
     * Pass 2: masks phrases containing whitespace by substring search
     * (case-insensitive).
     */
    public static String maskPhrases(String message, ChatFilterSavedData data) {
        String out = message;
        for (Pattern pat : data.getPhrasePatterns()) {
            out = pat.matcher(out).replaceAll("****");
        }
        return out;
    }

    /**
     * Filters arbitrary text (for signs, books, anvils).
     * Returns the masked version of the input.
     */
    public static String filterText(String text, ChatFilterSavedData data) {
        String masked = maskTokens(text, data);
        return maskPhrases(masked, data);
    }

    // ---- Normalization Pipeline ----

    /**
     * Basic normalization: lowercase + NFKC (compatibility decomposition +
     * composition) + accent strip. NFKC collapses fullwidth chars (e.g. Ａ → A)
     * and other compatibility equivalences.
     */
    public static String normalize(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        String nfkc = Normalizer.normalize(lower, Normalizer.Form.NFKC);
        String stripped = nfkc.replaceAll("\\p{M}+", "");
        return stripped;
    }

    /**
     * Aggressive normalization for evasion resistance. Applies on top of basic
     * normalization:
     * 1. Leet-speak substitution (@ → a, 0 → o, 3 → e, etc.)
     * 2. Separator stripping (remove . - _ * ~ between letters)
     * 3. Repeated character squashing (fuuuck → fuck)
     */
    public static String normalizeAggressive(String s) {
        String base = normalize(s);

        // 1. Leet-speak substitution
        StringBuilder sb = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            sb.append(LEET_MAP.getOrDefault(c, c));
        }

        // 2. Strip separators
        String noSeps = sb.toString();
        StringBuilder cleaned = new StringBuilder(noSeps.length());
        for (int i = 0; i < noSeps.length(); i++) {
            char c = noSeps.charAt(i);
            if (SEPARATORS.indexOf(c) < 0) {
                cleaned.append(c);
            }
        }

        // 3. Squash repeated characters (2+ identical → 1)
        String squashed = REPEATED_CHARS.matcher(cleaned.toString()).replaceAll("$1");

        return squashed;
    }
}
