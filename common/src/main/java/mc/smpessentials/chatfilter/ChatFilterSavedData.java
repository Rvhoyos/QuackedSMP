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
            STRING_LIST.optionalFieldOf("whitelist", List.of()).forGetter(d -> d.toWhitelistList()),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("mutes", java.util.Map.of())
                    .forGetter(d -> d.toMutesMap()),
            Codec.unboundedMap(Codec.STRING, ViolationData.CODEC).optionalFieldOf("violations", java.util.Map.of())
                    .forGetter(d -> d.toViolationsMap()))
            .apply(instance, ChatFilterSavedData::fromLists));

    /**
     * SavedDataType identifier; uses DataFixTypes.LEVEL for vanilla data fixers.
     */
    public static final SavedDataType<ChatFilterSavedData> TYPE = new SavedDataType<>("quackedsmp_chat_filter",
            ChatFilterSavedData::new, CODEC, DataFixTypes.LEVEL);

    private final Set<String> words = new HashSet<>();
    private final Set<String> whitelist = new HashSet<>();
    private final java.util.Map<java.util.UUID, Long> mutes = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, ViolationData> violations = new java.util.HashMap<>();

    public ChatFilterSavedData() {
    }

    private static ChatFilterSavedData fromLists(List<String> wordList, List<String> whitelistList,
            java.util.Map<String, Long> mutesMap, java.util.Map<String, ViolationData> violationsMap) {
        ChatFilterSavedData data = new ChatFilterSavedData();
        for (String s : wordList) {
            if (s != null && !s.isEmpty())
                data.words.add(ChatFilter.normalize(s));
        }
        for (String s : whitelistList) {
            if (s != null && !s.isEmpty())
                data.whitelist.add(ChatFilter.normalize(s));
        }
        for (var entry : mutesMap.entrySet()) {
            try {
                data.mutes.put(java.util.UUID.fromString(entry.getKey()), entry.getValue());
            } catch (Exception ignored) {
            }
        }
        for (var entry : violationsMap.entrySet()) {
            try {
                data.violations.put(java.util.UUID.fromString(entry.getKey()), entry.getValue());
            } catch (Exception ignored) {
            }
        }
        return data;
    }

    private List<String> toList() {
        return new ArrayList<>(new TreeSet<>(words));
    }

    private List<String> toWhitelistList() {
        return new ArrayList<>(new TreeSet<>(whitelist));
    }

    private java.util.Map<String, Long> toMutesMap() {
        java.util.Map<String, Long> map = new java.util.HashMap<>();
        for (var entry : mutes.entrySet()) {
            map.put(entry.getKey().toString(), entry.getValue());
        }
        return map;
    }

    private java.util.Map<String, ViolationData> toViolationsMap() {
        java.util.Map<String, ViolationData> map = new java.util.HashMap<>();
        for (var entry : violations.entrySet()) {
            map.put(entry.getKey().toString(), entry.getValue());
        }
        return map;
    }

    public record ViolationData(int count, int tier, long lastTime) {
        public static final Codec<ViolationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("count").forGetter(ViolationData::count),
                Codec.INT.fieldOf("tier").forGetter(ViolationData::tier),
                Codec.LONG.fieldOf("lastTime").forGetter(ViolationData::lastTime))
                .apply(instance, ViolationData::new));
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
    // ---- Mute & Strike Logic ----

    public void mute(java.util.UUID player, long durationMillis) {
        mutes.put(player, System.currentTimeMillis() + durationMillis);
        setDirty();
    }

    public void unmute(java.util.UUID player) {
        mutes.remove(player);
        setDirty();
    }

    public boolean isMuted(java.util.UUID player) {
        Long end = mutes.get(player);
        if (end == null) return false;
        if (end <= System.currentTimeMillis()) {
            mutes.remove(player);
            setDirty();
            return false;
        }
        return true;
    }

    public long getMuteEnd(java.util.UUID player) {
        return mutes.getOrDefault(player, 0L);
    }

    /**
     * Records a strike.
     * Logic:
     * - Resets tier/count if 24h passed since last violation.
     * - Increments count.
     * - If count >= 3, returns mute duration (millis) and increments tier.
     * - Otherwise returns 0.
     */
    public long addStrike(java.util.UUID player) {
        long now = System.currentTimeMillis();
        ViolationData data = violations.get(player);

        int count = 1;
        int tier = 0;

        if (data != null) {
            long timeSince = now - data.lastTime();
            if (timeSince < 24 * 60 * 60 * 1000L) {
                // Within 24h window, continue tracking
                count = data.count() + 1;
                tier = data.tier();
            }
            // Else: >24h, reset to 1/0 (done by default init above)
        }

        long muteDuration = 0;
        if (count >= 3) {
            // Trigger punishment
            List<Integer> levels = mc.smpessentials.config.SmpConfig.MUTE_LEVELS_MINUTES;
            int mins = levels.get(Math.min(tier, levels.size() - 1));
            muteDuration = mins * 60 * 1000L;

            // Advance tier, reset count
            tier++;
            count = 0;
        }

        violations.put(player, new ViolationData(count, tier, now));
        setDirty();

        return muteDuration;
    }
}
