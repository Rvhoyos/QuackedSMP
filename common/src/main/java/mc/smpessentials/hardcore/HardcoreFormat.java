package mc.smpessentials.hardcore;

// Shared human-readable formatting for hardcore run times. Used by the command and sidebar;
// the dashboard formats millis client-side.
public final class HardcoreFormat {

    private HardcoreFormat() {}

    // Compact duration: "3d 4h", "5h 12m", "8m 3s", or "12s".
    public static String duration(long millis) {
        long totalSeconds = Math.max(0, millis) / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (days > 0)    return days + "d " + hours + "h";
        if (hours > 0)   return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }
}
