package mc.smpessentials.ageverify;

import net.minecraft.resources.Identifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/**
 * Persists per-player age verification responses server-side via {@link SavedData}.
 * Stored as {@code quackedsmp_ageverify} in the overworld data folder.
 * Three states: unverified (absent from both sets, re-prompted every 5 min), verified (18+,
 * voice chat enabled), or denied (opted out, voice chat off). Players can change at any time.
 */
public final class AgeVerifyData extends SavedData {

    private final Set<UUID> verified = new HashSet<>();
    private final Set<UUID> denied = new HashSet<>();

    public static final Codec<AgeVerifyData> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.listOf().fieldOf("verified").forGetter(d -> List.copyOf(d.verified)),
            UUIDUtil.CODEC.listOf().fieldOf("denied").forGetter(d -> List.copyOf(d.denied)))
            .apply(i, AgeVerifyData::fromLists));

    public static final SavedDataType<AgeVerifyData> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace("quackedsmp_ageverify"),
            AgeVerifyData::new,
            CODEC,
            DataFixTypes.LEVEL);

    private AgeVerifyData() {
    }

    /** Codec deserialisation constructor, called by {@link #CODEC} when loading saved data. */
    private static AgeVerifyData fromLists(List<UUID> verifiedList, List<UUID> deniedList) {
        AgeVerifyData d = new AgeVerifyData();
        d.verified.addAll(verifiedList);
        d.denied.addAll(deniedList);
        return d;
    }

    /** Returns the singleton instance for the running server, creating it if absent. */
    public static AgeVerifyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Returns {@code true} if the player has given any response (confirm or deny). */
    public boolean hasAnswered(UUID uuid) {
        return verified.contains(uuid) || denied.contains(uuid);
    }

    /** Returns {@code true} if the player confirmed they are 18 or older. */
    public boolean isVerified(UUID uuid) {
        return verified.contains(uuid);
    }

    /** Marks the player as age-verified, removing any prior denial. */
    public void setVerified(UUID uuid) {
        verified.add(uuid);
        denied.remove(uuid);
        setDirty();
    }

    /** Marks the player as having declined, removing any prior verification. */
    public void setDenied(UUID uuid) {
        denied.add(uuid);
        verified.remove(uuid);
        setDirty();
    }
}
