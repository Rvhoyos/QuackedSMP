package mc.smpessentials.chatfilter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
 * World-persistent chat filter data: blocked words + whitelist.
 * Stored once under the Overworld's DimensionDataStorage and read server-wide.
 */
public final class ChatFilterSavedData extends SavedData {
    private static final Codec<List<String>> STRING_LIST = Codec.list(Codec.STRING);

    /**
     * Codec for the entire data object; persists both blocked words and whitelist.
     */
    public static final Codec<ChatFilterSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            STRING_LIST.optionalFieldOf("words", List.of()).forGetter(d -> d.toList()),
            STRING_LIST.optionalFieldOf("whitelist", List.of()).forGetter(d -> d.toWhitelistList()))
            .apply(instance, ChatFilterSavedData::fromLists));

    /**
     * SavedDataType identifier; uses DataFixTypes.LEVEL for vanilla data fixers.
     */
    public static final SavedDataType<ChatFilterSavedData> TYPE = new SavedDataType<>("quackedsmp_chat_filter",
            ChatFilterSavedData::new, CODEC, DataFixTypes.LEVEL);

    private final Set<String> words = new HashSet<>();
    private final Set<String> whitelist = new HashSet<>();

    public ChatFilterSavedData() {
    }

    private static ChatFilterSavedData fromLists(List<String> wordList, List<String> whitelistList) {
        ChatFilterSavedData data = new ChatFilterSavedData();
        for (String s : wordList) {
            if (s != null && !s.isEmpty())
                data.words.add(ChatFilter.normalize(s));
        }
        for (String s : whitelistList) {
            if (s != null && !s.isEmpty())
                data.whitelist.add(ChatFilter.normalize(s));
        }
        return data;
    }

    private List<String> toList() {
        return new ArrayList<>(new TreeSet<>(words));
    }

    private List<String> toWhitelistList() {
        return new ArrayList<>(new TreeSet<>(whitelist));
    }

    // ---- Blocked words ----

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
     * Returns an immutable snapshot of blocked words, sorted for readability.
     */
    public Set<String> snapshot() {
        return Collections.unmodifiableSet(new TreeSet<>(words));
    }

    // ---- Whitelist (Scunthorpe problem prevention) ----

    /**
     * Adds a word to the whitelist (normalized).
     * Whitelisted words are never masked, even if they contain blocked substrings.
     * 
     * @return true if the set changed
     */
    public boolean addWhitelist(String rawWord) {
        boolean changed = whitelist.add(ChatFilter.normalize(rawWord));
        if (changed)
            setDirty();
        return changed;
    }

    /**
     * Removes a word from the whitelist.
     * 
     * @return true if the set changed
     */
    public boolean removeWhitelist(String rawWord) {
        boolean changed = whitelist.remove(ChatFilter.normalize(rawWord));
        if (changed)
            setDirty();
        return changed;
    }

    /**
     * Checks if a normalized token is whitelisted.
     */
    public boolean isWhitelisted(String normalizedToken) {
        return whitelist.contains(normalizedToken);
    }

    /**
     * Returns an immutable snapshot of whitelisted words, sorted.
     */
    public Set<String> whitelistSnapshot() {
        return Collections.unmodifiableSet(new TreeSet<>(whitelist));
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
