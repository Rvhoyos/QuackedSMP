package mc.smpessentials.serverlog;

/**
 * One captured server log line. The sequence is assigned by {@link LogBuffer},
 * increases forever and is never reused, so a reader can tell exactly which
 * lines it has already seen.
 */
public record LogLine(long seq, long timeMillis, String level, String logger, String thread, String msg) {}
