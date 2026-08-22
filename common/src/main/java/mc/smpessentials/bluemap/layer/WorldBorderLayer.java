package mc.smpessentials.bluemap.layer;

import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import mc.smpessentials.bluemap.IconAssets;
import mc.smpessentials.bluemap.MarkerColors;
import mc.smpessentials.bluemap.MarkerRefresh;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;

import com.flowpowered.math.vector.Vector2d;

/** The world border outline, drawn on each dimension that has one set. */
public final class WorldBorderLayer implements MarkerLayer {

    // Vanilla's default border is effectively unbounded, so anything at or above this is "not set".
    private static final double UNBOUNDED = 5.9E7;

    @Override
    public String id() {
        return "quacksmp_worldborder";
    }

    @Override
    public String label() {
        return "World Border";
    }

    @Override
    public MarkerRefresh.Layer refreshKey() {
        return MarkerRefresh.Layer.WORLD_BORDER;
    }

    @Override
    public boolean enabled() {
        return SmpConfig.BLUEMAP_SHOW_WORLDBORDER;
    }

    @Override
    public void build(MinecraftServer server, BlueMapMap map, MarkerSet set, IconAssets icons) {
        for (ServerLevel level : server.getAllLevels()) {
            if (!Maps.isDimension(map, level.dimension()))
                continue;

            WorldBorder border = level.getWorldBorder();
            if (border.getSize() >= UNBOUNDED)
                return;

            Shape shape = new Shape(
                    new Vector2d(border.getMinX(), border.getMinZ()),
                    new Vector2d(border.getMaxX(), border.getMinZ()),
                    new Vector2d(border.getMaxX(), border.getMaxZ()),
                    new Vector2d(border.getMinX(), border.getMaxZ()));

            set.put("world_border", ShapeMarker.builder()
                    .label("World Border")
                    .shape(shape, 70)
                    .fillColor(new Color(0, 0, 0, 0.0f))
                    .lineColor(MarkerColors.parse(SmpConfig.BLUEMAP_WORLDBORDER_COLOR))
                    .lineWidth(3)
                    .depthTestEnabled(false)
                    .build());
            return;
        }
    }
}
