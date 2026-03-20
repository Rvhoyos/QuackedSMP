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
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
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

/**
 * Runtime dimension management: create, destroy, list, and restore custom dimensions.
 *
 * <p>Dimensions are added to {@code MinecraftServer.levels} via a Mixin accessor, making them
 * immediately active (ticked, saved, queryable) without a restart.
 *
 * <h2>Generator types</h2>
 * <ul>
 *   <li>{@code overworld} — standard overworld noise terrain</li>
 *   <li>{@code nether} — vanilla nether terrain and biomes</li>
 *   <li>{@code end} — vanilla end terrain and biomes</li>
 *   <li>{@code ether} — floating-islands noise ({@link NoiseGeneratorSettings#FLOATING_ISLANDS}),
 *       overworld {@code DimensionType}; falling below {@code minY} teleports the player back
 *       to the overworld at Y=300 above world spawn (see {@link EtherFallthrough})</li>
 * </ul>
 *
 * <h2>Portal system</h2>
 * <p>Every newly created dim is automatically assigned {@code minecraft:glowstone} as its portal
 * frame block, provided glowstone is not already claimed by another dim. Admins can override with
 * {@code /dim setportal <dim> <block>}. Each dim holds at most one frame block; each block links
 * to at most one dim. Players build a frame in the standard nether-portal shape and right-click
 * it with a water bucket to activate — only works in vanilla dimensions (overworld/nether/end).
 * Portal routing in both directions is handled by
 * {@link mc.smpessentials.mixin.NetherPortalBlockMixin}.</p>
 *
 * <h2>generatorConfig format</h2>
 * <p>The {@code generatorConfig} string encodes optional sub-generator parameters and is persisted
 * in {@link DimSavedData} so the dimension can be reconstructed on restart.
 * <ul>
 *   <li>Absent/empty — use the default biome set for the generator type</li>
 *   <li>{@code "biomes minecraft:plains:2 minecraft:desert:1"} — custom biome list with optional
 *       per-biome weights. Applies to {@code overworld} and {@code ether}.
 *       See {@link #parseBiomeSource} for weight semantics.</li>
 *   <li>{@code "flat minecraft:stone:5 minecraft:dirt:3 minecraft:grass_block:1"} — flat terrain
 *       with layers listed bottom-to-top. {@code overworld} only.</li>
 * </ul>
 */
public final class DimManager {

    private static final Logger LOGGER = LogManager.getLogger("DimManager");

    /**
     * Dimension IDs that need to be scrubbed from {@code level.dat}'s
     * {@code Data.WorldGenSettings.dimensions} on the next server stop.
     * Populated by {@link #destroy}; consumed by {@link #scrubLevelDat}.
     */
    private static final Set<String> pendingLevelDatRemovals =
            Collections.synchronizedSet(new HashSet<>());

    /**
     * IDs of all currently loaded ether dimensions. Populated by {@link #createLevel} and
     * {@link #restoreAll}; cleared by {@link #destroy}. Used by {@link EtherFallthrough#tick}
     * to avoid a {@link DimSavedData} lookup on every server tick per player.
     */
    private static final Set<String> etherDimIds =
            Collections.synchronizedSet(new HashSet<>());

    /**
     * Returns {@code true} if {@code dimId} is a currently loaded ether dimension.
     * Backed by an in-memory set populated at server start and kept in sync with create/destroy.
     */
    public static boolean isEtherDim(String dimId) {
        return etherDimIds.contains(dimId);
    }

    /**
     * Persists {@code portalPos} as the overworld return destination for {@code entity}.
     * Called whenever an entity enters any custom dim outbound so that the matching return
     * trip ({@link #popReturnPos}) can teleport them back to the right overworld location.
     * Stored via {@link EtherReturnData} and survives server restarts.
     */
    public static void saveReturnPos(Entity entity, BlockPos portalPos, MinecraftServer server) {
        EtherReturnData.get(server).record(entity.getUUID(), portalPos);
    }

    /**
     * Retrieves and removes the stored overworld return position for {@code entity}.
     * Falls back to overworld spawn only if no position was ever stored (e.g. the very first
     * time a player visits a custom dim on a fresh server install).
     */
    public static BlockPos popReturnPos(Entity entity, MinecraftServer server) {
        BlockPos stored = EtherReturnData.get(server).remove(entity.getUUID());
        return stored != null ? stored : server.overworld().getRespawnData().pos();
    }

    private DimManager() {}

    /**
     * Creates a new custom dimension and persists it to {@link DimSavedData}.
     *
     * @param generatorConfig optional sub-generator config string (see class javadoc)
     * @return {@code null} on success, or a human-readable error message on failure.
     */
    public static String create(MinecraftServer server, String id, String generatorType,
                                 Optional<String> generatorConfig) {
        return createLevel(server, id, generatorType, generatorConfig, true);
    }

    /**
     * Destroys a custom dimension: evicts all players, saves, closes, and removes it.
     *
     * <p>Three cleanup steps run after the level is removed from the live map:
     * <ol>
     *   <li><strong>Chunk data folder</strong> — deleted so recreating the dim starts with
     *       fresh terrain.</li>
     *   <li><strong>Datapack JSON</strong> — if the dim was registered via a datapack, its
     *       definition file at
     *       {@code <worldRoot>/datapacks/<packName>/data/<ns>/dimension/<path>.json}
     *       is deleted so vanilla does not re-register it from the pack on restart.</li>
     *   <li><strong>{@code level.dat} scrub</strong> — vanilla bakes datapack-registered dims
     *       into {@code Data.WorldGenSettings.dimensions} in {@code level.dat} and re-registers
     *       them from there even if the JSON file is gone. The dim ID is queued in
     *       {@link #pendingLevelDatRemovals} and patched out by {@link #scrubLevelDat} after the
     *       server writes its final {@code level.dat} on shutdown.</li>
     * </ol>
     *
     * @return {@code null} on success, or a human-readable error message on failure.
     */
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

        // Evict all players to overworld spawn
        ServerLevel overworld = server.overworld();
        BlockPos spawnPos = overworld.getRespawnData().pos();
        List<ServerPlayer> occupants = List.copyOf(level.players());
        for (ServerPlayer player : occupants) {
            BlockPos safe = player.adjustSpawnLocation(overworld, spawnPos);
            player.teleportTo(overworld,
                    safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                    Set.of(), player.getYRot(), player.getXRot(), false);
        }

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

        return null;
    }

    /**
     * Patches {@code level.dat} to remove any dimensions queued by {@link #destroy} from
     * {@code Data.WorldGenSettings.dimensions}. Must be called <em>after</em> the server has
     * written its own {@code level.dat} (i.e. from the platform's server-stopped event), so
     * our write is the final one before the next startup.
     *
     * <p>Safe to call even if no deletions are pending — exits immediately in that case.
     * Logs a warning on failure but never throws; a failed scrub is not fatal (the dim
     * will simply reappear on the next restart and can be deleted again).
     */
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

    /**
     * Re-creates all custom dimensions from {@link DimSavedData} after a server restart.
     * Does NOT write back to saved data (entries are already persisted).
     */
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
    }

    /**
     * Returns a list of all currently active level keys (vanilla + custom).
     */
    public static List<ResourceKey<Level>> listAll(MinecraftServer server) {
        return new ArrayList<>(((MinecraftServerMixin) server).getLevels().keySet());
    }

    /**
     * Returns a sensible spawn-search origin for {@code dest}, using overworld spawn XZ as the
     * horizontal hint. Delegates to {@link #findSpawnOrigin(MinecraftServer, ServerLevel, int, int)}.
     * Used by {@link DimCommands#tpPlayerToDim} (all dim types) and the ether dim-creation path
     * where no portal entry XZ context is available.
     */
    public static BlockPos findSpawnOrigin(MinecraftServer server, ServerLevel dest) {
        BlockPos owSpawn = server.overworld().getRespawnData().pos();
        return findSpawnOrigin(server, dest, owSpawn.getX(), owSpawn.getZ());
    }

    /**
     * Returns a sensible spawn-search origin for {@code dest} at the given horizontal position.
     *
     * <p>For ceiling dims (nether-type), Y=64 is used directly at the hint XZ.
     * For flat dims, the generator spawn height is used (no chunk load required).
     * For noise dims, the heightmap is probed at {@code hintX, hintZ}; falls back to (0,0) if
     * the chunk is not yet loaded (heightmap returns 0).
     *
     * <p>Portal entry callers should pass the overworld portal's XZ as the hint so the return
     * portal in the custom dim is placed at the matching position rather than a global spawn point.
     */
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

    /**
     * Generates a small floating island centred on {@code spawnPos} in {@code dest} if the
     * surface block (one below {@code spawnPos}) is air. If terrain already exists at that
     * position, the island is skipped but the return portal is still ensured.
     *
     * <p>Always calls {@link #ensureReturnPortal} so a return portal exists on the island
     * (or on whatever terrain is at {@code spawnPos}) regardless of whether the island was
     * freshly generated.
     *
     * <p>Called when routing a player to an ether dimension via portal or {@code /dim tp}.
     *
     * <h3>Island shape (tapered, floating-island style)</h3>
     * <ul>
     *   <li>+0 — {@code grass_block}, ~13-block oval</li>
     *   <li>−1, −2 — {@code dirt}, 3×3</li>
     *   <li>−3 — {@code stone}, 3×3</li>
     *   <li>−4 — {@code stone}, 5-block cross</li>
     *   <li>−5 — {@code stone}, single tip</li>
     * </ul>
     *
     * <h3>Return portal</h3>
     * <p>A 2×3 interior portal is placed on the island surface using the dim's registered frame
     * block (see {@link DimSavedData}), defaulting to glowstone. {@code AXIS=X}.
     * The mod's portal routing intercepts it and sends the player back to the overworld.
     *
     * @param spawnPos the block position where the player's feet will land
     */
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

    /**
     * Places a return portal at {@code spawnPos} in any custom dimension using the dim's
     * registered portal frame block (from {@link DimSavedData}), falling back to glowstone.
     * The frame is 4 wide × 5 tall (2×3 interior, {@code AXIS=X}) with its base at {@code spawnPos.Y}.
     * Frame blocks are force-placed (overwriting existing blocks) so the frame is always complete
     * even when landing inside a structure.
     *
     * <p>Skips silently if a {@code NETHER_PORTAL} block already exists anywhere in the interior
     * column at {@code spawnPos.XZ} — this is unambiguous (portal blocks only exist where we place
     * them) and avoids false positives from terrain blocks that match the frame block type.
     *
     * <p>Called directly for all non-ether custom dims, and via {@link #ensureSpawnPlatform} for ether.
     *
     * @param spawnPos the player's feet position (frame base is placed at this Y)
     */
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

    /**
     * Places {@code block} at {@code pos} only if the existing block state is air.
     * The flag {@code 3} triggers block update and client notification.
     */
    private static void placeIfAir(ServerLevel level, BlockPos pos, Block block) {
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, block.defaultBlockState(), 3);
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Core implementation shared by {@link #create} and {@link #restoreAll}.
     *
     * <p>Constructs a {@link ServerLevel} with the appropriate {@link LevelStem}, registers it in
     * the live levels map, and optionally persists it to {@link DimSavedData}. For {@code ether}
     * dims, a spawn-island generation task is scheduled on the server thread.
     *
     * @param persist if {@code true}, writes the entry to {@link DimSavedData} and assigns the
     *                default glowstone portal frame block. Pass {@code false} when called from
     *                {@link #restoreAll} to avoid duplicating already-persisted entries.
     * @return {@code null} on success, or a human-readable error message on failure.
     */
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

        levels.put(dimKey, newLevel);
        server.getPlayerList().addWorldborderListener(newLevel);

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
            // Assign glowstone as the default portal frame block if it is not already claimed
            // by another dim. Admins can override at any time with /dim setportal.
            String glowstoneId = "minecraft:glowstone";
            if (saved.getDimForPortalBlock(glowstoneId).isEmpty()) {
                saved.setPortalBlock(canonicalId, glowstoneId);
            }
        }

        return null;
    }

    /**
     * Resolves a {@link LevelStem} for the given generator type and optional config string.
     *
     * <p>For {@code nether} and {@code end}, the vanilla registry stem is used directly.
     * For {@code overworld}, an optional {@code flat} or {@code biomes} sub-config may override
     * the chunk generator while keeping the vanilla overworld {@link net.minecraft.world.level.dimension.DimensionType}.
     * For {@code ether}, the floating-islands noise preset is used with configurable biomes,
     * also reusing the overworld {@code DimensionType}.
     *
     * @return the resolved {@link LevelStem}, or {@code null} if {@code generatorType} is unknown.
     */
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

            // Ether: floating-islands terrain + overworld DimensionType + configurable biomes
            case "ether" -> {
                LevelStem ow = stemReg.getValue(LevelStem.OVERWORLD);
                if (ow == null) yield null;
                Holder<NoiseGeneratorSettings> floatingNoise = server.registryAccess()
                        .lookupOrThrow(Registries.NOISE_SETTINGS)
                        .getOrThrow(NoiseGeneratorSettings.FLOATING_ISLANDS);
                String config = generatorConfig.orElse("");
                String biomeListStr = config.startsWith("biomes ") ? config.substring(7).trim() : null;
                yield new LevelStem(ow.type(), new NoiseBasedChunkGenerator(
                        parseBiomeSource(server, biomeListStr), floatingNoise));
            }

            default -> null;
        };
    }

    /**
     * Resolves a {@link BiomeSource} from a space-separated list of {@code biomeId[:weight]} tokens.
     *
     * <h3>Behaviour by input</h3>
     * <ul>
     *   <li>Null/blank — returns the full vanilla overworld multi-noise preset (all vanilla biomes
     *       with their normal climate-based distribution).</li>
     *   <li>One valid biome — returns a {@link FixedBiomeSource}; the entire dimension uses that
     *       single biome exclusively.</li>
     *   <li>Multiple biomes — returns a {@link MultiNoiseBiomeSource} whose parameter list
     *       partitions the worldgen <em>temperature axis</em> {@code [-1, 1]} proportionally by
     *       weight. All other climate axes (humidity, continentalness, erosion, depth, weirdness)
     *       are set to the full range for every biome, so temperature alone determines placement.</li>
     * </ul>
     *
     * <h3>Weight semantics</h3>
     * <p>Only the <em>ratio</em> between weights matters — absolute values are irrelevant.
     * {@code plains:1 desert:1} and {@code plains:7 desert:7} produce an identical 50/50
     * distribution. A biome with weight {@code 3} in a list whose total weight is {@code 5}
     * occupies roughly 60% of the world's area.
     *
     * <h3>Token format</h3>
     * <p>Each token is either {@code namespace:path} (weight defaults to 1) or
     * {@code namespace:path:weight} where {@code weight} is a positive integer.
     * Unknown biome IDs are skipped with a warning; if all IDs are invalid the method
     * falls back to the vanilla overworld preset.
     */
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

    /**
     * Builds a flat-terrain {@link LevelStem} from a space-separated list of
     * {@code blockId:height} tokens.
     *
     * <p>Layers are applied <strong>bottom to top</strong>. Each token is
     * {@code namespace:path:height} where {@code height} is the number of block layers.
     * The dimension type is borrowed from the vanilla overworld stem.
     * The biome defaults to {@code minecraft:plains}.
     * Unknown or air blocks are skipped with a warning.
     */
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
}
