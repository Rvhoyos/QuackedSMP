package mc.smpessentials.antixray;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which block positions have already been sent to a player as their real state, so
 * proximity reveal does not re-send the same block every tick. Positions are packed longs
 * ({@code BlockPos.asLong}) held in a primitive set, since the set is probed once per position
 * per player per tick.
 */
final class RevealedPositions {
    private static final int MAX_PER_PLAYER = 10_000;

    private final Map<UUID, LongOpenHashSet> byPlayer = new ConcurrentHashMap<>();

    /**
     * The player's set of revealed positions, cleared first if it has grown past the cap. The cap
     * bounds memory for a player who has walked a long way; anything dropped is simply revealed
     * again the next time they come near it.
     */
    LongOpenHashSet forPlayer(UUID uuid) {
        LongOpenHashSet revealed = this.byPlayer.computeIfAbsent(uuid, k -> new LongOpenHashSet());
        if (revealed.size() > MAX_PER_PLAYER) {
            revealed.clear();
        }
        return revealed;
    }

    void forget(UUID uuid) {
        this.byPlayer.remove(uuid);
    }
}
