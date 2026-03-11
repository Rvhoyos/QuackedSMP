package mc.smpessentials.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.markers.HtmlMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import de.bluecolored.bluemap.api.BlueMapMap;
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

public class BlueMapMarkerManager {
    private final BlueMapAPI api;

    public BlueMapMarkerManager(BlueMapAPI api) {
        this.api = api;
    }

    public void updateAll() {
        SmpUtilsMod.LOGGER.info("BlueMap Maps: " + api.getMaps().stream()
                .map(m -> m.getId() + " (world: " + m.getWorld().getId() + ")").collect(Collectors.joining(", ")));
        if (SmpConfig.BLUEMAP_SHOW_HOMES) {
            updateHomes();
        }
        if (SmpConfig.BLUEMAP_SHOW_CLAIMS) {
            updateClaims();
        }
    }

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

                        CompoundTag nbt = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());

                        // Try to look up username from cache, fallback to UUID string
                        String playerName = uuid.toString();
                        var profile = server.services().nameToIdCache().get(uuid).orElse(null);
                        if (profile != null && profile.name() != null) {
                            playerName = profile.name();
                        }

                        // 1.21.11 RespawnConfig structure: respawn -> respawn_data -> {dimension, pos:
                        // [x, y, z]}
                        var respawnOpt = nbt.getCompound("respawn");
                        if (respawnOpt.isPresent()) {
                            CompoundTag respawn = respawnOpt.get();
                            var dataOpt = respawn.getCompound("respawn_data");
                            if (dataOpt.isPresent()) {
                                CompoundTag data = dataOpt.get();
                                String dimStr = data.getString("dimension").orElse("");
                                if (data.contains("pos")) {
                                    int[] posArr = data.getIntArray("pos").orElse(new int[0]);
                                    if (posArr.length >= 3) {
                                        BlockPos pos = new BlockPos(posArr[0], posArr[1], posArr[2]);
                                        Identifier id = Identifier.parse(dimStr);
                                        ResourceKey<Level> dim = ResourceKey.create(
                                                net.minecraft.core.registries.Registries.DIMENSION, id);
                                        addHomeMarker(uuid, playerName, pos, dim);
                                    }
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
                        }

                    } catch (Exception e) {
                        SmpUtilsMod.LOGGER.warn("Failed to read offline player data for BlueMap: " + file.getName(), e);
                    }
                }
            }
        }
    }

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

        POIMarker marker = POIMarker.builder()
                .label(playerName + "'s Home")
                .detail("<b>" + playerName + "</b>'s respawn point.")
                .position(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                .icon("data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjQiIGhlaWdodD0iNjQiIHZpZXdCb3g9IjAgMCA2NCA2NCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48ZGVmcz48bGluZWFyR3JhZGllbnQgaWQ9InJvb2ZHcmFkaWVudCIgeDE9IjAlIiB5MT0iMCUiIHgyPSIwJSIgeTI9IjEwMCUiPjxzdG9wIG9mZnNldD0iMCUiIHN0eWxlPSJzdG9wLWNvbG9yOiNGRjUyNTI7c3RvcC1vcGFjaXR5OjEiLz48c3RvcCBvZmZzZXQ9IjEwMCUiIHN0eWxlPSJzdG9wLWNvbG9yOiNEMzJGMkY7c3RvcC1vcGFjaXR5OjEiLz48L2xpbmVhckdyYWRpZW50PjxsaW5lYXJHcmFkaWVudCBpZD0id2FsbEdyYWRpZW50IiB4MT0iMCUiIHkxPSIwJSIgeDI9IjAlIiB5Mj0iMTAwJSI+PHN0b3Agb2Zmc2V0PSIwJSIgc3R5bGU9InN0b3AtY29sb3I6I0Y1RjVGNTtzdG9wLW9wYWNpdHk6MSIvPjxzdG9wIG9mZnNldD0iMTAwJSIgc3R5bGU9InN0b3AtY29sb3I6I0UwRTBFMDtzdG9wLW9wYWNpdHk6MSIvPjwvbGluZWFyR3JhZGllbnQ+PGRyb3BTaGFkb3cgaWQ9InNoYWRvdyIgZHg9IjAiIGR5PSIyIiBzdGREZXZpYXRpb249IjIiIGZsb29kLWNvbG9yPSIjMDAwMDAwIiBmbG9vZC1vcGFjaXR5PSIwLjMiLz48L2RlZnM+PHJlY3QgeD0iMTIiIHk9IjMyIiB3aWR0aD0iNDAiIGhlaWdodD0iMjQiIGZpbGw9InVybCgjd2FsbEdyYWRpZW50KSIgc3Ryb2tlPSIjQkRCREJEIiBzdHJva2Utd2lkdGg9IjEiLz48cGF0aCBkPSJNOCAzMkwzMiAxMkw1NiAzMkg4WiIgZmlsbD0idXJsKCNyb29mR3JhZGllbnQpIiBzdHJva2U9IiNDNjI4MjgiIHN0cm9rZS13aWR0aD0iMSIvPjxyZWN0IHg9IjI4IiB5PSI0NCIgd2lkdGg9IjgiIGhlaWdodD0iMTIiIGZpbGw9IiM1RDQwMzciLz48Y2lyY2xlIGN4PSIzNCIgY3k9IjUwIiByPSIxIiBmaWxsPSIjRkZEMjAwIi8+PHJlY3QgeD0iMTgiIHk9IjM4IiB3aWR0aD0iNiIgaGVpZ2h0PSI2IiBmaWxsPSIjODFENDRGQSIgc3Ryb2tlPSIjNEZDM0Y3IiBzdHJva2Utd2lkdGg9IjAuNSIvPjxyZWN0IHg9IjQwIiB5PSIzOCIgd2lkdGg9IjYiIGhlaWdodD0iNiIgZmlsbD0iIzgxRDRGQSIgc3Ryb2tlPSIjNEZDM0Y3IiBzdHJva2Utd2lkdGg9IjAuNSIvPjxyZWN0IHg9IjQyIiB5PSIxOCIgd2lkdGg9IjYiIGhlaWdodD0iOCIgZmlsbD0iIzc1NzU3NSIvPjwvc3ZnPg==",
                        24, 24)
                .anchor(new Vector2i(12, 12)) // Center of 24x24 icon
                .build();

        // Add marker. Key must be unique within the MarkerSet.
        homesMarkerSet.put(owner.toString() + "_home", marker);
    }

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
            ClaimManager mgr = ClaimManager.get(level);
            // Since we can't easily access the raw data list without reflection or a public
            // method,
            // we will need to retrieve all claims. Wait, ClaimedSavedData has
            // listClaims(level).
            // Let's use
            // mc.smpessentials.claims.storage.ClaimedSavedData.get(level).listClaims(level)
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

                boolean isVip = SmpConfig.VIPS.contains(ownerName);

                String hexColorStr = isOp ? SmpConfig.BLUEMAP_OP_CLAIM_COLOR
                        : (isVip ? SmpConfig.BLUEMAP_VIP_CLAIM_COLOR : SmpConfig.BLUEMAP_CLAIM_COLOR);
                Color fillColor = parseColor(hexColorStr);
                Color lineColor = new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), 1.0f); // Solid
                                                                                                                  // border

                // MVP Grouping algorithm: for now, map each chunk individually to ensure
                // correctness.
                // Advanced polygon merging algorithm omitted for time complexity,
                // drawing discrete ExtrudeMarkers per chunk scaling well enough.

                // We will group contiguous chunks by their ChunkPos in the future.
                // For now, render individual square regions. Wait, the user asked for named OP
                // regions.
                // Let's implement a simple flood-fill to group connected chunks.
                List<Set<ChunkPos>> regions = findConnectedRegions(
                        claims.stream().map(c -> new ChunkPos(c.chunk())).collect(Collectors.toSet()));

                int regionIdx = 0;
                for (Set<ChunkPos> region : regions) {
                    drawRegion(server, level.dimension(), owner, ownerName, regionIdx++, region, isOp, isVip, fillColor,
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

    public void cleanup() {
        for (BlueMapMap map : api.getMaps()) {
            map.getMarkerSets().remove("quacksmp_homes");
            map.getMarkerSets().remove("quacksmp_claims");
        }
    }
}
