package mc.smpessentials.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.markers.HtmlMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import de.bluecolored.bluemap.api.BlueMapMap;
import net.minecraft.world.level.border.WorldBorder;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.claims.ClaimManager;
import mc.smpessentials.claims.model.ClaimData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector2d;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

// Manages BlueMap markers for player homes, claimed regions, and the world border.
// Uses BlueMap's AssetStorage for custom icons to avoid CSP restrictions on POI markers.
public class BlueMapMarkerManager {
    private final BlueMapAPI api;

    // Caches parsed NBT home data per UUID to avoid re-reading .dat files on every update tick.
    private final Map<UUID, CachedHome> homeCache = new java.util.concurrent.ConcurrentHashMap<>();

    // Parsed home data from an offline player's .dat file.
    private static class CachedHome {
        final long lastModified;
        final String playerName;
        final BlockPos pos;
        final ResourceKey<Level> dim;

        CachedHome(long lastModified, String playerName, BlockPos pos, ResourceKey<Level> dim) {
            this.lastModified = lastModified;
            this.playerName = playerName;
            this.pos = pos;
            this.dim = dim;
        }
    }

    public BlueMapMarkerManager(BlueMapAPI api) {
        this.api = api;
    }

    private static final String HOUSE_SVG = "<svg width=\"64\" height=\"64\" viewBox=\"0 0 64 64\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\"><defs><linearGradient id=\"roofGradient\" x1=\"0%\" y1=\"0%\" x2=\"0%\" y2=\"100%\"><stop offset=\"0%\" style=\"stop-color:#FF5252;stop-opacity:1\"/><stop offset=\"100%\" style=\"stop-color:#D32F2F;stop-opacity:1\"/></linearGradient><linearGradient id=\"wallGradient\" x1=\"0%\" y1=\"0%\" x2=\"0%\" y2=\"100%\"><stop offset=\"0%\" style=\"stop-color:#F5F5F5;stop-opacity:1\"/><stop offset=\"100%\" style=\"stop-color:#E0E0E0;stop-opacity:1\"/></linearGradient><dropShadow id=\"shadow\" dx=\"0\" dy=\"2\" stdDeviation=\"2\" flood-color=\"#000000\" flood-opacity=\"0.3\"/></defs><rect x=\"12\" y=\"32\" width=\"40\" height=\"24\" fill=\"url(#wallGradient)\" stroke=\"#BDBDBD\" stroke-width=\"1\"/><path d=\"M8 32L32 12L56 32H8Z\" fill=\"url(#roofGradient)\" stroke=\"#C62828\" stroke-width=\"1\"/><rect x=\"28\" y=\"44\" width=\"8\" height=\"12\" fill=\"#5D4037\"/><circle cx=\"34\" cy=\"50\" r=\"1\" fill=\"#FFD200\"/><rect x=\"18\" y=\"38\" width=\"6\" height=\"6\" fill=\"#81D4FA\" stroke=\"#4FC3F7\" stroke-width=\"0.5\"/><rect x=\"40\" y=\"38\" width=\"6\" height=\"6\" fill=\"#81D4FA\" stroke=\"#4FC3F7\" stroke-width=\"0.5\"/><rect x=\"42\" y=\"18\" width=\"6\" height=\"8\" fill=\"#757575\"/></svg>";

    // Re-uploads SVG assets and refreshes all marker sets on every BlueMap map based on current SmpConfig settings.
    public void updateAll() {
        // Upload custom icons to BlueMap's Web App Asset Storage
        for (BlueMapMap map : api.getMaps()) {
            try {
                if (!map.getAssetStorage().assetExists("quacksmp_house.svg")) {
                    try (java.io.OutputStream os = map.getAssetStorage().writeAsset("quacksmp_house.svg")) {
                        os.write(HOUSE_SVG.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception e) {
                SmpUtilsMod.LOGGER.error("Failed to write BlueMap quacksmp_house.svg asset for map " + map.getId(), e);
            }
        }

        if (SmpConfig.BLUEMAP_SHOW_HOMES) {
            updateHomes();
        }
        if (SmpConfig.BLUEMAP_SHOW_CLAIMS) {
            updateClaims();
        }
        if (SmpConfig.BLUEMAP_SHOW_WORLDBORDER) {
            updateWorldBorder();
        }
    }

    // Syncs home markers for online players and offline players (reading their .dat files with NBT caching).
    private void updateHomes() {
        MinecraftServer server = BlueMapIntegration.getServer();
        if (server == null)
            return;

        for (BlueMapMap map : api.getMaps()) {
            MarkerSet homesMarkerSet = map.getMarkerSets().computeIfAbsent("quacksmp_homes",
                    id -> MarkerSet.builder().label("Player Homes").defaultHidden(false).build());
            homesMarkerSet.getMarkers().clear();
        }

        // 1. Get active players
        for (var player : server.getPlayerList().getPlayers()) {
            var respawn = player.getRespawnConfig();
            if (respawn != null && respawn.respawnData() != null) {
                ResourceKey<Level> dim = respawn.respawnData().dimension();
                BlockPos spawn = respawn.respawnData().pos();
                if (dim != null && spawn != null) {
                    addHomeMarker(player.getUUID(), player.getName().getString(), spawn, dim);
                }
            }
        }

        // 2. Get offline players
        File playerDataDir = new File(
                server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).toFile(), "");
        if (playerDataDir.exists() && playerDataDir.isDirectory()) {
            File[] files = playerDataDir.listFiles((dir, name) -> name.endsWith(".dat"));
            if (files != null) {
                for (File file : files) {
                    try {
                        String nameWithoutExt = file.getName().substring(0, file.getName().length() - 4);
                        UUID uuid = UUID.fromString(nameWithoutExt);

                        // If player is online, we already processed them
                        if (server.getPlayerList().getPlayer(uuid) != null)
                            continue;

                        long lastModified = file.lastModified();
                        CachedHome cached = homeCache.get(uuid);
                        if (cached != null && cached.lastModified == lastModified) {
                            if (cached.pos != null && cached.dim != null) {
                                addHomeMarker(uuid, cached.playerName, cached.pos, cached.dim);
                            }
                            continue; // Skip the expensive NBT read, file hasn't changed
                        }

                        CompoundTag nbt = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());

                        // Try to look up username from cache, fallback to UUID string
                        String playerName = uuid.toString();
                        var profile = server.services().nameToIdCache().get(uuid).orElse(null);
                        if (profile != null && profile.name() != null) {
                            playerName = profile.name();
                        }

                        boolean foundHome = false;

                        // 1.21.11 RespawnConfig structure: respawn -> {dimension, pos: [x, y, z]}
                        var respawnOpt = nbt.getCompound("respawn");
                        if (respawnOpt.isPresent()) {
                            CompoundTag respawn = respawnOpt.get();
                            String dimStr = respawn.getString("dimension").orElse("");
                            if (respawn.contains("pos")) {
                                int[] posArr = respawn.getIntArray("pos").orElse(new int[0]);
                                if (posArr.length >= 3) {
                                    BlockPos pos = new BlockPos(posArr[0], posArr[1], posArr[2]);
                                    Identifier id = Identifier.parse(dimStr);
                                    ResourceKey<Level> dim = ResourceKey.create(
                                            net.minecraft.core.registries.Registries.DIMENSION, id);
                                    addHomeMarker(uuid, playerName, pos, dim);
                                    homeCache.put(uuid, new CachedHome(lastModified, playerName, pos, dim));
                                    foundHome = true;
                                }
                            }
                        } else if (nbt.contains("SpawnX") && nbt.contains("SpawnY") && nbt.contains("SpawnZ")
                                && nbt.contains("SpawnDimension")) {
                            // Legacy fallback
                            int x = nbt.getInt("SpawnX").orElse(0);
                            int y = nbt.getInt("SpawnY").orElse(0);
                            int z = nbt.getInt("SpawnZ").orElse(0);
                            String dimStr = nbt.getString("SpawnDimension").orElse("");

                            Identifier id = Identifier.parse(dimStr);
                            ResourceKey<Level> dim = ResourceKey.create(
                                    net.minecraft.core.registries.Registries.DIMENSION, id);
                            addHomeMarker(uuid, playerName, new BlockPos(x, y, z), dim);
                            homeCache.put(uuid, new CachedHome(lastModified, playerName, new BlockPos(x, y, z), dim));
                            foundHome = true;
                        }

                        if (!foundHome) {
                            homeCache.put(uuid, new CachedHome(lastModified, playerName, null, null));
                        }

                    } catch (Exception e) {
                        SmpUtilsMod.LOGGER.warn("Failed to read offline player data for BlueMap: " + file.getName(), e);
                    }
                }
            }
        }
    }

    // Places a house SVG POI marker on the correct BlueMap map for the given dimension.
    private void addHomeMarker(UUID owner, String playerName, BlockPos pos, ResourceKey<Level> dimension) {
        // Find map for dimension
        String dimId = dimension.identifier().toString();
        Optional<BlueMapMap> mapOpt = api.getMaps().stream()
                .filter(m -> m.getWorld().getId().endsWith("#" + dimId))
                .findFirst();

        if (mapOpt.isEmpty())
            return;

        // Ensure the MarkerSet for Homes exists for this specific map
        MarkerSet homesMarkerSet = mapOpt.get().getMarkerSets().computeIfAbsent("quacksmp_homes",
                id -> MarkerSet.builder().label("Player Homes").defaultHidden(false).build());

        String iconUrl = mapOpt.get().getAssetStorage().getAssetUrl("quacksmp_house.svg");

        POIMarker marker = POIMarker.builder()
                .label(playerName + "'s Home")
                .detail("<b>" + playerName + "</b>'s respawn point.")
                .position(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                .icon(iconUrl, 16, 16)
                .build();

        // Add marker. Key must be unique within the MarkerSet.
        homesMarkerSet.put(owner.toString() + "_home", marker);
    }

    // Reads ClaimedSavedData and draws 2D shape markers for each claimed chunk on the relevant BlueMap map.
    private void updateClaims() {
        MinecraftServer server = BlueMapIntegration.getServer();
        if (server == null)
            return;

        for (BlueMapMap map : api.getMaps()) {
            MarkerSet claimsMarkerSet = map.getMarkerSets().computeIfAbsent("quacksmp_claims",
                    id -> MarkerSet.builder().label("Claimed Regions").defaultHidden(false).build());
            claimsMarkerSet.getMarkers().clear();
        }

        for (ServerLevel level : server.getAllLevels()) {
            var allClaims = mc.smpessentials.claims.storage.ClaimedSavedData.get(level).listClaims(level);

            Map<UUID, List<ClaimData>> claimsByOwner = new HashMap<>();
            for (ClaimData cd : allClaims) {
                claimsByOwner.computeIfAbsent(cd.owner(), k -> new ArrayList<>()).add(cd);
            }

            for (var entry : claimsByOwner.entrySet()) {
                UUID owner = entry.getKey();
                List<ClaimData> claims = entry.getValue();

                String ownerName = owner.toString();
                var profile = server.services().nameToIdCache().get(owner).orElse(null);
                if (profile != null && profile.name() != null) {
                    ownerName = profile.name();
                }

                // operator check
                boolean isOp = false;
                if (profile != null) {
                    isOp = server.getPlayerList().isOp(profile);
                }

                boolean isTier = mc.smpessentials.tier.TierService.getTier(owner, server) >= 1;

                String hexColorStr = isOp ? SmpConfig.BLUEMAP_OP_CLAIM_COLOR
                        : (isTier ? SmpConfig.BLUEMAP_VIP_CLAIM_COLOR : SmpConfig.BLUEMAP_CLAIM_COLOR);
                Color fillColor = parseColor(hexColorStr);
                Color lineColor = new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), 1.0f); // Solid
                                                                                                                  // border

                // We group contiguous chunks together to draw them as discrete connected
                // regions
                List<Set<ChunkPos>> regions = findConnectedRegions(
                        claims.stream().map(c -> new ChunkPos(c.chunk())).collect(Collectors.toSet()));

                int regionIdx = 0;
                for (Set<ChunkPos> region : regions) {
                    drawRegion(server, level.dimension(), owner, ownerName, regionIdx++, region, isOp, isTier, fillColor,
                            lineColor);
                }
            }
        }
    }

    private void drawRegion(MinecraftServer server, ResourceKey<Level> dim, UUID owner, String ownerName, int regionIdx,
            Set<ChunkPos> regionChunks, boolean isOp, boolean isVip, Color fillColor, Color lineColor) {
        String dimId = dim.identifier().toString();
        Optional<BlueMapMap> mapOpt = api.getMaps().stream()
                .filter(m -> m.getWorld().getId().endsWith("#" + dimId))
                .findFirst();

        if (mapOpt.isEmpty())
            return;

        // Resolve the most popular Name across this connected region:
        Map<String, Integer> nameCounts = new HashMap<>();
        ServerLevel sLevel = server.getLevel(dim);
        if (sLevel != null) {
            for (ChunkPos cp : regionChunks) {
                var cdOpt = mc.smpessentials.claims.storage.ClaimedSavedData.get(sLevel).getClaim(sLevel, cp);
                if (cdOpt.isPresent() && cdOpt.get().name().isPresent() && !cdOpt.get().name().get().isBlank()) {
                    String customName = cdOpt.get().name().get();
                    nameCounts.put(customName, nameCounts.getOrDefault(customName, 0) + 1);
                }
            }
        }

        String bestName = null;
        int maxCount = 0;
        for (var entry : nameCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                bestName = entry.getKey();
            }
        }

        String markerLabel = (bestName != null) ? bestName : ownerName + "'s Claim";

        int chunkIdx = 0;
        for (ChunkPos cp : regionChunks) {
            Shape square = generateChunkShape(cp);

            ShapeMarker marker = ShapeMarker.builder()
                    .label(markerLabel)
                    .detail("Claim owned by <b>" + ownerName + "</b>")
                    .shape(square, 70) // Y=70 is generally surface level
                    .fillColor(fillColor)
                    .lineColor(lineColor)
                    .lineWidth(0) // No individual cell borders so they merge visually
                    .depthTestEnabled(false) // Draw map marker flat directly over the surface
                    .build();

            // Put specifically into this map's marker set
            mapOpt.get().getMarkerSets().get("quacksmp_claims")
                    .put(owner.toString() + "_" + regionIdx + "_" + chunkIdx++, marker);
        }

        // Add a floating text label in the center of the region if it's named
        if (bestName != null) {
            double avgX = 0;
            double avgZ = 0;
            for (ChunkPos cp : regionChunks) {
                avgX += cp.getMiddleBlockX();
                avgZ += cp.getMiddleBlockZ();
            }
            avgX /= regionChunks.size();
            avgZ /= regionChunks.size();

            HtmlMarker htmlMarker = HtmlMarker.builder()
                    .label(bestName)
                    .html("<div style='color:white; text-shadow: 2px 2px 4px black; font-weight:bold; font-family:sans-serif; white-space:nowrap; pointer-events:none;'>"
                            + bestName + "</div>")
                    .position(new Vector3d(avgX, 70.1, avgZ))
                    .anchor(new Vector2i(0, 0)) // Center the label
                    .build();

            mapOpt.get().getMarkerSets().get("quacksmp_claims")
                    .put(owner.toString() + "_" + regionIdx + "_label", htmlMarker);
        }
    }

    private Shape generateChunkShape(ChunkPos cp) {
        int startX = cp.getMinBlockX();
        int startZ = cp.getMinBlockZ();
        int endX = cp.getMaxBlockX() + 1; // inclusive bounds for rendering
        int endZ = cp.getMaxBlockZ() + 1;

        return new Shape(
                new Vector2d(startX, startZ),
                new Vector2d(endX, startZ),
                new Vector2d(endX, endZ),
                new Vector2d(startX, endZ));
    }

    private List<Set<ChunkPos>> findConnectedRegions(Set<ChunkPos> allOwnerChunks) {
        List<Set<ChunkPos>> regions = new ArrayList<>();
        Set<ChunkPos> visited = new HashSet<>();

        for (ChunkPos cp : allOwnerChunks) {
            if (!visited.contains(cp)) {
                Set<ChunkPos> currentRegion = new HashSet<>();
                Queue<ChunkPos> queue = new LinkedList<>();

                queue.add(cp);
                visited.add(cp);

                while (!queue.isEmpty()) {
                    ChunkPos curr = queue.poll();
                    currentRegion.add(curr);

                    // Check ADJACENT chunks (N, S, E, W)
                    int x = curr.x;
                    int z = curr.z;
                    ChunkPos[] neighbors = {
                            new ChunkPos(x + 1, z), new ChunkPos(x - 1, z),
                            new ChunkPos(x, z + 1), new ChunkPos(x, z - 1)
                    };

                    for (ChunkPos n : neighbors) {
                        if (allOwnerChunks.contains(n) && !visited.contains(n)) {
                            visited.add(n);
                            queue.add(n);
                        }
                    }
                }
                regions.add(currentRegion);
            }
        }
        return regions;
    }

    private Color parseColor(String hexARGB) {
        if (hexARGB == null || hexARGB.isEmpty())
            return new Color(0, 0, 0, 0.5f);
        if (hexARGB.startsWith("#"))
            hexARGB = hexARGB.substring(1);
        try {
            if (hexARGB.length() == 8) {
                int a = Integer.parseInt(hexARGB.substring(0, 2), 16);
                int r = Integer.parseInt(hexARGB.substring(2, 4), 16);
                int g = Integer.parseInt(hexARGB.substring(4, 6), 16);
                int b = Integer.parseInt(hexARGB.substring(6, 8), 16);
                return new Color(r, g, b, a / 255.0f);
            } else if (hexARGB.length() == 6) {
                int r = Integer.parseInt(hexARGB.substring(0, 2), 16);
                int g = Integer.parseInt(hexARGB.substring(2, 4), 16);
                int b = Integer.parseInt(hexARGB.substring(4, 6), 16);
                return new Color(r, g, b, 0.5f);
            }
        } catch (NumberFormatException ignored) {
        }
        return new Color(255, 0, 0, 0.5f); // fallback red
    }

    private void updateWorldBorder() {
        MinecraftServer server = BlueMapIntegration.getServer();
        if (server == null) return;

        ServerLevel overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) return;

        WorldBorder border = overworld.getWorldBorder();

        // Skip if the border is at the default unlimited size — no border has been set
        if (border.getSize() >= 5.9E7) {
            for (BlueMapMap map : api.getMaps())
                map.getMarkerSets().remove("quacksmp_worldborder");
            return;
        }

        double minX = border.getMinX();
        double maxX = border.getMaxX();
        double minZ = border.getMinZ();
        double maxZ = border.getMaxZ();

        Shape borderShape = new Shape(
                new Vector2d(minX, minZ),
                new Vector2d(maxX, minZ),
                new Vector2d(maxX, maxZ),
                new Vector2d(minX, maxZ));

        String overworldId = net.minecraft.world.level.Level.OVERWORLD.identifier().toString();
        for (BlueMapMap map : api.getMaps()) {
            if (!map.getWorld().getId().endsWith("#" + overworldId)) continue;

            MarkerSet markerSet = map.getMarkerSets().computeIfAbsent("quacksmp_worldborder",
                    id -> MarkerSet.builder().label("World Border").defaultHidden(false).build());
            markerSet.getMarkers().clear();

            ShapeMarker marker = ShapeMarker.builder()
                    .label("World Border")
                    .shape(borderShape, 70)
                    .fillColor(new Color(0, 0, 0, 0.0f))
                    .lineColor(parseColor(SmpConfig.BLUEMAP_WORLDBORDER_COLOR))
                    .lineWidth(3)
                    .depthTestEnabled(false)
                    .build();

            markerSet.put("world_border", marker);
        }
    }

    public void cleanup() {
        for (BlueMapMap map : api.getMaps()) {
            map.getMarkerSets().remove("quacksmp_homes");
            map.getMarkerSets().remove("quacksmp_claims");
            map.getMarkerSets().remove("quacksmp_worldborder");
        }
    }
}
