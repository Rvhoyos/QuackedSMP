package mc.smpessentials.teleport;

import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * In-memory FIFO teleport request registry with basic cooldowns and timeouts.
 */
public final class TeleportService {
    public static final int QUEUE_CAP = 5;
    public static final long TIMEOUT_MS = 60_000L;
    public static final long REQUESTER_COOLDOWN_MS = 10_000L;

    private static final Map<UUID, Deque<Request>> byTarget = new HashMap<>();
    private static final Set<UUID> requesterHasPending = new HashSet<>();
    private static final Map<UUID, Long> requesterCooldownUntil = new HashMap<>();

    private TeleportService() {}

    public static synchronized Result sendRequest(ServerPlayer requester, ServerPlayer target, long nowMs) {
        if (requester.getUUID().equals(target.getUUID())) return Result.SELF_TARGET;
        Long until = requesterCooldownUntil.get(requester.getUUID());
        if (until != null && until > nowMs) return Result.IN_COOLDOWN;
        if (requesterHasPending.contains(requester.getUUID())) return Result.ALREADY_PENDING;

        Deque<Request> q = byTarget.computeIfAbsent(target.getUUID(), k -> new ArrayDeque<>());
        pruneExpired(q, nowMs);
        if (q.size() >= QUEUE_CAP) return Result.TARGET_QUEUE_FULL;

        q.addLast(new Request(requester.getUUID(), nowMs));
        requesterHasPending.add(requester.getUUID());
        requesterCooldownUntil.put(requester.getUUID(), nowMs + REQUESTER_COOLDOWN_MS);
        return Result.OK;
    }

    public static synchronized Optional<UUID> acceptOldest(ServerPlayer target, long nowMs) {
        Deque<Request> q = byTarget.get(target.getUUID());
        if (q == null) return Optional.empty();
        pruneExpired(q, nowMs);
        Request r = q.pollFirst();
        if (r == null) return Optional.empty();
        requesterHasPending.remove(r.requester());
        return Optional.of(r.requester());
    }

    public static synchronized Optional<UUID> denyOldest(ServerPlayer target, long nowMs) {
        Deque<Request> q = byTarget.get(target.getUUID());
        if (q == null) return Optional.empty();
        pruneExpired(q, nowMs);
        Request r = q.pollFirst();
        if (r == null) return Optional.empty();
        requesterHasPending.remove(r.requester());
        return Optional.of(r.requester());
    }

    public static synchronized long getRemainingCooldownMs(UUID requester, long nowMs) {
        Long until = requesterCooldownUntil.get(requester);
        if (until == null || until <= nowMs) return 0L;
        return until - nowMs;
    }

    public static synchronized void clearForPlayer(UUID playerId) {
        requesterHasPending.remove(playerId);
        requesterCooldownUntil.remove(playerId);
        // Also remove from any target queues
        for (Deque<Request> q : byTarget.values()) {
            q.removeIf(req -> req.requester().equals(playerId));
        }
        // Remove empty queues
        byTarget.values().removeIf(Deque::isEmpty);
    }

    private static void pruneExpired(Deque<Request> q, long nowMs) {
        while (!q.isEmpty() && nowMs - q.peekFirst().createdAt() >= TIMEOUT_MS) {
            requesterHasPending.remove(q.pollFirst().requester());
        }
    }

    public enum Result { OK, SELF_TARGET, IN_COOLDOWN, ALREADY_PENDING, TARGET_QUEUE_FULL }

    public record Request(UUID requester, long createdAt) {}
}
