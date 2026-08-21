package mc.smpessentials.serverlog;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Log4j appender that copies every event into a {@link LogBuffer} for the admin
 * panel terminal. Values are read out of the event immediately and no event is
 * retained, since log4j may recycle mutable events after append returns.
 *
 * This is the only class that knows about the logging framework: swapping the
 * capture mechanism means replacing this class alone.
 */
public final class LogTap extends AbstractAppender {

    // Dashboard HTTP worker threads. Their own request handling is noise the
    // operator did not ask to see, and it would grow every time the panel polls.
    private static final String SKIP_THREAD_PREFIX = "Dashboard-Conn";

    private final LogBuffer buffer;

    public LogTap(String name, LogBuffer buffer) {
        super(name, null, null, true, Property.EMPTY_ARRAY);
        this.buffer = buffer;
    }

    @Override
    public void append(LogEvent event) {
        String thread = event.getThreadName() == null ? "" : event.getThreadName();
        if (thread.startsWith(SKIP_THREAD_PREFIX)) return;

        String level  = event.getLevel() == null ? "INFO" : event.getLevel().name();
        String logger = shortName(event.getLoggerName());
        String msg    = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();
        buffer.add(event.getTimeMillis(), level, logger, thread, msg);

        Throwable thrown = event.getThrown();
        if (thrown != null) {
            buffer.add(event.getTimeMillis(), level, logger, thread,
                    thrown.getClass().getName() + (thrown.getMessage() == null ? "" : ": " + thrown.getMessage()));
        }
    }

    // Last dot segment, so net.minecraft.server.MinecraftServer reads as MinecraftServer.
    private static String shortName(String logger) {
        if (logger == null || logger.isEmpty()) return "";
        int dot = logger.lastIndexOf('.');
        return dot >= 0 && dot < logger.length() - 1 ? logger.substring(dot + 1) : logger;
    }
}
