package mc.smpessentials.claims.storage;

import net.minecraft.resources.Identifier;
import mc.smpessentials.claims.RegionNames;
import mc.smpessentials.claims.model.ClaimData;
import mc.smpessentials.claims.model.WarpAnchor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;
import java.util.stream.Collectors;

// Persistent store for claims (1.21 SavedDataType API). Storage primitives only; all region-name
// policy (naming, uniqueness, merge, migration, visit) lives in RegionNames.
public final class ClaimedSavedData extends SavedData {
    private final List<ClaimData> claims;

    private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<ClaimData>> indexByDim = new HashMap<>();

    private final Map<UUID, Integer> claimCounts = new HashMap<>();

    // One-shot legacy name migration runs the first time the loaded store is fetched.
    private boolean migrated = false;

    public static final Codec<ClaimedSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ClaimData.CODEC.listOf().fieldOf("claims").forGetter(s -> s.claims)).apply(i, ClaimedSavedData::new));

    public static final SavedDataType<ClaimedSavedData> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace("quackedsmp_claims"),
            () -> new ClaimedSavedData(new ArrayList<>()),
            ClaimedSavedData.CODEC,
            DataFixTypes.LEVEL);

    public ClaimedSavedData(List<ClaimData> claims) {
        this.claims = new ArrayList<>(claims);
        rebuildIndex();
    }

    // Stored in the overworld's DataStorage so counts are global across all dimensions.
    public static ClaimedSavedData get(ServerLevel level) {
        ClaimedSavedData data = level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
        if (!data.migrated) {
            RegionNames.migrate(data);
            data.migrated = true;
        }
        return data;
    }

    private void rebuildIndex() {
        indexByDim.clear();
        claimCounts.clear();
        for (ClaimData c : claims) {
            indexByDim.computeIfAbsent(c.dimension(), k -> new Long2ObjectOpenHashMap<>()).put(c.chunk(), c);
            claimCounts.merge(c.owner(), 1, Integer::sum);
        }
    }

    private Long2ObjectOpenHashMap<ClaimData> dimIndex(ResourceKey<Level> dim) {
        return indexByDim.computeIfAbsent(dim, k -> new Long2ObjectOpenHashMap<>());
    }

    // Swaps one claim record for its updated version (same dim + chunk) across the list and index.
    private void replace(ClaimData old, ClaimData updated) {
        claims.remove(old);
        claims.add(updated);
        dimIndex(updated.dimension()).put(updated.chunk(), updated);
        setDirty();
    }

    public boolean isClaimed(ServerLevel level, ChunkPos chunk) {
        return dimIndex(level.dimension()).containsKey(chunk.pack());
    }

    public Optional<ClaimData> getClaim(ServerLevel level, ChunkPos chunk) {
        return getClaim(level.dimension(), chunk);
    }

    public Optional<ClaimData> getClaim(ResourceKey<Level> dim, ChunkPos chunk) {
        return Optional.ofNullable(dimIndex(dim).get(chunk.pack()));
    }

    public Optional<ClaimData> getClaimAt(ServerLevel level, BlockPos pos) {
        return getClaim(level, ChunkPos.containing(pos));
    }

    // Returns false if the chunk is already claimed.
    public boolean claim(ServerLevel level, ChunkPos chunk, UUID owner) {
        long key = chunk.pack();
        var map = dimIndex(level.dimension());
        if (map.containsKey(key))
            return false;

        ClaimData cd = new ClaimData(level.dimension(), key, owner, Optional.empty(),
                System.currentTimeMillis(), Optional.empty());
        map.put(key, cd);
        claims.add(cd);
        claimCounts.merge(owner, 1, Integer::sum);
        setDirty();
        return true;
    }

    // Returns false if the chunk was not claimed.
    public boolean unclaim(ServerLevel level, ChunkPos chunk) {
        long key = chunk.pack();
        var map = dimIndex(level.dimension());
        ClaimData removed = map.remove(key);
        if (removed == null)
            return false;
        claims.remove(removed);
        claimCounts.computeIfPresent(removed.owner(), (k, v) -> v > 1 ? v - 1 : null);
        setDirty();
        return true;
    }

    public List<ClaimData> listClaims(ServerLevel level) {
        return dimIndex(level.dimension()).values().stream().collect(Collectors.toUnmodifiableList());
    }

    public List<ClaimData> allClaims() {
        return List.copyOf(claims);
    }

    // Chunks owned by a player in one dimension.
    public Set<ChunkPos> ownerChunks(ResourceKey<Level> dim, UUID owner) {
        Set<ChunkPos> out = new HashSet<>();
        for (ClaimData c : dimIndex(dim).values()) {
            if (c.owner().equals(owner))
                out.add(ChunkPos.unpack(c.chunk()));
        }
        return out;
    }

    // The single named chunk in a region, if any (invariant: at most one).
    public Optional<ClaimData> namedChunkIn(ResourceKey<Level> dim, Set<ChunkPos> region) {
        for (ChunkPos cp : region) {
            ClaimData c = dimIndex(dim).get(cp.pack());
            if (c != null && c.name().isPresent() && !c.name().get().isBlank())
                return Optional.of(c);
        }
        return Optional.empty();
    }

    // Sets the display name and warp anchor on one chunk. Returns false if the chunk is not claimed.
    public boolean setNameAnchor(ResourceKey<Level> dim, long chunkKey, String name, WarpAnchor anchor) {
        ClaimData existing = dimIndex(dim).get(chunkKey);
        if (existing == null)
            return false;
        replace(existing, existing.withName(Optional.of(name)).withWarp(Optional.ofNullable(anchor)));
        return true;
    }

    // Clears the display name and warp anchor on one chunk. Returns false if the chunk is not claimed.
    public boolean clearName(ResourceKey<Level> dim, long chunkKey) {
        ClaimData existing = dimIndex(dim).get(chunkKey);
        if (existing == null)
            return false;
        replace(existing, existing.withName(Optional.empty()).withWarp(Optional.empty()));
        return true;
    }

    public int countByOwner(UUID owner) {
        return claimCounts.getOrDefault(owner, 0);
    }

    public Map<UUID, Integer> claimCountsSnapshot() {
        return Map.copyOf(claimCounts);
    }

    // Returns the number of claims removed.
    public int removeAllByOwner(UUID owner) {
        List<ClaimData> toRemove = claims.stream()
                .filter(c -> c.owner().equals(owner))
                .collect(Collectors.toList());
        if (toRemove.isEmpty())
            return 0;
        for (ClaimData c : toRemove) {
            claims.remove(c);
            dimIndex(c.dimension()).remove(c.chunk());
        }
        claimCounts.remove(owner);
        setDirty();
        return toRemove.size();
    }
}
