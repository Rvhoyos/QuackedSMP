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

/**
 * Persists metadata for custom dimensions created via {@code /dim create} across server restarts.
 *
 * <p>Stored in the overworld's {@code DimensionDataStorage} under the key
 * {@code quackedsmp_dims}, alongside other global saved data such as claims.
 *
 * <p>On server start, {@link DimManager#restoreAll} reads this data and reconstructs every
 * saved dimension before players can connect. The stored {@code id} is always the canonical
 * {@code namespace:path} string from the dimension's {@link net.minecraft.resources.ResourceKey}
 * to guarantee that creates and deletes always match.
 *
 * <p><strong>Note:</strong> this storage only covers dimensions created through our own
 * {@code /dim create} command. Dimensions added externally (e.g. via datapacks) are loaded by
 * Minecraft automatically and appear in the live level registry, but have no entry here.
 * Callers that need the full set of active dimensions should use {@link DimManager#listAll}
 * and look up entries here only for metadata enrichment.
 */
public final class DimSavedData extends SavedData {

    /**
     * Immutable record of a single custom dimension's creation parameters.
     *
     * @param id              canonical dimension resource location, e.g. {@code quacksmp:pvp}.
     *                        Always stored in normalized {@code namespace:path} form so that
     *                        {@link DimManager#destroy} can match it reliably.
     * @param generatorType   one of {@code overworld}, {@code nether}, {@code end}, {@code ether}
     * @param generatorConfig optional sub-generator config string:
     *                        <ul>
     *                          <li>absent — default biomes for the type</li>
     *                          <li>{@code "biomes minecraft:plains:2 minecraft:desert:1"} —
     *                              weighted biome list; weight controls area share, not appearance</li>
     *                          <li>{@code "flat minecraft:stone:5 minecraft:dirt:3 minecraft:grass_block:1"} —
     *                              flat terrain layers, bottom to top ({@code overworld} only)</li>
     *                        </ul>
     * @param portalBlock     optional block type used as the portal frame, e.g. {@code minecraft:glowstone}
     */
    public record DimEntry(String id, String generatorType,
                            Optional<String> generatorConfig, Optional<String> portalBlock) {

        public static final Codec<DimEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(DimEntry::id),
                Codec.STRING.fieldOf("generatorType").forGetter(DimEntry::generatorType),
                Codec.STRING.optionalFieldOf("generatorConfig").forGetter(DimEntry::generatorConfig),
                Codec.STRING.optionalFieldOf("portalBlock").forGetter(DimEntry::portalBlock)
        ).apply(i, DimEntry::new));

        /** Convenience — no generator config or portal block. */
        public DimEntry(String id, String generatorType) {
            this(id, generatorType, Optional.empty(), Optional.empty());
        }
    }

    private final List<DimEntry> entries;

    /** Codec that serialises the full list of dim entries under the {@code "dims"} field. */
    public static final Codec<DimSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            DimEntry.CODEC.listOf().fieldOf("dims").forGetter(s -> s.entries)
    ).apply(i, DimSavedData::new));

    /**
     * Registration descriptor used by {@link net.minecraft.world.level.storage.DimensionDataStorage}.
     * The storage key is {@code quackedsmp_dims}; an empty list is the default when no file exists.
     */
    public static final SavedDataType<DimSavedData> TYPE = new SavedDataType<>(
            "quackedsmp_dims",
            () -> new DimSavedData(new ArrayList<>()),
            DimSavedData.CODEC,
            DataFixTypes.LEVEL);

    /**
     * Constructs a new instance with a defensive copy of {@code entries}.
     * Normally called by the {@link #CODEC} deserialiser or by {@link #TYPE}'s default factory.
     */
    public DimSavedData(List<DimEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    /**
     * Retrieves (or lazily creates) the {@code DimSavedData} instance from the overworld's
     * data storage. This is the primary entry point for all callers.
     */
    public static DimSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Returns an unmodifiable snapshot of all stored dimension entries. */
    public List<DimEntry> getEntries() {
        return List.copyOf(entries);
    }

    /**
     * Looks up the entry for {@code id} by exact string match.
     *
     * @param id canonical {@code namespace:path} dimension id
     * @return the matching entry, or empty if none exists
     */
    public Optional<DimEntry> getEntry(String id) {
        return entries.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    /**
     * Returns the id of the custom dim whose portal frame block is {@code blockId}, if any.
     * Used by {@link mc.smpessentials.dims.CustomPortalActivator} and
     * {@link mc.smpessentials.mixin.NetherPortalBlockMixin} to resolve portal routing.
     */
    public Optional<String> getDimForPortalBlock(String blockId) {
        return entries.stream()
                .filter(e -> e.portalBlock().isPresent() && e.portalBlock().get().equals(blockId))
                .map(DimEntry::id)
                .findFirst();
    }

    /**
     * Adds a new dimension entry with no portal block assigned and marks the data dirty.
     * The portal block must be set separately via {@link #setPortalBlock}.
     *
     * @param id              canonical {@code namespace:path} dimension resource location
     * @param generatorType   one of {@code overworld}, {@code nether}, {@code end}, {@code ether}
     * @param generatorConfig optional sub-generator config string (see {@link DimEntry})
     */
    public void add(String id, String generatorType, Optional<String> generatorConfig) {
        entries.add(new DimEntry(id, generatorType, generatorConfig, Optional.empty()));
        setDirty();
    }

    /**
     * Removes the entry whose {@code id} matches, using exact string equality first and falling
     * back to parsed-{@link Identifier} equality for legacy entries stored without a namespace
     * (e.g. {@code "pvp"} stored before normalization was enforced, matching {@code "minecraft:pvp"}).
     *
     * @return {@code true} if an entry was found and removed
     */
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

    /**
     * Parses {@code id} as a {@link net.minecraft.resources.Identifier}, returning {@code null}
     * instead of throwing if the string is malformed.
     */
    private static Identifier tryParseIdentifier(String id) {
        try { return Identifier.parse(id); } catch (Exception e) { return null; }
    }

    /**
     * Assigns a portal frame block to an existing dim.
     * Each dim holds at most one portal block; each block links to at most one dim.
     * Call {@link #clearPortalBlock} on the previous owner before calling this if reassigning.
     *
     * @return {@code false} if no dim with {@code id} exists
     */
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
