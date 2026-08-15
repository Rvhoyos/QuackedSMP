package mc.smpessentials.claims.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Exact spot a player stood when they named a region, used as the /visit destination.
 * Dimension is not stored here: it is the owning claim's dimension, since a region can
 * only be named from inside it.
 */
public record WarpAnchor(double x, double y, double z, float yaw, float pitch) {
    public static final Codec<WarpAnchor> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("x").forGetter(WarpAnchor::x),
            Codec.DOUBLE.fieldOf("y").forGetter(WarpAnchor::y),
            Codec.DOUBLE.fieldOf("z").forGetter(WarpAnchor::z),
            Codec.FLOAT.fieldOf("yaw").forGetter(WarpAnchor::yaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(WarpAnchor::pitch))
            .apply(i, WarpAnchor::new));
}
