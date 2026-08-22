package mc.smpessentials.serverlog;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded store of the most recent log lines, oldest dropped first.
 *
 * Writes run on whatever thread happened to log, so {@link #add} is O(1) and
 * never blocks or touches disk. Sequence numbers are monotonic and never reused:
 * a reader that falls behind sees its cursor drop below {@link #tail} and can
 * resync instead of silently missing lines.
 *
 * Deliberately free of any logging framework types so the storage side does not
 * care where the lines came from.
 */
public final class LogBuffer {

    private final LogLine[] ring;
    // Entries currently stored, never above ring.length.
    private int count;
    // Index the next add() writes to.
    private int next;
    // Sequence of the newest entry, 0 while empty.
    private long seq;

    public LogBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.ring = new LogLine[capacity];
    }

    /** Stores a line under the next sequence number and returns that sequence. */
    public synchronized long add(long timeMillis, String level, String logger, String thread, String msg) {
        seq++;
        ring[next] = new LogLine(seq, timeMillis, level, logger, thread, msg);
        next = (next + 1) % ring.length;
        if (count < ring.length) count++;
        return seq;
    }

    /** Sequence of the newest stored line, 0 when nothing has been logged yet. */
    public synchronized long head() {
        return seq;
    }

    /** Sequence of the oldest stored line, 0 when empty. Anything below it has been dropped. */
    public synchronized long tail() {
        return count == 0 ? 0 : seq - count + 1;
    }

    /**
     * Lines newer than {@code since}, oldest first, capped at {@code max}. When
     * the caller is further behind than the cap, the oldest of the pending lines
     * are skipped and the returned lines start at a sequence above the caller's
     * cursor, which is how the client detects the gap.
     */
    public synchronized List<LogLine> since(long since, int max) {
        List<LogLine> out = new ArrayList<>();
        if (count == 0 || max <= 0) return out;

        long from = Math.max(since + 1, tail());
        if (from > seq) return out;
        long pending = seq - from + 1;
        if (pending > max) from += pending - max;

        for (long s = from; s <= seq; s++) out.add(ring[indexOf(s)]);
        return out;
    }

    // Ring slot holding the given sequence. The newest entry sits one slot behind next.
    private int indexOf(long sequence) {
        long offset = seq - sequence;
        return (int) (((next - 1 - offset) % ring.length + ring.length) % ring.length);
    }
}
