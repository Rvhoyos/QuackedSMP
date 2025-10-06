package mc.smpessentials.claims.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Single claimed chunk entry.
 * chunk = ChunkPos.toLong()
 */
public record ClaimData(
        ResourceKey<Level> dimension,
        long chunk,
        java.util.UUID owner,
        long createdAtMillis
) {
    public static final Codec<ClaimData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ClaimData::dimension),
            Codec.LONG.fieldOf("chunk").forGetter(ClaimData::chunk),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(ClaimData::owner),
            Codec.LONG.optionalFieldOf("created_at", 0L).forGetter(ClaimData::createdAtMillis)
    ).apply(i, ClaimData::new));
}
