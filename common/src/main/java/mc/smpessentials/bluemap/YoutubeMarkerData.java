package mc.smpessentials.bluemap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Video markers an OP has pinned on the map. */
public final class YoutubeMarkerData extends SavedData {

    public record Entry(UUID owner, String label, String url, ResourceKey<Level> dimension,
            double x, double y, double z) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner),
                Codec.STRING.fieldOf("label").forGetter(Entry::label),
                Codec.STRING.fieldOf("url").forGetter(Entry::url),
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Entry::dimension),
                Codec.DOUBLE.fieldOf("x").forGetter(Entry::x),
                Codec.DOUBLE.fieldOf("y").forGetter(Entry::y),
                Codec.DOUBLE.fieldOf("z").forGetter(Entry::z))
                .apply(i, Entry::new));
    }

    private final List<Entry> entries = new ArrayList<>();

    public YoutubeMarkerData() {
    }

    public static final Codec<YoutubeMarkerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().optionalFieldOf("entries", List.of())
                    .forGetter(d -> List.copyOf(d.entries)))
            .apply(i, list -> {
                YoutubeMarkerData d = new YoutubeMarkerData();
                d.entries.addAll(list);
                return d;
            }));

    public static final SavedDataType<YoutubeMarkerData> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace("quackedsmp_youtube_markers"),
            YoutubeMarkerData::new,
            CODEC,
            DataFixTypes.LEVEL);

    public static YoutubeMarkerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Entry> all() {
        return List.copyOf(entries);
    }

    public List<Entry> byOwner(UUID owner) {
        return entries.stream().filter(e -> e.owner().equals(owner)).toList();
    }

    public void add(Entry entry) {
        entries.add(entry);
        setDirty();
    }

    /** Removes the entry nearest the given spot in the same dimension, if there is one. */
    public Optional<Entry> removeNearest(ResourceKey<Level> dim, double x, double y, double z) {
        Entry best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entry e : entries) {
            if (!e.dimension().equals(dim))
                continue;
            double d = distSq(e, x, y, z);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        if (best == null)
            return Optional.empty();
        entries.remove(best);
        setDirty();
        return Optional.of(best);
    }

    private static double distSq(Entry e, double x, double y, double z) {
        double dx = e.x() - x, dy = e.y() - y, dz = e.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Whether a URL may be pinned. The marker popup is HTML on a page anyone can open, so this
     * is a security control: only https, and only YouTube hosts, so the command cannot be used
     * to plant an arbitrary link on the public map.
     */
    public static boolean isAllowedUrl(String url) {
        if (url == null || url.isBlank())
            return false;
        final java.net.URI uri;
        try {
            uri = java.net.URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
            return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.equals("youtube.com") || host.equals("www.youtube.com")
                || host.equals("m.youtube.com") || host.equals("youtu.be");
    }
}
