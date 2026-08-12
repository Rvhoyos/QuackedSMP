package mc.smpessentials.hardcore;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.List;
import java.util.UUID;

// A finished hardcore run, persisted for the leaderboard history. Immutable snapshot taken
// when a session ends; never mutated afterward.
public record HardcoreRun(
        String name,
        String creatorName,
        UUID creator,
        Outcome outcome,
        long startedAt,
        long endedAt,
        int peakPlayers,
        int deaths,
        List<UUID> participants) {

    public enum Outcome { WIN, LOSS, FORCE_ENDED }

    public static final Codec<HardcoreRun> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(HardcoreRun::name),
            Codec.STRING.fieldOf("creator_name").forGetter(HardcoreRun::creatorName),
            UUIDUtil.CODEC.fieldOf("creator").forGetter(HardcoreRun::creator),
            Codec.STRING.xmap(HardcoreRun::parseOutcome, Outcome::name).fieldOf("outcome").forGetter(HardcoreRun::outcome),
            Codec.LONG.fieldOf("started_at").forGetter(HardcoreRun::startedAt),
            Codec.LONG.fieldOf("ended_at").forGetter(HardcoreRun::endedAt),
            Codec.INT.fieldOf("peak_players").forGetter(HardcoreRun::peakPlayers),
            Codec.INT.fieldOf("deaths").forGetter(HardcoreRun::deaths),
            UUIDUtil.CODEC.listOf().fieldOf("participants").forGetter(HardcoreRun::participants)
    ).apply(i, HardcoreRun::new));

    private static Outcome parseOutcome(String s) {
        try {
            return Outcome.valueOf(s);
        } catch (IllegalArgumentException e) {
            return Outcome.FORCE_ENDED;
        }
    }

    // Real elapsed run time in milliseconds.
    public long durationMillis() {
        return Math.max(0, endedAt - startedAt);
    }
}
