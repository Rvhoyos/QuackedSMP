package mc.smpessentials.chatfilter;

import com.mojang.serialization.Codec;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * World-persistent chat filter word set backed by SavedDataType (MC 1.21+).
 * Stored once under the Overworld's DimensionDataStorage and read server-wide.
 */
public final class ChatFilterSavedData extends SavedData {
    private static final Codec<List<String>> WORDS_CODEC = Codec.list(Codec.STRING);

    /**
     * Codec for the entire data object; persists a simple sorted list of normalized
     * words.
     */
    public static final Codec<ChatFilterSavedData> CODEC = WORDS_CODEC.xmap(ChatFilterSavedData::fromList,
            ChatFilterSavedData::toList);
    /**
     * SavedDataType identifier; uses DataFixTypes.LEVEL for vanilla data fixers.
     * Constructor order: id, supplier-ctor, codec, data-fix type.
     */
    public static final SavedDataType<ChatFilterSavedData> TYPE = new SavedDataType<>("quackedsmp_chat_filter",
            ChatFilterSavedData::new, CODEC, DataFixTypes.LEVEL);

    private final Set<String> words = new HashSet<>();

    public ChatFilterSavedData() {
    }

    private static ChatFilterSavedData fromList(List<String> list) {
        ChatFilterSavedData data = new ChatFilterSavedData();
        for (String s : list) {
            if (s != null && !s.isEmpty())
                data.words.add(ChatFilter.normalize(s));
        }
        return data;
    }

    private List<String> toList() {
        // Persist deterministically for stable diffs
        return new ArrayList<>(new TreeSet<>(words));
    }

    /**
     * Adds a word (normalized) to the filter set.
     * 
     * @return true if the set changed
     */
    public boolean add(String rawWord) {
        boolean changed = words.add(ChatFilter.normalize(rawWord));
        if (changed) {
            setDirty();
            invalidateCache();
        }
        return changed;
    }

    /**
     * Removes a word (normalized) from the filter set.
     * 
     * @return true if the set changed
     */
    public boolean remove(String rawWord) {
        boolean changed = words.remove(ChatFilter.normalize(rawWord));
        if (changed) {
            setDirty();
            invalidateCache();
        }
        return changed;
    }

    public boolean containsNormalized(String normalizedToken) {
        return words.contains(normalizedToken);
    }

    /**
     * Returns an immutable snapshot sorted for readability.
     */
    public Set<String> snapshot() {
        return Collections.unmodifiableSet(new TreeSet<>(words));
    }

    // --- Optimization: Cached Patterns ---

    private transient List<java.util.regex.Pattern> phrasePatterns;

    public List<java.util.regex.Pattern> getPhrasePatterns() {
        if (phrasePatterns == null)
            rebuildPatterns();
        return phrasePatterns;
    }

    private void rebuildPatterns() {
        List<java.util.regex.Pattern> list = new ArrayList<>();
        for (String entry : words) {
            // Only compile patterns for phrases that maskTokens() won't catch
            // (whitespace/non-alphanumeric).
            // Logic mirrored from ChatFilter.maskPhrases
            boolean needs = false;
            for (int i = 0; i < entry.length(); i++) {
                char c = entry.charAt(i);
                if (Character.isWhitespace(c) || !Character.isLetterOrDigit(c)) {
                    needs = true;
                    break;
                }
            }
            if (!needs)
                continue;

            try {
                list.add(java.util.regex.Pattern.compile(
                        java.util.regex.Pattern.quote(entry),
                        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE));
            } catch (Exception ignored) {
            }
        }
        this.phrasePatterns = Collections.unmodifiableList(list);
    }

    private void invalidateCache() {
        this.phrasePatterns = null;
    }
}
