package mc.smpessentials.chatfilter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory strike tracker for chat filter violations.
 * Resets on server restart — no persistence needed.
 */
public final class ChatFilterStrikeTracker {
    private static final Map<UUID, Integer> strikes = new ConcurrentHashMap<>();

    private ChatFilterStrikeTracker() {
    }

    /** Increments and returns the new strike count for the given player. */
    public static int increment(UUID playerId) {
        return strikes.merge(playerId, 1, Integer::sum);
    }

    /** Returns the current strike count (0 if none). */
    public static int get(UUID playerId) {
        return strikes.getOrDefault(playerId, 0);
    }

    /** Resets strikes for all players. */
    public static void reset() {
        strikes.clear();
    }

    /** Resets strikes for a specific player. */
    public static void reset(UUID playerId) {
        strikes.remove(playerId);
    }

    /** Returns an unmodifiable snapshot of all strike data. */
    public static Map<UUID, Integer> snapshot() {
        return Map.copyOf(strikes);
    }
}
