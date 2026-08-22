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
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

import com.flowpowered.math.vector.Vector2d;

/** The vanilla spawn protection square, overworld only. */
public final class SpawnProtectionLayer implements MarkerLayer {

    @Override
    public String id() {
        return "quacksmp_spawnprotection";
    }

    @Override
    public String label() {
        return "Spawn Protection";
    }

    @Override
    public MarkerRefresh.Layer refreshKey() {
        return MarkerRefresh.Layer.SPAWN_PROTECTION;
    }

    @Override
    public boolean enabled() {
        return SmpConfig.BLUEMAP_SHOW_SPAWN_PROTECTION;
    }

    @Override
    public void build(MinecraftServer server, BlueMapMap map, MarkerSet set, IconAssets icons) {
        if (!(server instanceof DedicatedServer ds))
            return;
        if (!Maps.isDimension(map, server.overworld().dimension()))
            return;

        int radius = ds.spawnProtectionRadius();
        if (radius <= 0)
            return;

        BlockPos spawn = server.overworld().getRespawnData().pos();
        Shape shape = new Shape(
                new Vector2d(spawn.getX() - radius, spawn.getZ() - radius),
                new Vector2d(spawn.getX() + radius, spawn.getZ() - radius),
                new Vector2d(spawn.getX() + radius, spawn.getZ() + radius),
                new Vector2d(spawn.getX() - radius, spawn.getZ() + radius));

        Color fill = MarkerColors.parse(SmpConfig.BLUEMAP_SPAWN_PROTECTION_COLOR);
        set.put("spawn_protection", ShapeMarker.builder()
                .label("Spawn Protection (radius " + radius + ")")
                .shape(shape, spawn.getY())
                .fillColor(fill)
                .lineColor(MarkerColors.opaque(fill))
                .lineWidth(2)
                .depthTestEnabled(false)
                .build());
    }
}
