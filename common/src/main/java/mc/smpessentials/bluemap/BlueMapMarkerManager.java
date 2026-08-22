package mc.smpessentials.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.bluemap.layer.ClaimLayer;
import mc.smpessentials.bluemap.layer.HomeLayer;
import mc.smpessentials.bluemap.layer.MarkerLayer;
import mc.smpessentials.bluemap.layer.ShopLayer;
import mc.smpessentials.bluemap.layer.SpawnProtectionLayer;
import mc.smpessentials.bluemap.layer.WorldBorderLayer;
import mc.smpessentials.bluemap.layer.YoutubeLayer;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Set;

/**
 * Drives the marker layers: owns the layer list, uploads their icons once, and rebuilds whichever
 * layers are due.
 *
 * The rendering itself lives in the layer classes, so adding something to the map means adding a
 * layer rather than growing this file.
 */
public class BlueMapMarkerManager {

    private final BlueMapAPI api;
    private final IconAssets icons = new IconAssets();

    private final List<MarkerLayer> layers = List.of(
            new HomeLayer(),
            new ClaimLayer(),
            new ShopLayer(),
            new YoutubeLayer(),
            new WorldBorderLayer(),
            new SpawnProtectionLayer());

    public BlueMapMarkerManager(BlueMapAPI api) {
        this.api = api;
        icons.uploadAll(api, TagIcons.required());
    }

    /** Rebuilds every layer. The periodic safety net, and the first draw after startup. */
    public void updateAll(MinecraftServer server) {
        render(server, layers);
    }

    /** Rebuilds only the layers whose data changed. */
    public void updateDue(MinecraftServer server, Set<MarkerRefresh.Layer> due) {
        render(server, layers.stream().filter(l -> due.contains(l.refreshKey())).toList());
    }

    private void render(MinecraftServer server, List<MarkerLayer> toRender) {
        if (server == null || toRender.isEmpty())
            return;

        for (BlueMapMap map : api.getMaps()) {
            for (MarkerLayer layer : toRender) {
                try {
                    if (!layer.enabled()) {
                        map.getMarkerSets().remove(layer.id());
                        continue;
                    }

                    MarkerSet set = map.getMarkerSets().computeIfAbsent(layer.id(),
                            id -> MarkerSet.builder().label(layer.label()).defaultHidden(false).build());
                    set.getMarkers().clear();
                    layer.build(server, map, set, icons);

                    // A layer with nothing to show would otherwise leave an empty entry cluttering
                    // BlueMap's layer list.
                    if (set.getMarkers().isEmpty())
                        map.getMarkerSets().remove(layer.id());
                } catch (Exception e) {
                    SmpUtilsMod.LOGGER.error("[BlueMap] Layer {} failed on map {}: {}",
                            layer.id(), map.getId(), e.toString());
                }
            }
        }
    }

    public void cleanup() {
        for (BlueMapMap map : api.getMaps())
            for (MarkerLayer layer : layers)
                map.getMarkerSets().remove(layer.id());
        icons.clear();
    }
}
