package mc.smpessentials.serverlog;

import mc.smpessentials.SmpUtilsMod;
import org.apache.logging.log4j.LogManager;

import java.util.List;

/**
 * Owns the live server log feed behind the admin panel terminal. Attaches a
 * {@link LogTap} to the root logger while the dashboard runs and detaches it
 * when the dashboard stops, so nothing is captured while nobody can read it.
 */
public final class ServerLogService {

    // Lines kept in memory. Enough to cover a server start or a burst of errors
    // without the buffer being a meaningful share of heap.
    private static final int CAPACITY = 1000;

    private static final String APPENDER_NAME = "QuackedSMP-DashboardTap";

    private static final ServerLogService INSTANCE = new ServerLogService();

    private final LogBuffer buffer = new LogBuffer(CAPACITY);
    private LogTap tap;

    private ServerLogService() {}

    public static ServerLogService get() {
        return INSTANCE;
    }

    /** Attaches the tap to the root logger. A second call while running does nothing. */
    public synchronized void start() {
        if (tap != null) return;
        org.apache.logging.log4j.core.Logger root = coreRootLogger();
        if (root == null) {
            SmpUtilsMod.LOGGER.warn("[Dashboard] Live log unavailable: root logger is not log4j-core");
            return;
        }
        LogTap t = new LogTap(APPENDER_NAME, buffer);
        t.start();
        root.addAppender(t);
        tap = t;
    }

    /** Detaches the tap. Safe when it was never started. */
    public synchronized void stop() {
        LogTap t = tap;
        tap = null;
        if (t == null) return;
        org.apache.logging.log4j.core.Logger root = coreRootLogger();
        if (root != null) root.removeAppender(t);
        t.stop();
    }

    /** Sequence of the newest captured line. */
    public long head() {
        return buffer.head();
    }

    /** Captured lines newer than the given sequence, oldest first, capped at max. */
    public List<LogLine> since(long since, int max) {
        return buffer.since(since, max);
    }

    // Null when something other than log4j-core is backing the logging API, in
    // which case there is no appender to attach to and the feature stays off.
    private static org.apache.logging.log4j.core.Logger coreRootLogger() {
        return LogManager.getRootLogger() instanceof org.apache.logging.log4j.core.Logger root ? root : null;
    }
}
