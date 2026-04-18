package mc.smpessentials.shops;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

// One shop = one chest selling one item type at a configurable price.
// currencyItemId defaults to "minecraft:emerald" but can be any item.
public record ShopEntry(
        ResourceKey<Level> dimension,
        BlockPos pos,
        UUID owner,
        String itemId,
        int pricePerItem,
        String currencyItemId,
        boolean spawnShop) {

    public static final Codec<ShopEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ShopEntry::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(ShopEntry::pos),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(ShopEntry::owner),
            Codec.STRING.fieldOf("item_id").forGetter(ShopEntry::itemId),
            Codec.INT.fieldOf("price").forGetter(ShopEntry::pricePerItem),
            Codec.STRING.optionalFieldOf("currency", "minecraft:emerald").forGetter(ShopEntry::currencyItemId),
            Codec.BOOL.optionalFieldOf("spawn_shop", false).forGetter(ShopEntry::spawnShop))
            .apply(i, ShopEntry::new));
}
