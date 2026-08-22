package mc.smpessentials.bluemap.layer;

import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.bluemap.IconAssets;
import mc.smpessentials.bluemap.MarkerHtml;
import mc.smpessentials.bluemap.MarkerRefresh;
import mc.smpessentials.bluemap.TagIcons;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player respawn points.
 *
 * Offline players only exist on disk, so their homes come from their .dat files. Those reads are
 * cached against the file's timestamp, since re-parsing every player's NBT on each refresh would
 * be real disk work for data that almost never changes.
 */
public final class HomeLayer implements MarkerLayer {

    private record Home(String playerName, BlockPos pos, ResourceKey<Level> dim) {
    }

    private record Cached(long lastModified, Home home) {
    }

    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "quacksmp_homes";
    }

    @Override
    public String label() {
        return "Player Homes";
    }

    @Override
    public MarkerRefresh.Layer refreshKey() {
        return MarkerRefresh.Layer.HOMES;
    }

    @Override
    public boolean enabled() {
        return SmpConfig.BLUEMAP_SHOW_HOMES;
    }

    @Override
    public void build(MinecraftServer server, BlueMapMap map, MarkerSet set, IconAssets icons) {
        String icon = icons.url(map, TagIcons.HOME);
        if (icon == null)
            return;

        for (var entry : collect(server)) {
            UUID id = entry.getKey();
            Home home = entry.getValue();
            if (!Maps.isDimension(map, home.dim()))
                continue;

            String name = MarkerHtml.escape(home.playerName());
            set.put(id + "_home", Maps.ranged(POIMarker.builder())
                    .label(home.playerName() + "'s Home")
                    .detail("<b>" + name + "</b>'s respawn point.")
                    .position(home.pos().getX() + 0.5, home.pos().getY() + 0.5, home.pos().getZ() + 0.5)
                    .icon(icon, 32, 32)
                    .build());
        }
    }

    private List<Map.Entry<UUID, Home>> collect(MinecraftServer server) {
        Map<UUID, Home> homes = new java.util.LinkedHashMap<>();

        for (var player : server.getPlayerList().getPlayers()) {
            var respawn = player.getRespawnConfig();
            if (respawn == null || respawn.respawnData() == null)
                continue;
            ResourceKey<Level> dim = respawn.respawnData().dimension();
            BlockPos pos = respawn.respawnData().pos();
            if (dim != null && pos != null)
                homes.put(player.getUUID(), new Home(player.getName().getString(), pos, dim));
        }

        File dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile();
        File[] files = dir.isDirectory() ? dir.listFiles((d, n) -> n.endsWith(".dat")) : null;
        if (files != null) {
            for (File file : files) {
                try {
                    UUID uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
                    if (homes.containsKey(uuid))
                        continue;

                    Cached cached = cache.get(uuid);
                    if (cached != null && cached.lastModified() == file.lastModified()) {
                        if (cached.home() != null)
                            homes.put(uuid, cached.home());
                        continue;
                    }

                    Home home = readHome(server, file, uuid);
                    cache.put(uuid, new Cached(file.lastModified(), home));
                    if (home != null)
                        homes.put(uuid, home);
                } catch (Exception e) {
                    SmpUtilsMod.LOGGER.warn("[BlueMap] Unreadable player data {}: {}", file.getName(), e.getMessage());
                }
            }
        }

        return new ArrayList<>(homes.entrySet());
    }

    private static Home readHome(MinecraftServer server, File file, UUID uuid) throws Exception {
        CompoundTag nbt = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());

        String playerName = server.services().nameToIdCache().get(uuid)
                .map(p -> p.name()).orElse(uuid.toString());

        var respawn = nbt.getCompound("respawn");
        if (respawn.isPresent()) {
            int[] pos = respawn.get().getIntArray("pos").orElse(new int[0]);
            if (pos.length >= 3) {
                return new Home(playerName, new BlockPos(pos[0], pos[1], pos[2]),
                        dimensionOf(respawn.get().getString("dimension").orElse("")));
            }
            return null;
        }

        // Pre-1.21 layout, kept so a world carried forward still shows its old homes.
        if (nbt.contains("SpawnX") && nbt.contains("SpawnY") && nbt.contains("SpawnZ")) {
            return new Home(playerName,
                    new BlockPos(nbt.getInt("SpawnX").orElse(0), nbt.getInt("SpawnY").orElse(0),
                            nbt.getInt("SpawnZ").orElse(0)),
                    dimensionOf(nbt.getString("SpawnDimension").orElse("")));
        }
        return null;
    }

    private static ResourceKey<Level> dimensionOf(String id) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
    }
}
