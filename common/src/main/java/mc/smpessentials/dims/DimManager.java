package mc.smpessentials.dims;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import mc.smpessentials.mixin.MinecraftServerMixin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.TicketStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;


import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/*
 * Handles runtime creation, deletion, and restoration of custom dimensions.
 * Dimensions are injected into MinecraftServer.levels via a Mixin accessor so they are live
 * immediately without a restart. Supports generator types: overworld, nether, end, ether.
 * Ether uses a custom island density function (EtherIslandDensityFunction) with configurable
 * threshold, radius, and spacing params. Stored in generatorConfig alongside biomes.
 * Each custom dim gets a portal frame block (default glowstone); right-click with water bucket
 * to activate. The generatorConfig string is persisted in DimSavedData for restart recovery.
 */
public final class DimManager {

    private static final Logger LOGGER = LogManager.getLogger("DimManager");

    // Dims queued for removal from level.dat on next server stop (see scrubLevelDat)
    private static final Set<String> pendingLevelDatRemovals =
            Collections.synchronizedSet(new HashSet<>());

    // Tracks which loaded dims are ether-type to avoid SavedData lookups on every tick
    private static final Set<String> etherDimIds =
            Collections.synchronizedSet(new HashSet<>());

    // True if the dim is currently loaded as ether-type; backed by an in-memory set updated on create/destroy/restore.
    public static boolean isEtherDim(String dimId) {
        return etherDimIds.contains(dimId);
    }

    // Records where the entity entered the custom dim so the return trip lands in the right spot.
    public static void saveReturnPos(Entity entity, BlockPos portalPos, MinecraftServer server) {
        EtherReturnData.get(server).record(entity.getUUID(), portalPos);
    }

    // Returns (and clears) the stored return position, falling back to overworld spawn.
    public static BlockPos popReturnPos(Entity entity, MinecraftServer server) {
        BlockPos stored = EtherReturnData.get(server).remove(entity.getUUID());
        return stored != null ? stored : server.overworld().getRespawnData().pos();
    }

    private DimManager() {}

    // Returns null on success or an error message on failure.
    public static String create(MinecraftServer server, String id, String generatorType,
                                 Optional<String> generatorConfig) {
        return createLevel(server, id, generatorType, generatorConfig, true);
    }

    // Evicts all players, saves, closes, and removes the dimension. Deletes chunk data, datapack
    // JSON, and BlueMap map config so it does not re-register on restart. Queues a level.dat scrub
    // for server stop. Returns null on success or an error message on failure.
    public static String destroy(MinecraftServer server, String id) {
        Identifier loc;
        try {
            loc = Identifier.parse(id);
        } catch (Exception e) {
            return "Invalid resource location: " + id;
        }

        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, loc);

        if (dimKey.equals(Level.OVERWORLD) || dimKey.equals(Level.NETHER) || dimKey.equals(Level.END)) {
            return "Cannot delete vanilla dimensions.";
        }

        MinecraftServerMixin accessor = (MinecraftServerMixin) server;
        Map<ResourceKey<Level>, ServerLevel> levels = accessor.getLevels();

        ServerLevel level = levels.get(dimKey);
        if (level == null) {
            return "Dimension not found: " + id;
        }

        // Evict all online players to overworld spawn
        ServerLevel overworld = server.overworld();
        BlockPos spawnPos = overworld.getRespawnData().pos();
        List<ServerPlayer> occupants = List.copyOf(level.players());
        for (ServerPlayer player : occupants) {
            BlockPos safe = player.adjustSpawnLocation(overworld, spawnPos);
            player.teleportTo(overworld,
                    safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                    Set.of(), player.getYRot(), player.getXRot(), false);
        }

        // Relocate any OFFLINE players whose saved Dimension tag references this dim.
        // Without this, the server crashes (ArrayIndexOutOfBoundsException in
        // LoadingChunkTracker) when an offline player who was last in this dim tries
        // to reconnect after the dimension no longer exists.
        relocateOfflinePlayers(server, dimKey.identifier().toString());

        // Save and close
        level.save(null, true, false);
        try {
            level.close();
        } catch (IOException e) {
            LOGGER.error("Error closing dimension {}: {}", id, e.getMessage());
        }

        levels.remove(dimKey);
        etherDimIds.remove(loc.toString());
        DimSavedData.get(server).remove(id);
        // Queue this dim for removal from level.dat on the next server stop.
        // Vanilla bakes datapack-registered dimensions into level.dat on first load and
        // re-registers them from there on subsequent restarts even if the datapack JSON
        // is deleted. scrubLevelDat() patches the file after the server's own save completes.
        pendingLevelDatRemovals.add(loc.toString());
        // Force-write to disk immediately so the deletion survives a crash or fast restart
        server.overworld().getDataStorage().saveAndJoin();

        // Delete the dimension's world folder so recreating it starts with fresh terrain.
        // Without this, old chunk data persists on disk and loads instead of regenerating.
        Path dimFolder = accessor.getStorageSource().getDimensionPath(dimKey);
        if (java.nio.file.Files.exists(dimFolder)) {
            try (var stream = java.nio.file.Files.walk(dimFolder)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                      .forEach(p -> {
                          try { java.nio.file.Files.delete(p); }
                          catch (IOException ex) { LOGGER.warn("Could not delete {}: {}", p, ex.getMessage()); }
                      });
            } catch (IOException e) {
                LOGGER.error("Failed to delete dimension folder {}: {}", dimFolder, e.getMessage());
            }
        }

        // Delete the datapack dimension JSON, if any, so the dim does not re-register on restart.
        // Datapack-defined dims live at: <worldRoot>/datapacks/<packName>/data/<ns>/dimension/<path>.json
        Path datapacks = server.getWorldPath(LevelResource.ROOT).resolve("datapacks");
        if (java.nio.file.Files.isDirectory(datapacks)) {
            Path dimJsonRelative = java.nio.file.Paths.get(
                    "data", loc.getNamespace(), "dimension", loc.getPath() + ".json");
            try (var packStream = java.nio.file.Files.list(datapacks)) {
                packStream.filter(java.nio.file.Files::isDirectory).forEach(pack -> {
                    Path dimJson = pack.resolve(dimJsonRelative);
                    if (java.nio.file.Files.exists(dimJson)) {
                        try {
                            java.nio.file.Files.delete(dimJson);
                            LOGGER.info("Deleted datapack dimension file: {}", dimJson);
                        } catch (IOException ex) {
                            LOGGER.warn("Could not delete datapack dimension file {}: {}", dimJson, ex.getMessage());
                        }
                    }
                });
            } catch (IOException e) {
                LOGGER.error("Failed to scan datapacks for dimension file: {}", e.getMessage());
            }
        }

        mc.smpessentials.bluemap.BlueMapIntegration.onDimDeleted(server, dimKey);

        return null;
    }

    // Scans all offline player .dat files and resets the Dimension tag to
    // minecraft:overworld for any player saved in the given dimension.
    // This prevents a fatal server crash when the player reconnects.
    private static void relocateOfflinePlayers(MinecraftServer server, String deletedDimId) {
        Path playerDataDir = server.getWorldPath(LevelResource.ROOT).resolve("playerdata");
        if (!java.nio.file.Files.isDirectory(playerDataDir)) return;

        // Collect UUIDs of currently online players — their data is managed in memory.
        Set<String> onlineUuids = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            onlineUuids.add(p.getStringUUID());
        }

        try (DirectoryStream<Path> stream = java.nio.file.Files.newDirectoryStream(playerDataDir, "*.dat")) {
            for (Path datFile : stream) {
                String fileName = datFile.getFileName().toString();
                // Skip backups and online players
                if (fileName.endsWith("_old")) continue;
                String uuid = fileName.replace(".dat", "");
                if (onlineUuids.contains(uuid)) continue;

                try {
                    CompoundTag root = NbtIo.readCompressed(datFile, NbtAccounter.unlimitedHeap());
                    Optional<String> dimOpt = root.getString("Dimension");
                    if (dimOpt.isEmpty()) continue;
                    String savedDim = dimOpt.get();
                    if (!savedDim.equals(deletedDimId)) continue;

                    // Rewrite to overworld
                    root.putString("Dimension", "minecraft:overworld");
                    NbtIo.writeCompressed(root, datFile);
                    LOGGER.info("Relocated offline player {} from deleted dim {} to overworld",
                            uuid, deletedDimId);
                } catch (Exception e) {
                    LOGGER.warn("Failed to check/relocate player data {}: {}", datFile, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan playerdata directory: {}", e.getMessage());
        }
    }

    // Patches level.dat after the server's own save to remove deleted dim IDs from
    // WorldGenSettings.dimensions. Must be called from the server-stopped event.
    public static void scrubLevelDat(MinecraftServer server) {
        if (pendingLevelDatRemovals.isEmpty()) return;

        Path levelDat = server.getWorldPath(LevelResource.ROOT).resolve("level.dat");
        try {
            CompoundTag root = NbtIo.readCompressed(levelDat, NbtAccounter.unlimitedHeap());
            // getCompound returns Optional<CompoundTag> in 1.21.11
            Optional<CompoundTag> dimsOpt = root.getCompound("Data")
                    .flatMap(d -> d.getCompound("WorldGenSettings"))
                    .flatMap(w -> w.getCompound("dimensions"));

            if (dimsOpt.isEmpty()) return;
            CompoundTag dimensions = dimsOpt.get();

            boolean modified = false;
            for (String dimId : pendingLevelDatRemovals) {
                if (dimensions.contains(dimId)) {
                    dimensions.remove(dimId);
                    LOGGER.info("Scrubbed {} from level.dat WorldGenSettings", dimId);
                    modified = true;
                }
            }

            if (modified) {
                NbtIo.writeCompressed(root, levelDat);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scrub deleted dimensions from level.dat: {}", e.getMessage());
        } finally {
            pendingLevelDatRemovals.clear();
        }
    }

    // Re-creates all custom dimensions from SavedData on server start.
    // After restoring, repairs any player data files that reference dimensions
    // not present in the server's levels map (safety net for servers that already
    // have corrupted data from a previous version that lacked the destroy() fix).
    public static void restoreAll(MinecraftServer server) {
        List<DimSavedData.DimEntry> entries = DimSavedData.get(server).getEntries();
        for (DimSavedData.DimEntry entry : entries) {
            String error = createLevel(server, entry.id(), entry.generatorType(),
                    entry.generatorConfig(), false);
            if (error != null) {
                LOGGER.warn("Failed to restore dimension {}: {}", entry.id(), error);
            } else {
                LOGGER.info("Restored custom dimension: {}", entry.id());
            }
        }
        repairOrphanedPlayers(server);
    }

    // Startup safety net: scans all offline player .dat files and relocates any
    // whose saved Dimension does not exist in the server's active levels map.
    // This handles the case where a previous version of the mod deleted a dim
    // without fixing offline player data, bricking the server on their next login.
    private static void repairOrphanedPlayers(MinecraftServer server) {
        Map<ResourceKey<Level>, ServerLevel> levels =
                ((MinecraftServerMixin) server).getLevels();
        Path playerDataDir = server.getWorldPath(LevelResource.ROOT).resolve("playerdata");
        if (!java.nio.file.Files.isDirectory(playerDataDir)) return;

        try (DirectoryStream<Path> stream = java.nio.file.Files.newDirectoryStream(playerDataDir, "*.dat")) {
            for (Path datFile : stream) {
                String fileName = datFile.getFileName().toString();
                if (fileName.endsWith("_old")) continue;

                try {
                    CompoundTag root = NbtIo.readCompressed(datFile, NbtAccounter.unlimitedHeap());
                    Optional<String> dimOpt = root.getString("Dimension");
                    if (dimOpt.isEmpty()) continue;
                    String savedDim = dimOpt.get();

                    // Check if this dimension exists in the server's active levels
                    ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                            Identifier.parse(savedDim));
                    if (levels.containsKey(dimKey)) continue;

                    // Dimension doesn't exist — relocate to overworld
                    root.putString("Dimension", "minecraft:overworld");
                    NbtIo.writeCompressed(root, datFile);
                    String uuid = fileName.replace(".dat", "");
                    LOGGER.warn("Repaired orphaned player {} — was in non-existent dim {}, "
                            + "relocated to overworld", uuid, savedDim);
                } catch (Exception e) {
                    LOGGER.warn("Failed to check/repair player data {}: {}",
                            datFile, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan playerdata for orphaned dimensions: {}", e.getMessage());
        }
    }

    // Returns all currently active dimension keys, vanilla and custom.
    public static List<ResourceKey<Level>> listAll(MinecraftServer server) {
        return new ArrayList<>(((MinecraftServerMixin) server).getLevels().keySet());
    }

    // Delegates to the hint overload using overworld spawn XZ as the position hint.
    public static BlockPos findSpawnOrigin(MinecraftServer server, ServerLevel dest) {
        BlockPos owSpawn = server.overworld().getRespawnData().pos();
        return findSpawnOrigin(server, dest, owSpawn.getX(), owSpawn.getZ());
    }

    // hintX/hintZ should be the overworld portal XZ so the custom-dim portal aligns with it.
    public static BlockPos findSpawnOrigin(MinecraftServer server, ServerLevel dest, int hintX, int hintZ) {
        if (dest.dimensionTypeRegistration().value().hasCeiling()) {
            return new BlockPos(hintX, 64, hintZ);
        }
        // Flat dims: compute spawn height directly from the generator — it doesn't require
        // loaded chunks, and flat terrain can sit at negative Y where the > 0 heightmap
        // check would incorrectly treat it as an unloaded chunk.
        if (dest.getChunkSource().getGenerator() instanceof FlatLevelSource flat) {
            return new BlockPos(hintX, flat.getSpawnHeight(dest), hintZ);
        }
        // Noise-based dims: probe the heightmap. getHeight returns 0 for unloaded chunks.
        int surfaceY = dest.getHeight(Heightmap.Types.WORLD_SURFACE, hintX, hintZ);
        if (surfaceY > 0) {
            return new BlockPos(hintX, surfaceY, hintZ);
        }
        int islandY = dest.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0);
        return new BlockPos(0, islandY > 0 ? islandY : 80, 0);
    }

    // Generates a small floating island at spawnPos if terrain is missing, then ensures a return portal.
    public static void ensureSpawnPlatform(ServerLevel dest, BlockPos spawnPos) {
        BlockPos surface = spawnPos.below();
        if (!dest.getBlockState(surface).isAir()) {
            // Terrain already exists at this position — no custom island needed, but still
            // ensure the return portal is placed on top of whatever terrain is here.
            ensureReturnPortal(dest, spawnPos);
            return;
        }

        int x = surface.getX(), y = surface.getY(), z = surface.getZ();

        // Island
        int[][] oval  = {{0,0},{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1},{2,0},{-2,0},{0,2},{0,-2}};
        int[][] sq3   = {{0,0},{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
        int[][] cross = {{0,0},{1,0},{-1,0},{0,1},{0,-1}};

        for (int[] d : oval)  placeIfAir(dest, new BlockPos(x+d[0], y,   z+d[1]), Blocks.GRASS_BLOCK);
        for (int[] d : sq3)   placeIfAir(dest, new BlockPos(x+d[0], y-1, z+d[1]), Blocks.DIRT);
        for (int[] d : sq3)   placeIfAir(dest, new BlockPos(x+d[0], y-2, z+d[1]), Blocks.DIRT);
        for (int[] d : sq3)   placeIfAir(dest, new BlockPos(x+d[0], y-3, z+d[1]), Blocks.STONE);
        for (int[] d : cross) placeIfAir(dest, new BlockPos(x+d[0], y-4, z+d[1]), Blocks.STONE);
        placeIfAir(dest, new BlockPos(x, y-5, z), Blocks.STONE);

        ensureReturnPortal(dest, spawnPos);
    }

    // Places a 4×5 return portal at spawnPos using the dim's registered frame block (default glowstone).
    // Skips if a NETHER_PORTAL block already exists in that column.
    public static void ensureReturnPortal(ServerLevel dest, BlockPos spawnPos) {
        String dimId = dest.dimension().identifier().toString();
        Optional<String> portalBlockId = DimSavedData.get(dest.getServer())
                .getEntry(dimId).flatMap(DimSavedData.DimEntry::portalBlock);
        Block frameBlock = portalBlockId
                .map(id -> BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)))
                .orElse(Blocks.GLOWSTONE);

        int x = spawnPos.getX(), y = spawnPos.getY(), z = spawnPos.getZ();
        int px = x - 1; // left edge of the 4-wide frame
        // Scan the interior column (px+1) for NETHER_PORTAL blocks rather than the frame block.
        // NETHER_PORTAL only exists where we explicitly placed it, so this has no false positives
        // from terrain — unlike scanning for the frame block, which can appear in natural terrain
        // (e.g. dirt in a flat dim) and would cause every call to exit early with no portal placed.
        for (int scanY = dest.getMinY(); scanY <= dest.getMaxY(); scanY++) {
            if (dest.getBlockState(new BlockPos(px + 1, scanY, z)).is(Blocks.NETHER_PORTAL)) return;
        }

        // Frame: bottom at y, pillars y→y+4, top at y+4
        // Force-place frame blocks (not placeIfAir) so the frame is always complete even if
        // existing structure blocks are in the way — an incomplete frame breaks portal routing.
        for (int i = 0; i < 4; i++) dest.setBlock(new BlockPos(px+i, y,   z), frameBlock.defaultBlockState(), 3);
        for (int dy = 0; dy <= 4; dy++) {
            dest.setBlock(new BlockPos(px,   y+dy, z), frameBlock.defaultBlockState(), 3);
            dest.setBlock(new BlockPos(px+3, y+dy, z), frameBlock.defaultBlockState(), 3);
        }
        for (int i = 0; i < 4; i++) dest.setBlock(new BlockPos(px+i, y+4, z), frameBlock.defaultBlockState(), 3);

        // Interior: y+1 → y+3 (AXIS=X so mixin looks EAST/WEST for glowstone pillars)
        BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState()
                .setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
        for (int dy = 1; dy <= 3; dy++) {
            dest.setBlock(new BlockPos(px+1, y+dy, z), portal, 3);
            dest.setBlock(new BlockPos(px+2, y+dy, z), portal, 3);
        }
    }

    private static void placeIfAir(ServerLevel level, BlockPos pos, Block block) {
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, block.defaultBlockState(), 3);
        }
    }

    // -------------------------------------------------------------------------

    // persist=true when creating new (writes SavedData); false when restoring on startup.
    private static String createLevel(MinecraftServer server, String id, String generatorType,
                                       Optional<String> generatorConfig, boolean persist) {
        Identifier loc;
        try {
            loc = Identifier.parse(id);
        } catch (Exception e) {
            return "Invalid resource location: " + id;
        }

        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, loc);

        MinecraftServerMixin accessor = (MinecraftServerMixin) server;
        Map<ResourceKey<Level>, ServerLevel> levels = accessor.getLevels();

        if (levels.containsKey(dimKey)) {
            return "Dimension already exists: " + id;
        }

        LevelStem stem = resolveStem(server, generatorType, generatorConfig);
        if (stem == null) {
            return "Unknown generator type: " + generatorType
                    + ". Use: overworld, nether, end, ether";
        }

        ServerLevelData overworldData = server.getWorldData().overworldData();
        DerivedLevelData derivedData = new DerivedLevelData(server.getWorldData(), overworldData);

        long seed = server.getWorldData().worldGenOptions().seed();
        long biomeZoomSeed = BiomeManager.obfuscateSeed(seed);

        Executor executor = accessor.getExecutor();
        LevelStorageSource.LevelStorageAccess storageSource = accessor.getStorageSource();

        ServerLevel newLevel = new ServerLevel(
                server, executor, storageSource, derivedData, dimKey, stem,
                false, biomeZoomSeed, ImmutableList.of(), false, null);

        // Match what vanilla does in MinecraftServer.createLevels() + prepareLevels():
        // 1) Set world border max size so mayInteract() and isWithinBounds() work
        newLevel.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());

        levels.put(dimKey, newLevel);
        server.getPlayerList().addWorldborderListener(newLevel);

        // 2) Activate any persisted forced-chunk tickets. Without this the chunk
        //    system never processes chunk load requests for this dimension, so
        //    blocks cannot be broken (getBlockState returns stale/wrong data on
        //    the server side even though the client received chunks correctly).
        TicketStorage ticketStorage = newLevel.getDataStorage().get(TicketStorage.TYPE);
        if (ticketStorage != null) {
            ticketStorage.activateAllDeactivatedTickets();
        }

        if ("ether".equals(generatorType)) {
            etherDimIds.add(dimKey.identifier().toString());
        }

        // Pre-generate the spawn island for ether dims on the next tick, by which time the
        // chunk generator will have had a chance to initialise the spawn chunk.
        if ("ether".equals(generatorType)) {
            final ServerLevel ethLevel = newLevel;
            server.execute(() -> {
                BlockPos spawnPos = findSpawnOrigin(server, ethLevel);
                ensureSpawnPlatform(ethLevel, spawnPos);
            });
        }

        if (persist) {
            DimSavedData saved = DimSavedData.get(server);
            String canonicalId = dimKey.identifier().toString();
            saved.add(canonicalId, generatorType, generatorConfig);
            mc.smpessentials.bluemap.BlueMapIntegration.onDimCreated(server, dimKey);
            // Assign glowstone as the default portal frame block if it is not already claimed
            // by another dim. Admins can override at any time with /dim setportal.
            String glowstoneId = "minecraft:glowstone";
            if (saved.getDimForPortalBlock(glowstoneId).isEmpty()) {
                saved.setPortalBlock(canonicalId, glowstoneId);
            }
        }

        return null;
    }

    // Returns null if generatorType is unrecognised.
    @SuppressWarnings("unchecked")
    private static LevelStem resolveStem(MinecraftServer server, String generatorType,
                                          Optional<String> generatorConfig) {
        var stemReg = (net.minecraft.core.Registry<LevelStem>)
                server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);

        return switch (generatorType.toLowerCase()) {
            case "nether" -> {
                LevelStem nether = stemReg.getValue(LevelStem.NETHER);
                if (nether == null) yield null;
                Holder<NoiseGeneratorSettings> netherNoise = server.registryAccess()
                        .lookupOrThrow(Registries.NOISE_SETTINGS)
                        .getOrThrow(NoiseGeneratorSettings.NETHER);
                yield new LevelStem(nether.type(), new NoiseBasedChunkGenerator(
                        nether.generator().getBiomeSource(), netherNoise));
            }
            case "end" -> {
                LevelStem end = stemReg.getValue(LevelStem.END);
                if (end == null) yield null;
                Holder<NoiseGeneratorSettings> endNoise = server.registryAccess()
                        .lookupOrThrow(Registries.NOISE_SETTINGS)
                        .getOrThrow(NoiseGeneratorSettings.END);
                yield new LevelStem(end.type(), new NoiseBasedChunkGenerator(
                        end.generator().getBiomeSource(), endNoise));
            }

            case "overworld" -> {
                LevelStem ow = stemReg.getValue(LevelStem.OVERWORLD);
                if (ow == null) yield null;
                String config = generatorConfig.orElse("");
                Holder<NoiseGeneratorSettings> owNoise = server.registryAccess()
                        .lookupOrThrow(Registries.NOISE_SETTINGS)
                        .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
                if (config.startsWith("flat ")) {
                    yield buildFlatStem(server, config.substring(5), ow);
                }
                if (config.startsWith("biomes ")) {
                    String biomeListStr = config.substring(7).trim();
                    yield new LevelStem(ow.type(), new NoiseBasedChunkGenerator(
                            parseBiomeSource(server, biomeListStr.isEmpty() ? null : biomeListStr), owNoise));
                }
                // Default: fresh overworld generator — never reuse the live overworld's LevelStem
                // instance, as sharing a ChunkGenerator between two ServerLevels corrupts terrain.
                yield new LevelStem(ow.type(), new NoiseBasedChunkGenerator(
                        parseBiomeSource(server, null), owNoise));
            }

            // Ether: custom island density function + overworld DimensionType + configurable biomes
            case "ether" -> {
                LevelStem ow = stemReg.getValue(LevelStem.OVERWORLD);
                if (ow == null) yield null;
                String config = generatorConfig.orElse("");

                // Extract biomes (must be last in config string) -- backward compatible
                String biomeListStr = null;
                int biomesIdx = config.indexOf("biomes ");
                if (biomesIdx >= 0) {
                    String biomes = config.substring(biomesIdx + 7).trim();
                    biomeListStr = biomes.isEmpty() ? null : biomes;
                    config = config.substring(0, biomesIdx).trim();
                }

                // Parse ether-specific island params from remaining config
                float threshold = EtherIslandDensityFunction.DEFAULT_THRESHOLD;
                float minRadius = EtherIslandDensityFunction.DEFAULT_MIN_RADIUS;
                float maxRadius = EtherIslandDensityFunction.DEFAULT_MAX_RADIUS;
                int spacing = EtherIslandDensityFunction.DEFAULT_SPACING;
                if (!config.isBlank()) {
                    String[] tokens = config.split("\\s+");
                    for (int ti = 0; ti < tokens.length; ti++) {
                        try {
                            switch (tokens[ti]) {
                                case "threshold" -> {
                                    if (ti + 1 < tokens.length) threshold = Float.parseFloat(tokens[++ti]);
                                }
                                case "radius" -> {
                                    if (ti + 2 < tokens.length) {
                                        minRadius = Float.parseFloat(tokens[++ti]);
                                        maxRadius = Float.parseFloat(tokens[++ti]);
                                    }
                                }
                                case "spacing" -> {
                                    if (ti + 1 < tokens.length) spacing = Integer.parseInt(tokens[++ti]);
                                }
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }

                yield buildEtherStem(server, ow, threshold, minRadius, maxRadius, spacing, biomeListStr);
            }

            default -> null;
        };
    }

    // Parses "biomeId[:weight] ..." tokens. Single biome = FixedBiomeSource; multiple = MultiNoise
    // partitioned by weight along the temperature axis. Falls back to vanilla overworld on null/blank.
    private static BiomeSource parseBiomeSource(MinecraftServer server, String biomeListStr) {
        if (biomeListStr == null || biomeListStr.isBlank()) {
            var reg = server.registryAccess()
                    .lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
            return MultiNoiseBiomeSource.createFromPreset(
                    reg.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        }

        // Parse "namespace:path" and "namespace:path:weight" tokens
        String[] tokens = biomeListStr.trim().split("\\s+");
        List<String>  biomeIds = new ArrayList<>();
        List<Integer> weights  = new ArrayList<>();
        for (String token : tokens) {
            int firstColon = token.indexOf(':');
            int lastColon  = token.lastIndexOf(':');
            if (firstColon >= 0 && lastColon > firstColon) {
                String tail = token.substring(lastColon + 1);
                try {
                    weights.add(Integer.parseInt(tail));
                    biomeIds.add(token.substring(0, lastColon));
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            biomeIds.add(token);
            weights.add(1);
        }

        // Validate — skip unknown biomes
        var biomeReg     = server.registryAccess().lookupOrThrow(Registries.BIOME);
        List<String>  validIds     = new ArrayList<>();
        List<Integer> validWeights = new ArrayList<>();
        for (int i = 0; i < biomeIds.size(); i++) {
            var key = ResourceKey.create(Registries.BIOME, Identifier.parse(biomeIds.get(i)));
            if (biomeReg.get(key).isPresent()) {
                validIds.add(biomeIds.get(i));
                validWeights.add(weights.get(i));
            } else {
                LOGGER.warn("Unknown biome '{}' — skipped", biomeIds.get(i));
            }
        }

        if (validIds.isEmpty()) {
            LOGGER.warn("No valid biomes in list — using overworld default");
            return parseBiomeSource(server, null);
        }

        if (validIds.size() == 1) {
            var key = ResourceKey.create(Registries.BIOME, Identifier.parse(validIds.get(0)));
            return new FixedBiomeSource(biomeReg.getOrThrow(key));
        }

        // Partition temperature axis [-1, 1] proportionally by weight
        int totalWeight = validWeights.stream().mapToInt(Integer::intValue).sum();
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> paramList = new ArrayList<>();
        float currentTemp = -1.0f;
        for (int i = 0; i < validIds.size(); i++) {
            var key    = ResourceKey.create(Registries.BIOME, Identifier.parse(validIds.get(i)));
            Holder<Biome> holder = biomeReg.getOrThrow(key);
            float fraction = (float) validWeights.get(i) / totalWeight;
            float nextTemp = (i == validIds.size() - 1) ? 1.0f : currentTemp + 2.0f * fraction;
            Climate.ParameterPoint point = Climate.parameters(
                    Climate.Parameter.span(currentTemp, nextTemp),
                    Climate.Parameter.span(-1.0f, 1.0f),
                    Climate.Parameter.span(-1.0f, 1.0f),
                    Climate.Parameter.span(-1.0f, 1.0f),
                    Climate.Parameter.span(-1.0f, 1.0f),
                    Climate.Parameter.span(-1.0f, 1.0f),
                    0.0f);
            paramList.add(Pair.of(point, holder));
            currentTemp = nextTemp;
        }
        return MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(paramList));
    }

    // Builds a flat terrain stem from "blockId:height ..." tokens (bottom to top). Defaults biome to plains.
    private static LevelStem buildFlatStem(MinecraftServer server, String layersStr,
                                            LevelStem overworldStem) {
        var biomeReg  = server.registryAccess().lookupOrThrow(Registries.BIOME);
        var plainsKey = ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:plains"));
        Holder<Biome> defaultBiome = biomeReg.getOrThrow(plainsKey);

        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(
                Optional.empty(), defaultBiome, List.of());

        for (String token : layersStr.trim().split("\\s+")) {
            int firstColon = token.indexOf(':');
            int lastColon  = token.lastIndexOf(':');
            String blockId = token;
            int height = 1;
            if (firstColon >= 0 && lastColon > firstColon) {
                String tail = token.substring(lastColon + 1);
                try {
                    height  = Integer.parseInt(tail);
                    blockId = token.substring(0, lastColon);
                } catch (NumberFormatException ignored) {}
            }
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId));
            if (block == Blocks.AIR) {
                LOGGER.warn("Unknown or air block '{}' in flat layers — skipped", blockId);
                continue;
            }
            settings.getLayersInfo().add(new FlatLayerInfo(height, block));
        }

        settings.updateLayers();
        return new LevelStem(overworldStem.type(), new FlatLevelSource(settings));
    }

    // Builds an ether LevelStem with our custom island density function wired into the noise router.
    private static LevelStem buildEtherStem(MinecraftServer server, LevelStem overworldStem,
                                             float threshold, float minRadius, float maxRadius,
                                             int spacing, String biomeListStr) {
        // Copy base settings from the registered FLOATING_ISLANDS preset
        NoiseGeneratorSettings floatingSettings = server.registryAccess()
                .lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.FLOATING_ISLANDS).value();
        NoiseRouter existingRouter = floatingSettings.noiseRouter();

        // Get the END base 3D noise from the density function registry
        DensityFunction base3d = server.registryAccess()
                .lookupOrThrow(Registries.DENSITY_FUNCTION)
                .getOrThrow(ResourceKey.create(Registries.DENSITY_FUNCTION,
                        Identifier.withDefaultNamespace("end/base_3d_noise")))
                .value();

        // Build the custom island density function
        long seed = server.getWorldData().worldGenOptions().seed();
        EtherIslandDensityFunction islands = new EtherIslandDensityFunction(
                seed, threshold, minRadius, maxRadius, spacing);

        // Island shaping + 3D noise for natural terrain detail
        DensityFunction combined = DensityFunctions.add(islands, base3d);

        // slideEndLike: slide(input, minY=0, height=256, 72, -184, -23.4375, 4, 32, -0.234375)
        DensityFunction topGrad = DensityFunctions.yClampedGradient(256 - 72, 256 + 184, 1.0, 0.0);
        DensityFunction afterTop = DensityFunctions.lerp(topGrad, -23.4375, combined);
        DensityFunction botGrad = DensityFunctions.yClampedGradient(4, 32, 0.0, 1.0);
        DensityFunction slid = DensityFunctions.lerp(botGrad, -0.234375, afterTop);

        // postProcess: blend, interpolate, scale, squeeze
        DensityFunction blended = DensityFunctions.blendDensity(slid);
        DensityFunction finalDensity = DensityFunctions.mul(
                DensityFunctions.interpolated(blended),
                DensityFunctions.constant(0.64)).squeeze();

        // Build router: keep temperature/vegetation from existing for biome placement
        NoiseRouter router = new NoiseRouter(
                DensityFunctions.zero(),      // barrier
                DensityFunctions.zero(),      // fluidLevelFloodedness
                DensityFunctions.zero(),      // fluidLevelSpread
                DensityFunctions.zero(),      // lava
                existingRouter.temperature(),
                existingRouter.vegetation(),
                DensityFunctions.zero(),      // continents
                DensityFunctions.zero(),      // erosion
                DensityFunctions.zero(),      // depth
                DensityFunctions.zero(),      // ridges
                DensityFunctions.zero(),      // initialDensityWithoutJaggedness
                finalDensity,
                DensityFunctions.zero(),      // veinToggle
                DensityFunctions.zero(),      // veinRidged
                DensityFunctions.zero());     // veinGap

        NoiseGeneratorSettings customSettings = new NoiseGeneratorSettings(
                floatingSettings.noiseSettings(),
                floatingSettings.defaultBlock(),
                floatingSettings.defaultFluid(),
                router,
                floatingSettings.surfaceRule(),
                floatingSettings.spawnTarget(),
                floatingSettings.seaLevel(),
                floatingSettings.disableMobGeneration(),
                floatingSettings.aquifersEnabled(),
                floatingSettings.oreVeinsEnabled(),
                floatingSettings.useLegacyRandomSource());

        return new LevelStem(overworldStem.type(), new NoiseBasedChunkGenerator(
                parseBiomeSource(server, biomeListStr), new Holder.Direct<>(customSettings)));
    }
}
