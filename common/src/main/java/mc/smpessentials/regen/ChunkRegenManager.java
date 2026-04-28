package mc.smpessentials.regen;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.claims.model.ClaimData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

// Queues and executes wilderness chunk regeneration at server shutdown.
// Claimed chunks and a buffer zone around them are preserved; everything else
// gets its header zeroed in the .mca files so Minecraft regenerates from seed.
public final class ChunkRegenManager {

    private ChunkRegenManager() {}

    private static final String MARKER_FILE = "quacksmp_regen_pending";
    private static final String CLAIMS_FILE = "quackedsmp_claims.dat";
    private static final int BUFFER = 3;
    private static final String[] SUBDIRS = {"region", "entities", "poi"};

    // Creates the marker file to queue a regen for next shutdown.
    public static void queueRegen(MinecraftServer server) throws IOException {
        Path marker = markerPath(server);
        Files.createDirectories(marker.getParent());
        if (!Files.exists(marker)) {
            Files.createFile(marker);
        }
    }

    // Removes the marker file, cancelling any pending regen.
    public static void cancelRegen(MinecraftServer server) throws IOException {
        Files.deleteIfExists(markerPath(server));
    }

    public static boolean isPending(MinecraftServer server) {
        return Files.exists(markerPath(server));
    }

    // Called from both platform modules at SERVER_STOPPED.
    public static void onServerStopped(MinecraftServer server) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        Path marker = worldRoot.resolve("data").resolve(MARKER_FILE);
        if (!Files.exists(marker)) return;

        SmpUtilsMod.LOGGER.info("[QuackedSMP] Performing scheduled wilderness regen...");

        try {
            List<ClaimData> claims = readClaimsFromDisk(worldRoot);
            SmpUtilsMod.LOGGER.info("[QuackedSMP] Loaded {} claims from disk.", claims.size());

            // Group claims by dimension
            Map<String, List<ClaimData>> claimsByDim = new HashMap<>();
            for (ClaimData cd : claims) {
                String dimId = cd.dimension().identifier().toString();
                claimsByDim.computeIfAbsent(dimId, k -> new ArrayList<>()).add(cd);
            }

            // Discover all dimension folders that have region data
            Set<String> allDims = discoverDimensions(worldRoot);
            SmpUtilsMod.LOGGER.info("[QuackedSMP] Found dimensions with region data: {}", allDims);

            long totalCleared = 0;
            long totalFiles = 0;
            long totalDeleted = 0;

            for (String dimId : allDims) {
                List<ClaimData> dimClaims = claimsByDim.getOrDefault(dimId, Collections.emptyList());
                LongOpenHashSet protectedSet = buildProtectedSet(dimClaims);
                Path dimFolder = resolveDimensionFolder(worldRoot, dimId);

                SmpUtilsMod.LOGGER.info("[QuackedSMP] {}: {} claims, {} protected chunks (with buffer={})",
                        dimId, dimClaims.size(), protectedSet.size(), BUFFER);

                for (String subdir : SUBDIRS) {
                    Path dir = dimFolder.resolve(subdir);
                    if (!Files.isDirectory(dir)) continue;

                    long[] stats = processDirectory(dir, protectedSet);
                    totalFiles += stats[0];
                    totalCleared += stats[1];
                    totalDeleted += stats[2];

                    if (stats[1] > 0) {
                        SmpUtilsMod.LOGGER.info("[QuackedSMP] {}/{}: cleared {} chunks across {} files, deleted {} empty region files",
                                dimId, subdir, stats[1], stats[0], stats[2]);
                    }
                }
            }

            Files.deleteIfExists(marker);
            SmpUtilsMod.LOGGER.info("[QuackedSMP] Wilderness regen complete. Cleared {} chunks across {} files, deleted {} empty region files.",
                    totalCleared, totalFiles, totalDeleted);

        } catch (Exception e) {
            SmpUtilsMod.LOGGER.error("[QuackedSMP] Wilderness regen failed", e);
            // Do NOT delete marker so admin can investigate and retry
        }
    }

    private static Path markerPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(MARKER_FILE);
    }

    // Reads claims directly from the compressed NBT file on disk.
    // Returns an empty list if the file does not exist (no claims).
    private static List<ClaimData> readClaimsFromDisk(Path worldRoot) {
        Path claimFile = worldRoot.resolve("data").resolve(CLAIMS_FILE);
        if (!Files.exists(claimFile)) {
            SmpUtilsMod.LOGGER.warn("[QuackedSMP] No claims file found -- all chunks will be regenerated.");
            return Collections.emptyList();
        }

        try {
            CompoundTag root = NbtIo.readCompressed(claimFile, NbtAccounter.unlimitedHeap());
            Optional<CompoundTag> dataOpt = root.getCompound("data");
            if (dataOpt.isEmpty()) {
                SmpUtilsMod.LOGGER.warn("[QuackedSMP] Claims file has no 'data' compound.");
                return Collections.emptyList();
            }

            var result = ClaimData.CODEC.listOf().fieldOf("claims").codec()
                    .parse(NbtOps.INSTANCE, dataOpt.get());
            return result.result().orElse(Collections.emptyList());
        } catch (IOException e) {
            SmpUtilsMod.LOGGER.error("[QuackedSMP] Failed to read claims file", e);
            return Collections.emptyList();
        }
    }

    // Builds the set of chunk longs that must NOT be cleared.
    // Includes every claimed chunk plus a square buffer around each.
    private static LongOpenHashSet buildProtectedSet(List<ClaimData> claims) {
        LongOpenHashSet set = new LongOpenHashSet();
        for (ClaimData cd : claims) {
            ChunkPos cp = ChunkPos.unpack(cd.chunk());
            for (int dx = -BUFFER; dx <= BUFFER; dx++) {
                for (int dz = -BUFFER; dz <= BUFFER; dz++) {
                    set.add(new ChunkPos(cp.x() + dx, cp.z() + dz).pack());
                }
            }
        }
        return set;
    }

    // Scans the world root to find all dimensions that have region data on disk.
    private static Set<String> discoverDimensions(Path worldRoot) {
        Set<String> dims = new LinkedHashSet<>();

        // Overworld
        if (Files.isDirectory(worldRoot.resolve("region"))) {
            dims.add("minecraft:overworld");
        }
        // Nether
        if (Files.isDirectory(worldRoot.resolve("DIM-1").resolve("region"))) {
            dims.add("minecraft:the_nether");
        }
        // End
        if (Files.isDirectory(worldRoot.resolve("DIM1").resolve("region"))) {
            dims.add("minecraft:the_end");
        }
        // Custom dimensions: dimensions/<namespace>/<path>/region/
        Path dimsRoot = worldRoot.resolve("dimensions");
        if (Files.isDirectory(dimsRoot)) {
            try (DirectoryStream<Path> namespaces = Files.newDirectoryStream(dimsRoot)) {
                for (Path ns : namespaces) {
                    if (!Files.isDirectory(ns)) continue;
                    try (DirectoryStream<Path> paths = Files.newDirectoryStream(ns)) {
                        for (Path p : paths) {
                            if (Files.isDirectory(p.resolve("region"))) {
                                dims.add(ns.getFileName() + ":" + p.getFileName());
                            }
                        }
                    }
                }
            } catch (IOException e) {
                SmpUtilsMod.LOGGER.warn("[QuackedSMP] Failed to scan custom dimensions", e);
            }
        }

        return dims;
    }

    // Maps a dimension ID string to its filesystem folder.
    static Path resolveDimensionFolder(Path worldRoot, String dimId) {
        return switch (dimId) {
            case "minecraft:overworld" -> worldRoot;
            case "minecraft:the_nether" -> worldRoot.resolve("DIM-1");
            case "minecraft:the_end" -> worldRoot.resolve("DIM1");
            default -> {
                int colon = dimId.indexOf(':');
                String namespace = colon > 0 ? dimId.substring(0, colon) : "minecraft";
                String path = colon > 0 ? dimId.substring(colon + 1) : dimId;
                yield worldRoot.resolve("dimensions").resolve(namespace).resolve(path);
            }
        };
    }

    // Processes all .mca files in a directory. Returns {filesProcessed, chunksCleared, filesDeleted}.
    private static long[] processDirectory(Path dir, LongOpenHashSet protectedChunks) {
        long files = 0;
        long cleared = 0;
        long deleted = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.mca")) {
            for (Path mca : stream) {
                int[] regionCoords = parseRegionCoords(mca.getFileName().toString());
                if (regionCoords == null) continue;

                long[] result = processRegionFile(mca, regionCoords[0], regionCoords[1], protectedChunks);
                if (result[0] > 0) {
                    files++;
                    cleared += result[0];
                }
                // No live chunks remain — delete the entire file to reclaim disk space
                if (result[1] == 0) {
                    Files.deleteIfExists(mca);
                    deleted++;
                }
            }
        } catch (IOException e) {
            SmpUtilsMod.LOGGER.error("[QuackedSMP] Failed to list .mca files in {}", dir, e);
        }

        return new long[]{files, cleared, deleted};
    }

    // Zeroes header entries for unprotected chunks in a single .mca file.
    // Returns {chunksCleared, chunksRemaining}.
    private static long[] processRegionFile(Path mca, int regionX, int regionZ,
                                            LongOpenHashSet protectedChunks) {
        long cleared = 0;
        long remaining = 0;
        byte[] zero = new byte[4];

        try (RandomAccessFile raf = new RandomAccessFile(mca.toFile(), "rw")) {
            if (raf.length() < 8192) return new long[]{0, 0};

            for (int index = 0; index < 1024; index++) {
                int localX = index & 31;
                int localZ = index >> 5;
                int chunkX = regionX * 32 + localX;
                int chunkZ = regionZ * 32 + localZ;
                long chunkLong = new ChunkPos(chunkX, chunkZ).pack();

                int locOffset = index * 4;
                raf.seek(locOffset);
                int loc = raf.readInt();
                if (loc == 0) continue;

                if (protectedChunks.contains(chunkLong)) {
                    remaining++;
                    continue;
                }

                raf.seek(locOffset);
                raf.write(zero);

                raf.seek(4096 + locOffset);
                raf.write(zero);

                cleared++;
            }
        } catch (IOException e) {
            SmpUtilsMod.LOGGER.error("[QuackedSMP] Failed to process region file {}", mca, e);
        }

        return new long[]{cleared, remaining};
    }

    // Parses "r.X.Z.mca" into {X, Z}. Returns null on failure.
    private static int[] parseRegionCoords(String filename) {
        // Format: r.<regionX>.<regionZ>.mca
        if (!filename.startsWith("r.") || !filename.endsWith(".mca")) return null;
        String[] parts = filename.substring(2, filename.length() - 4).split("\\.");
        if (parts.length != 2) return null;
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
