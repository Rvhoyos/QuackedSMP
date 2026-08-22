package mc.smpessentials.bluemap;

import de.bluecolored.bluemap.api.math.Color;

/** Parses the hex colour strings the config stores into BlueMap colours. */
public final class MarkerColors {
    private MarkerColors() {
    }

    private static final float DEFAULT_ALPHA = 0.5f;

    /** Accepts AARRGGBB or RRGGBB, with or without a leading #. */
    public static Color parse(String hex) {
        if (hex == null || hex.isEmpty())
            return new Color(0, 0, 0, DEFAULT_ALPHA);
        if (hex.startsWith("#"))
            hex = hex.substring(1);
        try {
            if (hex.length() == 8) {
                return new Color(
                        Integer.parseInt(hex.substring(2, 4), 16),
                        Integer.parseInt(hex.substring(4, 6), 16),
                        Integer.parseInt(hex.substring(6, 8), 16),
                        Integer.parseInt(hex.substring(0, 2), 16) / 255.0f);
            }
            if (hex.length() == 6) {
                return new Color(
                        Integer.parseInt(hex.substring(0, 2), 16),
                        Integer.parseInt(hex.substring(2, 4), 16),
                        Integer.parseInt(hex.substring(4, 6), 16),
                        DEFAULT_ALPHA);
            }
        } catch (NumberFormatException ignored) {
        }
        return new Color(255, 0, 0, DEFAULT_ALPHA);
    }

    /** Same colour at full opacity, for drawing a solid outline around a translucent fill. */
    public static Color opaque(Color c) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 1.0f);
    }
}
