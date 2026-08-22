package mc.smpessentials.bluemap.layer;

import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import mc.smpessentials.bluemap.IconAssets;
import mc.smpessentials.bluemap.MarkerRefresh;
import net.minecraft.server.MinecraftServer;

/**
 * One toggleable group of markers on the map (homes, claims, shops and so on).
 *
 * Every layer follows the same shape: decide whether it is enabled, then repopulate its own
 * MarkerSet on the maps it belongs to. Keeping that shape in one interface is what stops the
 * marker manager from turning back into the place every feature's rendering piles up.
 *
 * Implementations read game state through the server passed to {@link #build}, so they run on
 * the server thread. Marker sets themselves are created by the caller for the same reason: the
 * marker map inside a set is concurrent, but the set collection on BlueMapMap is not ours to
 * make assumptions about.
 */
public interface MarkerLayer {

    /** Stable id, used as the MarkerSet key and to clean up on shutdown. */
    String id();

    /** Label shown in BlueMap's layer list. */
    String label();

    /** Which refresh signal rebuilds this layer. */
    MarkerRefresh.Layer refreshKey();

    /** Whether the layer is switched on in config. */
    boolean enabled();

    /**
     * Fills the layer's markers for one map. Called once per map, with that map's marker set
     * already created and cleared.
     */
    void build(MinecraftServer server, BlueMapMap map, MarkerSet set, IconAssets icons);
}
