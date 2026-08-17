package mc.smpessentials.timelapse;

/**
 * Decides how many bytes a single timelapse render may allocate. Deals only in
 * bytes: it knows nothing about pixels or downsampling ({@link MapCanvas} turns
 * a byte budget into a blocks-per-pixel factor).
 *
 * The ceiling is always the heap actually free at render time, so an idle server
 * (nearly the whole heap free) renders at full 1:1, while a populated one gets a
 * smaller budget. The admin override tightens only forced, players-online
 * captures: it is the RAM-vs-map-resolution dial for a server that never idles.
 */
final class HeapBudget {

    // Leave this much heap free so the render does not starve the concurrent
    // server tick and GC. Physical headroom, not a policy fraction of the heap.
    private static final long RESERVE = 256L * 1024 * 1024;
    // Never budget below this, so a momentary low-free reading cannot collapse a
    // capture to a near-empty image.
    private static final long MIN_BUDGET = 32L * 1024 * 1024;

    private HeapBudget() {}

    /**
     * Byte budget for one render. The free heap is always the hard ceiling; when
     * players are online and the admin set an override, it further tightens the
     * budget (and so raises downsampling). Idle captures ignore the override.
     *
     * @param overrideMb   configured cap in megabytes; 0 means no override
     * @param playersOnline whether players are online at render time
     */
    static long forRender(int overrideMb, boolean playersOnline) {
        Runtime rt = Runtime.getRuntime();
        long available = rt.maxMemory() - rt.totalMemory() + rt.freeMemory() - RESERVE;
        long budget = Math.max(MIN_BUDGET, available);
        if (playersOnline && overrideMb > 0) {
            budget = Math.min(budget, (long) overrideMb * 1024 * 1024);
        }
        return budget;
    }
}
