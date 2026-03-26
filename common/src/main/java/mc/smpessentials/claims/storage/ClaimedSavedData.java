package mc.smpessentials.claims.storage;

import mc.smpessentials.claims.model.ClaimData;
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

/** Persistent store for claims (1.21 SavedDataType API). */
public final class ClaimedSavedData extends SavedData {
    private final List<ClaimData> claims;

    private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<ClaimData>> indexByDim = new HashMap<>();

    private final Map<UUID, Integer> claimCounts = new HashMap<>();

    public static final Codec<ClaimedSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ClaimData.CODEC.listOf().fieldOf("claims").forGetter(s -> s.claims)).apply(i, ClaimedSavedData::new));

    public static final SavedDataType<ClaimedSavedData> TYPE = new SavedDataType<>(
            "quackedsmp_claims",
            () -> new ClaimedSavedData(new ArrayList<>()),
            ClaimedSavedData.CODEC,
            DataFixTypes.LEVEL);

    public ClaimedSavedData(List<ClaimData> claims) {
        this.claims = new ArrayList<>(claims);
        rebuildIndex();
    }

    /**
     * All claim data is stored in the overworld's DataStorage so that counts
     * are global across all dimensions. The dimension key inside each ClaimData
     * record still scopes individual claims correctly.
     */
    public static ClaimedSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
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

    /** Returns {@code true} if {@code chunk} in {@code level}'s dimension is claimed. */
    public boolean isClaimed(ServerLevel level, ChunkPos chunk) {
        return dimIndex(level.dimension()).containsKey(chunk.toLong());
    }

    /** Returns the {@link ClaimData} for {@code chunk} in {@code level}'s dimension, or empty. */
    public Optional<ClaimData> getClaim(ServerLevel level, ChunkPos chunk) {
        return Optional.ofNullable(dimIndex(level.dimension()).get(chunk.toLong()));
    }

    /** Convenience overload: looks up the claim for the chunk containing {@code pos}. */
    public Optional<ClaimData> getClaimAt(ServerLevel level, BlockPos pos) {
        return getClaim(level, new ChunkPos(pos));
    }

    /**
     * Claims {@code chunk} in {@code level}'s dimension for {@code owner}.
     *
     * @return {@code true} if the claim was created; {@code false} if already claimed.
     */
    public boolean claim(ServerLevel level, ChunkPos chunk, UUID owner) {
        long key = chunk.toLong();
        var map = dimIndex(level.dimension());
        if (map.containsKey(key))
            return false;

        ClaimData cd = new ClaimData(level.dimension(), key, owner, Optional.empty(), System.currentTimeMillis());
        map.put(key, cd);
        claims.add(cd);
        claimCounts.merge(owner, 1, Integer::sum);
        setDirty();
        return true;
    }

    /**
     * Removes the claim for {@code chunk} in {@code level}'s dimension regardless of owner.
     *
     * @return {@code true} if the claim existed and was removed; {@code false} if unclaimed.
     */
    public boolean unclaim(ServerLevel level, ChunkPos chunk) {
        long key = chunk.toLong();
        var map = dimIndex(level.dimension());
        ClaimData removed = map.remove(key);
        if (removed == null)
            return false;
        claims.remove(removed);
        claimCounts.computeIfPresent(removed.owner(), (k, v) -> v > 1 ? v - 1 : null);
        setDirty();
        return true;
    }

    /** Returns an unmodifiable snapshot of all claims in {@code level}'s dimension. */
    public List<ClaimData> listClaims(ServerLevel level) {
        return dimIndex(level.dimension()).values().stream().collect(Collectors.toUnmodifiableList());
    }

    /**
     * Sets or clears the display name of the claim at {@code chunk}.
     *
     * @param name the new name, or {@code null} to clear it.
     * @return {@code true} if the claim existed and was updated; {@code false} if unclaimed.
     */
    public boolean updateName(ServerLevel level, ChunkPos chunk, String name) {
        long key = chunk.toLong();
        var map = dimIndex(level.dimension());
        ClaimData existing = map.get(key);
        if (existing == null) {
            return false;
        }

        ClaimData updated = new ClaimData(
                existing.dimension(),
                existing.chunk(),
                existing.owner(),
                Optional.ofNullable(name),
                existing.createdAtMillis());

        map.put(key, updated);

        // Update the list reference
        claims.remove(existing);
        claims.add(updated);

        setDirty();
        return true;
    }
    /** Returns the total number of claims owned by {@code owner} across all dimensions. */
    public int countByOwner(UUID owner) {
        return claimCounts.getOrDefault(owner, 0);
    }

    /** Returns an unmodifiable snapshot of claim counts per owner UUID. */
    public Map<UUID, Integer> claimCountsSnapshot() {
        return Map.copyOf(claimCounts);
    }

    /**
     * Removes all claims owned by {@code owner}.
     * Returns the number of claims removed.
     */
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

    /**
     * Transfers all claims from {@code from} to {@code to}.
     * Returns the number of claims transferred (0 if {@code from} had none).
     */
    public int transferClaims(UUID from, UUID to) {
        List<ClaimData> toTransfer = claims.stream()
                .filter(c -> c.owner().equals(from))
                .collect(Collectors.toList());
        if (toTransfer.isEmpty())
            return 0;
        for (ClaimData old : toTransfer) {
            ClaimData updated = new ClaimData(old.dimension(), old.chunk(), to, old.name(), old.createdAtMillis());
            claims.remove(old);
            claims.add(updated);
            dimIndex(old.dimension()).put(old.chunk(), updated);
        }
        int count = toTransfer.size();
        claimCounts.computeIfPresent(from, (k, v) -> v > count ? v - count : null);
        claimCounts.merge(to, count, Integer::sum);
        setDirty();
        return count;
    }

}
