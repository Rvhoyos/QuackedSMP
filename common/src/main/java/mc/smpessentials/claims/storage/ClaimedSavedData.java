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

    public static ClaimedSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
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

    public boolean isClaimed(ServerLevel level, ChunkPos chunk) {
        return dimIndex(level.dimension()).containsKey(chunk.toLong());
    }

    public Optional<ClaimData> getClaim(ServerLevel level, ChunkPos chunk) {
        return Optional.ofNullable(dimIndex(level.dimension()).get(chunk.toLong()));
    }

    public Optional<ClaimData> getClaimAt(ServerLevel level, BlockPos pos) {
        return getClaim(level, new ChunkPos(pos));
    }

    public boolean claim(ServerLevel level, ChunkPos chunk, UUID owner) {
        long key = chunk.toLong();
        var map = dimIndex(level.dimension());
        if (map.containsKey(key))
            return false;

        ClaimData cd = new ClaimData(level.dimension(), key, owner, System.currentTimeMillis());
        map.put(key, cd);
        claims.add(cd);
        claimCounts.merge(owner, 1, Integer::sum);
        setDirty();
        return true;
    }

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

    public List<ClaimData> listClaims(ServerLevel level) {
        return dimIndex(level.dimension()).values().stream().collect(Collectors.toUnmodifiableList());
    }
    // in mc.smpessentials.claims.storage.ClaimedSavedData

    public int countByOwner(ServerLevel level, UUID owner) {
        return claimCounts.getOrDefault(owner, 0);
    }

}
