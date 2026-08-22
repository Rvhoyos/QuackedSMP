package mc.smpessentials.bluemap;

import java.util.EnumSet;
import java.util.Set;

/**
 * Decides when marker layers get rebuilt.
 *
 * The integration used to redraw everything on a fixed ten minute timer, so naming a region could
 * take ten minutes to show up. Instead, whatever changes the world marks its layer dirty, and the
 * next tick after a short settle delay rebuilds only that layer. The full sweep stays as a safety
 * net for anything that changes without telling us.
 *
 * Cost on the tick thread is one check against an empty EnumSet, and the settle delay means a
 * claim brush painting a whole area rebuilds once rather than once per chunk.
 */
public final class MarkerRefresh {
    private MarkerRefresh() {
    }

    public enum Layer {
        HOMES, CLAIMS, SHOPS, YOUTUBE, WORLD_BORDER, SPAWN_PROTECTION
    }

    // Long enough that a burst of edits collapses into one rebuild, short enough to feel immediate.
    private static final int SETTLE_TICKS = 40;

    private static final EnumSet<Layer> DIRTY = EnumSet.noneOf(Layer.class);
    private static int ticksSinceMark = 0;

    public static void markDirty(Layer layer) {
        synchronized (DIRTY) {
            DIRTY.add(layer);
            ticksSinceMark = 0;
        }
    }

    public static void markAllDirty() {
        synchronized (DIRTY) {
            DIRTY.addAll(EnumSet.allOf(Layer.class));
            ticksSinceMark = 0;
        }
    }

    /**
     * Layers due for a rebuild this tick, empty when there is nothing to do or the burst has not
     * settled yet. Claims the set, so a caller that gets a non-empty result must render it.
     */
    public static Set<Layer> pollDue() {
        synchronized (DIRTY) {
            if (DIRTY.isEmpty())
                return Set.of();
            if (++ticksSinceMark < SETTLE_TICKS)
                return Set.of();
            Set<Layer> due = EnumSet.copyOf(DIRTY);
            DIRTY.clear();
            ticksSinceMark = 0;
            return due;
        }
    }

    public static void reset() {
        synchronized (DIRTY) {
            DIRTY.clear();
            ticksSinceMark = 0;
        }
    }
}
