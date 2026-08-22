package mc.smpessentials.bluemap.layer;

import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.HtmlMarker;
import de.bluecolored.bluemap.api.markers.POIMarker;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Matching BlueMap maps to Minecraft dimensions. */
public final class Maps {
    private Maps() {
    }

    // BlueMap world ids are "<world path>#<dimension id>", so a map belongs to a dimension when
    // its world id carries that suffix.
    public static boolean isDimension(BlueMapMap map, ResourceKey<Level> dim) {
        return map.getWorld().getId().endsWith("#" + dim.identifier());
    }

    /**
     * Applies the configured zoom cutoff to an icon marker. BlueMap cannot scale icons down as the
     * camera pulls back, so the way to keep a zoomed out view readable is to stop drawing them.
     */
    public static POIMarker.Builder ranged(POIMarker.Builder builder) {
        double max = SmpConfig.BLUEMAP_ICON_MAX_DISTANCE;
        return max > 0 ? builder.maxDistance(max) : builder;
    }

    /** Same cutoff for the text labels untagged regions get, so both kinds fade out together. */
    public static HtmlMarker.Builder ranged(HtmlMarker.Builder builder) {
        double max = SmpConfig.BLUEMAP_ICON_MAX_DISTANCE;
        return max > 0 ? builder.maxDistance(max) : builder;
    }
}
