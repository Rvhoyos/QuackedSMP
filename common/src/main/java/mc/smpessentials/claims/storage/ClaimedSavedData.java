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

    public static final SavedDataType<ClaimedSavedData> TYPE = new SavedDataType<>(
            "quackedsmp_claims",
            ctx -> new ClaimedSavedData(List.of()),
            ctx -> ClaimedSavedData.CODEC,
            DataFixTypes.LEVEL
    );

    public static final Codec<ClaimedSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ClaimData.CODEC.listOf().fieldOf("claims").forGetter(s -> s.claims)
    ).apply(i, ClaimedSavedData::new));

    public ClaimedSavedData(List<ClaimData> claims) {
        this.claims = new ArrayList<>(claims);
        rebuildIndex();
    }

    public static ClaimedSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private void rebuildIndex() {
        indexByDim.clear();
        for (ClaimData c : claims) {
            indexByDim.computeIfAbsent(c.dimension(), k -> new Long2ObjectOpenHashMap<>()).put(c.chunk(), c);
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
        if (map.containsKey(key)) return false;

        ClaimData cd = new ClaimData(level.dimension(), key, owner, System.currentTimeMillis());
        map.put(key, cd);
        claims.add(cd);
        setDirty();
        return true;
    }

    public boolean unclaim(ServerLevel level, ChunkPos chunk) {
        long key = chunk.toLong();
        var map = dimIndex(level.dimension());
        ClaimData removed = map.remove(key);
        if (removed == null) return false;
        claims.remove(removed);
        setDirty();
        return true;
    }

    public List<ClaimData> listClaims(ServerLevel level) {
        return dimIndex(level.dimension()).values().stream().collect(Collectors.toUnmodifiableList());
    }
    // in mc.smpessentials.claims.storage.ClaimedSavedData

    public int countByOwner(ServerLevel level, UUID owner) {
        var map = dimIndex(level.dimension());
        int n = 0;
        for (ClaimData c : map.values()) {
            if (c.owner().equals(owner)) n++;
        }
        return n;
    }

}
