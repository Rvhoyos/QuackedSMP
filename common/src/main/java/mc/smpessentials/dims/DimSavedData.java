package mc.smpessentials.dims;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// SavedData store for custom dimensions created via /dim create.
// Read by DimManager.restoreAll() on startup to reconstruct dimensions before players connect.
public final class DimSavedData extends SavedData {

    // Persisted metadata for a single custom dimension.
    public record DimEntry(String id, String generatorType,
                            Optional<String> generatorConfig, Optional<String> portalBlock) {

        public static final Codec<DimEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(DimEntry::id),
                Codec.STRING.fieldOf("generatorType").forGetter(DimEntry::generatorType),
                Codec.STRING.optionalFieldOf("generatorConfig").forGetter(DimEntry::generatorConfig),
                Codec.STRING.optionalFieldOf("portalBlock").forGetter(DimEntry::portalBlock)
        ).apply(i, DimEntry::new));

        // Convenience: no generator config or portal block.
        public DimEntry(String id, String generatorType) {
            this(id, generatorType, Optional.empty(), Optional.empty());
        }
    }

    private final List<DimEntry> entries;

    public static final Codec<DimSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            DimEntry.CODEC.listOf().fieldOf("dims").forGetter(s -> s.entries)
    ).apply(i, DimSavedData::new));

    public static final SavedDataType<DimSavedData> TYPE = new SavedDataType<>(
            "quackedsmp_dims",
            () -> new DimSavedData(new ArrayList<>()),
            DimSavedData.CODEC,
            DataFixTypes.LEVEL);

    public DimSavedData(List<DimEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    // Primary entry point. Lazily creates the store if not yet loaded.
    public static DimSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<DimEntry> getEntries() {
        return List.copyOf(entries);
    }

    public Optional<DimEntry> getEntry(String id) {
        return entries.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    // Used by portal routing to look up which custom dim owns a given frame block.
    public Optional<String> getDimForPortalBlock(String blockId) {
        return entries.stream()
                .filter(e -> e.portalBlock().isPresent() && e.portalBlock().get().equals(blockId))
                .map(DimEntry::id)
                .findFirst();
    }

    public void add(String id, String generatorType, Optional<String> generatorConfig) {
        entries.add(new DimEntry(id, generatorType, generatorConfig, Optional.empty()));
        setDirty();
    }

    // Falls back to parsed-Identifier equality for legacy entries stored without a namespace.
    // Returns true if an entry was found and removed.
    public boolean remove(String id) {
        // Try exact match first; fall back to parsed-identifier equality for legacy entries
        // where the stored id may lack a namespace (e.g. "pvp" vs "minecraft:pvp").
        Identifier parsedId = tryParseIdentifier(id);
        boolean removed = entries.removeIf(e -> {
            if (e.id().equals(id)) return true;
            if (parsedId != null) {
                Identifier parsedEntry = tryParseIdentifier(e.id());
                return parsedId.equals(parsedEntry);
            }
            return false;
        });
        if (removed) setDirty();
        return removed;
    }

    private static Identifier tryParseIdentifier(String id) {
        try { return Identifier.parse(id); } catch (Exception e) { return null; }
    }

    // Each dim holds at most one frame block. Returns false if the dim doesn't exist.
    public boolean setPortalBlock(String id, String blockId) {
        for (int i = 0; i < entries.size(); i++) {
            DimEntry e = entries.get(i);
            if (e.id().equals(id)) {
                entries.set(i, new DimEntry(e.id(), e.generatorType(), e.generatorConfig(), Optional.of(blockId)));
                setDirty();
                return true;
            }
        }
        return false;
    }

    /**
     * Removes the portal block assignment from a dim.
     * Called before {@link #setPortalBlock} when a block is being reassigned to a different dim,
     * ensuring the one-block-per-dim / one-dim-per-block invariant is maintained.
     */
    public void clearPortalBlock(String id) {
        for (int i = 0; i < entries.size(); i++) {
            DimEntry e = entries.get(i);
            if (e.id().equals(id)) {
                entries.set(i, new DimEntry(e.id(), e.generatorType(), e.generatorConfig(), Optional.empty()));
                setDirty();
                return;
            }
        }
    }
}
