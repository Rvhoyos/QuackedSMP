package mc.smpessentials.slimechunk;

import mc.smpessentials.config.SmpConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldgenRandom;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ambient hint that the player is standing in a slime chunk.
 *
 * Emits a few slime particles at the player's feet, plus a rare quiet squish, while they are inside
 * a slime chunk below Y 40. Both the chunk test and the depth gate are copied from vanilla's own
 * spawn rule ({@code Slime.checkSlimeSpawnRules}), so the hint appears exactly where slimes can
 * actually spawn and nowhere else.
 *
 * Swamp surface slimes (Y 50 to 70) are a separate vanilla spawn path that has nothing to do with
 * slime chunks, so no hint is shown for them.
 *
 * The cue is broadcast, not sent to one connection, so players standing nearby see the particles
 * and hear the squish too.
 */
public final class SlimeChunkHint {

    private SlimeChunkHint() {}

    // Vanilla's slime chunk salt and depth gate. Both from Slime.checkSlimeSpawnRules.
    private static final long SLIME_SALT = 987234911L;
    private static final int MAX_Y = 40;

    // Quiet enough to read as ambient rather than as a mob behind you.
    private static final float SOUND_VOLUME = 0.25f;

    // Last chunk each player was tested in, so the seeded RNG runs once per chunk entry
    // instead of once per emission.
    private static final Map<UUID, Cached> CACHE = new HashMap<>();

    private record Cached(long chunkKey, boolean slime) {}

    /**
     * Runs the hint for one player. Called once per player per server tick; self-throttles to the
     * configured interval. No-op outside the overworld, above {@link #MAX_Y}, or when disabled.
     */
    public static void tick(ServerPlayer player) {
        if (!SmpConfig.SLIME_HINT_ENABLED) return;

        ServerLevel level = (ServerLevel) player.level();
        if (level.dimension() != Level.OVERWORLD) return;
        if (player.getY() >= MAX_Y) return;

        int interval = Math.max(1, SmpConfig.SLIME_HINT_INTERVAL_TICKS);
        if (player.tickCount % interval != 0) return;

        if (!inSlimeChunk(level, player)) return;
        emit(level, player);
    }

    /** Drops the cached chunk test for a player who left. */
    public static void onDisconnect(UUID uuid) {
        CACHE.remove(uuid);
    }

    private static boolean inSlimeChunk(ServerLevel level, ServerPlayer player) {
        ChunkPos pos = player.chunkPosition();
        long key = pos.pack();
        Cached cached = CACHE.get(player.getUUID());
        if (cached != null && cached.chunkKey() == key) return cached.slime();

        boolean slime = WorldgenRandom
                .seedSlimeChunk(pos.x(), pos.z(), level.getSeed(), SLIME_SALT)
                .nextInt(10) == 0;
        CACHE.put(player.getUUID(), new Cached(key, slime));
        return slime;
    }

    private static void emit(ServerLevel level, ServerPlayer player) {
        int count = Math.max(1, SmpConfig.SLIME_HINT_PARTICLE_COUNT);
        level.sendParticles(ParticleTypes.ITEM_SLIME,
                player.getX(), player.getY() + 0.1, player.getZ(),
                count, 0.25, 0.05, 0.25, 0.0);

        double chance = SmpConfig.SLIME_HINT_SOUND_CHANCE;
        if (chance > 0 && ThreadLocalRandom.current().nextDouble() < chance) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SLIME_SQUISH_SMALL, SoundSource.AMBIENT,
                    SOUND_VOLUME, 0.8f + ThreadLocalRandom.current().nextFloat() * 0.4f);
        }
    }
}
