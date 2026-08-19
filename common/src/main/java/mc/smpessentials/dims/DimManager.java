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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

/*
 * Handles runtime creation, deletion, and restoration of custom dimensions.
 * Dimensions are injected into MinecraftServer.levels via a Mixin accessor so they are live
 * immediately without a restart. Supports generator types: overworld, nether, end, ether.
 * Ether uses a custom island density function (EtherIslandDensityFunction) with configurable
 * island shape params. The generatorConfig grammar those params ride in lives in GeneratorConfig.
 * Each custom dim gets a portal frame block (default glowstone); right-click with water bucket
 * to activate. The generatorConfig string is persisted in DimSavedData for restart recovery.
 */
public final class DimManager {

    private static final Logger LOGGER = LogManager.getLogger("DimManager");

    // Loaded ether dims mapped to whether they generate structures, so the per-tick void check and
    // the structure mixin never touch SavedData. Updated on create/destroy/restore.
    private static final Map<String, Boolean> etherDims =
            Collections.synchronizedMap(new HashMap<>());

    // True if the dim is currently loaded as ether-type.
    public static boolean isEtherDim(String dimId) {
        return etherDims.containsKey(dimId);
    }

    // True if that ether dim was created with structures enabled. See EtherStructureFilter.
    public static boolean etherStructuresEnabled(String dimId) {
        return Boolean.TRUE.equals(etherDims.get(dimId));
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
    // JSON, and BlueMap map config so it does not re-register on restart.
    // Returns null on success or an error message on failure.
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
        etherDims.remove(loc.toString());
        DimSavedData.get(server).remove(id);
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
        Path playerDataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (!java.nio.file.Files.isDirectory(playerDataDir)) return;

        // Collect UUIDs of currently online players, their data is managed in memory.
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
        Path playerDataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
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

                    // Dimension doesn't exist, relocate to overworld
                    root.putString("Dimension", "minecraft:overworld");
                    NbtIo.writeCompressed(root, datFile);
                    String uuid = fileName.replace(".dat", "");
                    LOGGER.warn("Repaired orphaned player {}, was in non-existent dim {}, "
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
        // Flat dims: compute spawn height directly from the generator, it doesn't require
        // loaded chunks, and flat terrain can sit at negative Y where the > 0 heightmap
        // check would incorrectly treat it as an unloaded chunk.
        if (dest.getChunkSource().getGenerator() instanceof FlatLevelSource flat) {
            return new BlockPos(hintX, flat.getSpawnHeight(dest), hintZ);
        }
        // Noise-based dims: probe the heightmap. Returns minY for unloaded chunks.
        int surfaceY = dest.getHeight(Heightmap.Types.WORLD_SURFACE, hintX, hintZ);
        return new BlockPos(hintX, surfaceY > 0 ? surfaceY : 80, hintZ);
    }

    // Teleports a player to the dim's spawn origin, scheduling the spawn island or return portal for
    // the next tick so it lands on terrain that has actually generated. Shared by /dim tp and the
    // admin panel. Returns false if the teleport was rejected.
    public static boolean teleportToDim(MinecraftServer server, ServerPlayer player, ServerLevel dest) {
        BlockPos origin = findSpawnOrigin(server, dest);

        DimSavedData.get(server)
                .getEntry(dest.dimension().identifier().toString())
                .ifPresent(entry -> server.execute(() -> {
                    BlockPos freshOrigin = findSpawnOrigin(server, dest);
                    if ("ether".equals(entry.generatorType())) {
                        ensureSpawnPlatform(dest, freshOrigin);
                    } else {
                        ensureReturnPortal(dest, freshOrigin);
                    }
                }));

        BlockPos safe = player.adjustSpawnLocation(dest, origin);
        return player.teleportTo(dest,
                safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot(), false);
    }

    // Generates a small floating island at spawnPos if terrain is missing, then ensures a return portal.
    public static void ensureSpawnPlatform(ServerLevel dest, BlockPos spawnPos) {
        BlockPos surface = spawnPos.below();
        if (!dest.getBlockState(surface).isAir()) {
            // Terrain already exists at this position, no custom island needed, but still
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
        // from terrain, unlike scanning for the frame block, which can appear in natural terrain
        // (e.g. dirt in a flat dim) and would cause every call to exit early with no portal placed.
        for (int scanY = dest.getMinY(); scanY <= dest.getMaxY(); scanY++) {
            if (dest.getBlockState(new BlockPos(px + 1, scanY, z)).is(Blocks.NETHER_PORTAL)) return;
        }

        // Frame: bottom at y, pillars y→y+4, top at y+4
        // Force-place frame blocks (not placeIfAir) so the frame is always complete even if
        // existing structure blocks are in the way, an incomplete frame breaks portal routing.
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

        // Anything wrong with the config is collected rather than logged and skipped, so a bad
        // value fails the request instead of quietly generating a different world.
        List<String> problems = new ArrayList<>();
        LevelStem stem = resolveStem(server, generatorType, generatorConfig, problems);
        if (stem == null) {
            return "Unknown generator type: " + generatorType
                    + ". Use: overworld, nether, end, ether";
        }
        if (!problems.isEmpty()) {
            if (persist) return String.join("; ", problems);
            // Restoring a dim that was valid when it was created: report and carry on rather than
            // dropping the dimension on startup.
            problems.forEach(problem -> LOGGER.warn("Restoring {}: {}", id, problem));
        }

        ServerLevelData overworldData = server.getWorldData().overworldData();
        DerivedLevelData derivedData = new DerivedLevelData(server.getWorldData(), overworldData);

        long seed = server.getWorldGenSettings().options().seed();
        long biomeZoomSeed = BiomeManager.obfuscateSeed(seed);

        Executor executor = accessor.getExecutor();
        LevelStorageSource.LevelStorageAccess storageSource = accessor.getStorageSource();

        ServerLevel newLevel = new ServerLevel(
                server, executor, storageSource, derivedData, dimKey, stem,
                false, biomeZoomSeed, ImmutableList.of(), false);

        levels.put(dimKey, newLevel);
        server.getPlayerList().addWorldborderListener(newLevel);

        if ("ether".equals(generatorType)) {
            etherDims.put(dimKey.identifier().toString(),
                    GeneratorConfig.parseEther(generatorConfig.orElse("")).structures());

            // Pre-generate the spawn island on the next tick, by which time the chunk generator
            // will have had a chance to initialise the spawn chunk.
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
                                          Optional<String> generatorConfig, List<String> problems) {
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
                    yield buildFlatStem(server, config.substring(5), ow, problems);
                }
                // Always a fresh overworld generator, never the live overworld's LevelStem
                // instance, as sharing a ChunkGenerator between two ServerLevels corrupts terrain.
                String biomeListStr = GeneratorConfig.splitBiomes(config).biomes().orElse(null);
                yield new LevelStem(ow.type(), new NoiseBasedChunkGenerator(
                        parseBiomeSource(server, biomeListStr, problems), owNoise));
            }

            // Ether: custom island density function + overworld DimensionType + configurable biomes
            case "ether" -> {
                LevelStem ow = stemReg.getValue(LevelStem.OVERWORLD);
                if (ow == null) yield null;

                GeneratorConfig.Ether ether = GeneratorConfig.parseEther(generatorConfig.orElse(""));
                problems.addAll(ether.problems());
                yield buildEtherStem(server, ow, ether.params(), ether.biomes().orElse(null), problems);
            }

            default -> null;
        };
    }

    // Parses "biomeId[:weight] ..." tokens. Single biome = FixedBiomeSource; multiple = MultiNoise
    // partitioned by weight along the temperature axis. Falls back to vanilla overworld on
    // null/blank. Unknown ids and bad weights are recorded in problems, never quietly dropped.
    private static BiomeSource parseBiomeSource(MinecraftServer server, String biomeListStr,
                                                 List<String> problems) {
        if (biomeListStr == null || biomeListStr.isBlank()) {
            var reg = server.registryAccess()
                    .lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
            return MultiNoiseBiomeSource.createFromPreset(
                    reg.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        }

        var biomeReg = server.registryAccess().lookupOrThrow(Registries.BIOME);
        List<String>  validIds     = new ArrayList<>();
        List<Integer> validWeights = new ArrayList<>();

        // Tokens are "namespace:path" or "namespace:path:weight".
        for (String token : biomeListStr.trim().split("\\s+")) {
            String biomeId = token;
            int weight = 1;

            int firstColon = token.indexOf(':');
            int lastColon  = token.lastIndexOf(':');
            if (firstColon >= 0 && lastColon > firstColon) {
                String tail = token.substring(lastColon + 1);
                try {
                    weight  = Integer.parseInt(tail);
                    biomeId = token.substring(0, lastColon);
                } catch (NumberFormatException e) {
                    problems.add("Biome weight must be a whole number, got '" + tail + "'");
                    continue;
                }
                if (weight < 1) {
                    problems.add("Biome weight must be 1 or more, got " + weight + " for " + biomeId);
                    continue;
                }
            }

            Identifier parsed;
            try {
                parsed = Identifier.parse(biomeId);
            } catch (Exception e) {
                problems.add("Not a valid biome id: '" + biomeId + "'");
                continue;
            }
            if (biomeReg.get(ResourceKey.create(Registries.BIOME, parsed)).isEmpty()) {
                problems.add("Unknown biome: " + biomeId);
                continue;
            }

            validIds.add(biomeId);
            validWeights.add(weight);
        }

        if (validIds.isEmpty()) {
            problems.add("No usable biomes in the list");
            return parseBiomeSource(server, null, problems);
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

    // Builds a flat terrain stem from "blockId:height ..." tokens (bottom to top). Defaults biome to
    // plains. Bad tokens are recorded in problems so creation fails loudly instead of quietly
    // producing a world with layers missing.
    private static LevelStem buildFlatStem(MinecraftServer server, String layersStr,
                                            LevelStem overworldStem, List<String> problems) {
        var biomeReg  = server.registryAccess().lookupOrThrow(Registries.BIOME);
        var plainsKey = ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:plains"));
        Holder<Biome> defaultBiome = biomeReg.getOrThrow(plainsKey);

        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(
                Optional.empty(), defaultBiome, List.of());

        int maxHeight = overworldStem.type().value().height();
        int total = 0;
        for (String token : layersStr.trim().split("\\s+")) {
            if (token.isEmpty()) continue;

            String blockId = token;
            int height = 1;
            int firstColon = token.indexOf(':');
            int lastColon  = token.lastIndexOf(':');
            if (firstColon >= 0 && lastColon > firstColon) {
                String tail = token.substring(lastColon + 1);
                try {
                    height  = Integer.parseInt(tail);
                    blockId = token.substring(0, lastColon);
                } catch (NumberFormatException e) {
                    problems.add("Layer height must be a whole number, got '" + tail + "'");
                    continue;
                }
            }
            if (height < 1) {
                problems.add("Layer height must be 1 or more, got " + height + " for " + blockId);
                continue;
            }

            Identifier parsed;
            try {
                parsed = Identifier.parse(blockId);
            } catch (Exception e) {
                problems.add("Not a valid block id: '" + blockId + "'");
                continue;
            }
            Block block = BuiltInRegistries.BLOCK.getValue(parsed);
            if (block == Blocks.AIR) {
                problems.add("Unknown or air block in flat layers: " + blockId);
                continue;
            }

            total += height;
            if (total > maxHeight) {
                problems.add("Flat layers total " + total + " blocks, more than the "
                        + maxHeight + " this dimension can hold");
                break;
            }
            settings.getLayersInfo().add(new FlatLayerInfo(height, block));
        }

        if (settings.getLayersInfo().isEmpty()) {
            problems.add("Flat needs at least one usable layer");
        }

        settings.updateLayers();
        return new LevelStem(overworldStem.type(), new FlatLevelSource(settings));
    }

    // Builds an ether LevelStem with our custom island density function wired into the noise router.
    private static LevelStem buildEtherStem(MinecraftServer server, LevelStem overworldStem,
                                             EtherIslandParams params, String biomeListStr,
                                             List<String> problems) {
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
        long seed = server.getWorldGenSettings().options().seed();
        EtherIslandDensityFunction islands = new EtherIslandDensityFunction(seed, params);

        // Island shaping + 3D noise for natural terrain detail
        DensityFunction combined = DensityFunctions.add(islands, base3d);

        // No End-style top or bottom slide. Those fade terrain out below y32 and above y184, which
        // would silently forbid the low and high ends of the configured island height band.
        // Islands are bounded by their own thickness, so nothing else needs the slide.

        // postProcess: blend, interpolate, scale, squeeze
        DensityFunction blended = DensityFunctions.blendDensity(combined);
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
                parseBiomeSource(server, biomeListStr, problems), Holder.direct(customSettings)));
    }
}
