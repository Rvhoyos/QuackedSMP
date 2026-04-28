package mc.smpessentials.shops;

import net.minecraft.resources.Identifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

// Persists all registered shops. Keyed by dimension + block position for fast lookup.
public final class ShopData extends SavedData {

    private final Map<ResourceKey<Level>, Map<BlockPos, ShopEntry>> shops = new HashMap<>();

    public ShopData() {}

    private List<ShopEntry> toList() {
        List<ShopEntry> list = new ArrayList<>();
        for (Map<BlockPos, ShopEntry> dimShops : shops.values()) {
            list.addAll(dimShops.values());
        }
        return list;
    }

    private static ShopData fromList(List<ShopEntry> entries) {
        ShopData d = new ShopData();
        for (ShopEntry e : entries) {
            d.shops.computeIfAbsent(e.dimension(), k -> new HashMap<>()).put(e.pos(), e);
        }
        return d;
    }

    public static final Codec<ShopData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ShopEntry.CODEC.listOf().optionalFieldOf("shops", List.of())
                    .forGetter(ShopData::toList))
            .apply(i, ShopData::fromList));

    public static final SavedDataType<ShopData> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace("quackedsmp_shops"),
            ShopData::new,
            CODEC,
            DataFixTypes.LEVEL);

    public static ShopData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<ShopEntry> getShopAt(ResourceKey<Level> dim, BlockPos pos) {
        Map<BlockPos, ShopEntry> dimShops = shops.get(dim);
        if (dimShops == null) return Optional.empty();
        return Optional.ofNullable(dimShops.get(pos));
    }

    public boolean addShop(ShopEntry entry) {
        Map<BlockPos, ShopEntry> dimShops = shops.computeIfAbsent(entry.dimension(), k -> new HashMap<>());
        if (dimShops.containsKey(entry.pos())) return false;
        dimShops.put(entry.pos(), entry);
        setDirty();
        return true;
    }

    public boolean removeShop(ResourceKey<Level> dim, BlockPos pos) {
        Map<BlockPos, ShopEntry> dimShops = shops.get(dim);
        if (dimShops == null || dimShops.remove(pos) == null) return false;
        if (dimShops.isEmpty()) shops.remove(dim);
        setDirty();
        return true;
    }

    public List<ShopEntry> listAll() {
        return toList();
    }

    public List<ShopEntry> listByOwner(UUID owner) {
        List<ShopEntry> result = new ArrayList<>();
        for (Map<BlockPos, ShopEntry> dimShops : shops.values()) {
            for (ShopEntry e : dimShops.values()) {
                if (e.owner().equals(owner)) result.add(e);
            }
        }
        return result;
    }
}
