package mc.smpessentials.pregen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How far each dimension's pregen got, so a run that was interrupted resumes from the last batch
 * that actually reached disk, and a finished area never delays another startup.
 *
 * The distance an entry was recorded for is stored alongside the cursor: raising the configured
 * distance changes the area, so the old entry stops applying and that dimension runs again.
 */
public final class PregenProgress extends SavedData {

    /** One dimension's place in the run, plus what that dimension actually cost. */
    record Entry(int distance, long cursor, boolean complete, double kbPerChunk, double chunksPerSecond) {}

    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("distance").forGetter(Entry::distance),
            Codec.LONG.fieldOf("cursor").forGetter(Entry::cursor),
            Codec.BOOL.fieldOf("complete").forGetter(Entry::complete),
            Codec.DOUBLE.optionalFieldOf("kb_per_chunk", PregenEstimate.SEED_KB_PER_CHUNK)
                    .forGetter(Entry::kbPerChunk),
            Codec.DOUBLE.optionalFieldOf("chunks_per_second", PregenEstimate.SEED_CHUNKS_PER_SECOND)
                    .forGetter(Entry::chunksPerSecond))
            .apply(i, Entry::new));

    private record NamedEntry(String dimension, Entry entry) {}

    private static final Codec<NamedEntry> NAMED_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("dimension").forGetter(NamedEntry::dimension),
            ENTRY_CODEC.fieldOf("progress").forGetter(NamedEntry::entry))
            .apply(i, NamedEntry::new));

    public static final Codec<PregenProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            NAMED_CODEC.listOf().optionalFieldOf("dimensions", List.of())
                    .forGetter(PregenProgress::toEntries))
            .apply(i, PregenProgress::fromEntries));

    public static final SavedDataType<PregenProgress> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace("quackedsmp_pregen"),
            PregenProgress::new,
            CODEC,
            DataFixTypes.LEVEL);

    private final Map<String, Entry> byDimension = new HashMap<>();

    public PregenProgress() {}

    public static PregenProgress get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private List<NamedEntry> toEntries() {
        List<NamedEntry> list = new ArrayList<>(this.byDimension.size());
        this.byDimension.forEach((dim, entry) -> list.add(new NamedEntry(dim, entry)));
        return list;
    }

    private static PregenProgress fromEntries(List<NamedEntry> entries) {
        PregenProgress progress = new PregenProgress();
        for (NamedEntry e : entries) progress.byDimension.put(e.dimension(), e.entry());
        return progress;
    }

    /** Whether this dimension is already done for exactly this distance. */
    boolean isComplete(String dimension, int distance) {
        Entry entry = this.byDimension.get(dimension);
        return entry != null && entry.complete() && entry.distance() == distance;
    }

    /**
     * Where a run should pick up. Zero when the dimension is new or the distance changed, since a
     * different distance means a different square and the old cursor points into nothing.
     */
    long cursor(String dimension, int distance) {
        Entry entry = this.byDimension.get(dimension);
        return entry != null && entry.distance() == distance ? entry.cursor() : 0L;
    }

    double kbPerChunk(String dimension) {
        Entry entry = this.byDimension.get(dimension);
        return entry != null ? entry.kbPerChunk() : PregenEstimate.SEED_KB_PER_CHUNK;
    }

    double chunksPerSecond(String dimension) {
        Entry entry = this.byDimension.get(dimension);
        return entry != null ? entry.chunksPerSecond() : PregenEstimate.SEED_CHUNKS_PER_SECOND;
    }

    /** Records a batch boundary. Only called once the batch's chunks are on disk. */
    void advance(String dimension, int distance, long cursor, boolean complete,
                 double kbPerChunk, double chunksPerSecond) {
        this.byDimension.put(dimension,
                new Entry(distance, cursor, complete, kbPerChunk, chunksPerSecond));
        setDirty();
    }
}
