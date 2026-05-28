package mc.smpessentials.backup;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrency guard for the public world-snapshot download route.
 * Enforces a per-IP cap and a global cap. Slots are released the moment
 * the stream finishes (or fails); no timestamps, no cleanup thread.
 */
public final class PublicDownloadLimiter {

    private static final PublicDownloadLimiter INSTANCE = new PublicDownloadLimiter();

    private final AtomicInteger active = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Integer> activeByIp = new ConcurrentHashMap<>();

    private PublicDownloadLimiter() {}

    public static PublicDownloadLimiter get() { return INSTANCE; }

    /**
     * Attempts to acquire a slot. Returns {@code true} only if both per-IP and
     * global caps are still available. Caller MUST invoke {@link #release} exactly
     * once on success, and not at all on failure.
     */
    public boolean tryAcquire(String ip, int perIpCap, int globalCap) {
        if (ip == null || ip.isBlank()) return false;
        if (perIpCap < 1 || globalCap < 1) return false;

        final boolean[] acquired = { false };
        activeByIp.compute(ip, (k, v) -> {
            int current = v == null ? 0 : v;
            if (current >= perIpCap) return v;
            acquired[0] = true;
            return current + 1;
        });
        if (!acquired[0]) return false;

        int next = active.incrementAndGet();
        if (next > globalCap) {
            active.decrementAndGet();
            decrementIp(ip);
            return false;
        }
        return true;
    }

    /** Releases a slot previously acquired by {@link #tryAcquire}. Idempotent on blank/null. */
    public void release(String ip) {
        if (ip == null || ip.isBlank()) return;
        decrementIp(ip);
        active.decrementAndGet();
    }

    private void decrementIp(String ip) {
        activeByIp.compute(ip, (k, v) -> (v == null || v <= 1) ? null : v - 1);
    }
}
