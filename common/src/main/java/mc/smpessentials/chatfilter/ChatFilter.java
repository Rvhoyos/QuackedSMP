package mc.smpessentials.chatfilter;

import dev.architectury.event.events.common.ChatEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat masking hook and SavedData accessor.
 * Registers a decorate callback that replaces offending word tokens with
 * "****".
 */
public final class ChatFilter {
    // Letters, digits, and common in-word symbols: _ ' - @ $ ! . + * # % & ? ~ ^
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_'@\\-\\$!\\.\\+\\*#%&\\?~^]+");

    private ChatFilter() {
    }

    /**
     * Registers the chat decorate event.
     * + Server start autoload
     * Must be called once during common init.
     */
    public static void init() {
        ChatEvent.DECORATE.register(ChatFilter::onDecorate);
        LifecycleEvent.SERVER_STARTED.register(server -> ChatFilterConfig.mergeFromConfig(server));
    }
    /*
     * Chat decorate callback that inspects the raw message and replaces any
     * word-token
     * present in the persisted filter set with asterisks. Messages beginning with
     * '/'
     * are ignored. Operates server-side and is a no-op if the server instance is
     * null.
     */

    private static void onDecorate(ServerPlayer player, ChatEvent.ChatComponent component) {
        if (component == null)
            return;

        // Do not process commands
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
     * Matching is case-insensitive and accent-insensitive via normalization.
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
            if (data.containsNormalized(norm)) {
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
     * Uses lowercase-only normalization to maintain index alignment with the
     * original string.
     */
    public static String maskPhrases(String message, ChatFilterSavedData data) {
        String out = message;

        for (java.util.regex.Pattern pat : data.getPhrasePatterns()) {
            out = pat.matcher(out).replaceAll("****");
        }
        return out;
    }

    /**
     * Lowercase + NFD accent strip; suitable for case/accent-insensitive equality.
     */
    public static String normalize(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        String nfd = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}+", "");
    }
}
