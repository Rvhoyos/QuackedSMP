package mc.smpessentials.claims.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Single claimed chunk entry.
 * chunk = ChunkPos.pack()
 * name/warp are set on at most one chunk per connected same-owner region (the anchor chunk).
 */
public record ClaimData(
                ResourceKey<Level> dimension,
                long chunk,
                java.util.UUID owner,
                java.util.Optional<String> name,
                long createdAtMillis,
                java.util.Optional<WarpAnchor> warp) {
        public static final Codec<ClaimData> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ClaimData::dimension),
                        Codec.LONG.fieldOf("chunk").forGetter(ClaimData::chunk),
                        UUIDUtil.CODEC.fieldOf("owner").forGetter(ClaimData::owner),
                        Codec.STRING.optionalFieldOf("name").forGetter(ClaimData::name),
                        Codec.LONG.optionalFieldOf("created_at", 0L).forGetter(ClaimData::createdAtMillis),
                        WarpAnchor.CODEC.optionalFieldOf("warp").forGetter(ClaimData::warp))
                        .apply(i, ClaimData::new));

        public ClaimData withName(java.util.Optional<String> newName) {
                return new ClaimData(dimension, chunk, owner, newName, createdAtMillis, warp);
        }

        public ClaimData withWarp(java.util.Optional<WarpAnchor> newWarp) {
                return new ClaimData(dimension, chunk, owner, name, createdAtMillis, newWarp);
        }
}
