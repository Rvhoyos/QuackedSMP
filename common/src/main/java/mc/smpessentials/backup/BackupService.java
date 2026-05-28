package mc.smpessentials.backup;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Owns the lifecycle of world snapshot zips under {@code <BACKUP_DIR>/}.
 * {@link #create} is asynchronous; one snapshot may be in flight at a time.
 */
public final class BackupService {

    private static final DateTimeFormatter TS_FMT     = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern           NAME_RX    = Pattern.compile("^world-\\d{8}-\\d{6}\\.zip$");
    private static final String            SUFFIX_TMP = ".tmp";

    private static final BackupService INSTANCE = new BackupService();

    private final AtomicBoolean   inProgress = new AtomicBoolean(false);
    private final ExecutorService worker     = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Backup-Worker");
        t.setDaemon(true);
        return t;
    });

    private BackupService() {}

    public static BackupService get() { return INSTANCE; }

    public boolean isRunning() { return inProgress.get(); }

    /**
     * Kicks off a snapshot on a worker thread and returns immediately.
     * @throws BusyException if a snapshot is already running
     */
    public void create(MinecraftServer server) {
        if (server == null) throw new IllegalStateException("Server not ready");
        if (!inProgress.compareAndSet(false, true)) {
            throw new BusyException();
        }
        worker.submit(() -> runCreate(server));
    }

    public List<Snapshot> list() {
        Path dir = backupsDir();
        if (!Files.isDirectory(dir)) return List.of();
        List<Snapshot> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String name = p.getFileName().toString();
                if (!NAME_RX.matcher(name).matches()) continue;
                try {
                    out.add(new Snapshot(name, Files.size(p),
                            Files.getLastModifiedTime(p).toMillis()));
                } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            return List.of();
        }
        out.sort(Comparator.comparingLong(Snapshot::createdAt).reversed());
        return out;
    }

    /**
     * Resolves the path of an existing snapshot.
     * @throws InvalidNameException if the name is malformed
     * @throws NoSuchFileException  if the snapshot is missing
     */
    public Path pathOf(String name) throws IOException {
        Path file = resolveValid(name);
        if (!Files.isRegularFile(file)) throw new NoSuchFileException(name);
        return file;
    }

    public void delete(String name) throws IOException {
        Path file = resolveValid(name);
        if (!Files.deleteIfExists(file)) throw new NoSuchFileException(name);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void runCreate(MinecraftServer server) {
        String name = "world-" + TS_FMT.format(LocalDateTime.now()) + ".zip";
        Path dir   = backupsDir();
        Path dst   = dir.resolve(name);
        Path tmp   = dir.resolve(name + SUFFIX_TMP);
        try {
            Files.createDirectories(dir);
            runOnServer(server, () -> {
                server.saveEverything(true, true, true);
                for (ServerLevel level : server.getAllLevels()) {
                    level.noSave = true;
                }
            });
            try {
                Path worldDir = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                BackupZipper.zip(worldDir, tmp);
                Files.move(tmp, dst, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                prune();
                SmpUtilsMod.LOGGER.info("[Backup] Created snapshot {} ({} bytes)", name, Files.size(dst));
            } finally {
                runOnServer(server, () -> {
                    for (ServerLevel level : server.getAllLevels()) {
                        level.noSave = false;
                    }
                });
            }
        } catch (Exception e) {
            SmpUtilsMod.LOGGER.error("[Backup] Snapshot failed: {}", e.getMessage(), e);
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        } finally {
            inProgress.set(false);
        }
    }

    private static void runOnServer(MinecraftServer server, Runnable task) throws Exception {
        CompletableFuture<Void> fut = new CompletableFuture<>();
        server.execute(() -> {
            try { task.run(); fut.complete(null); }
            catch (Throwable t) { fut.completeExceptionally(t); }
        });
        fut.get();
    }

    private void prune() {
        int keep = Math.max(1, SmpConfig.BACKUP_MAX_COUNT);
        List<Snapshot> all = list();
        for (int i = keep; i < all.size(); i++) {
            try { delete(all.get(i).name()); }
            catch (IOException e) {
                SmpUtilsMod.LOGGER.warn("[Backup] Failed to prune {}: {}", all.get(i).name(), e.getMessage());
            }
        }
    }

    private Path backupsDir() {
        return Path.of(SmpConfig.BACKUP_DIR).toAbsolutePath().normalize();
    }

    private Path resolveValid(String name) {
        if (name == null || !NAME_RX.matcher(name).matches()) {
            throw new InvalidNameException(name);
        }
        return backupsDir().resolve(name);
    }

    // ── Types ─────────────────────────────────────────────────────────────────

    public record Snapshot(String name, long sizeBytes, long createdAt) {}

    public static final class BusyException extends RuntimeException {
        public BusyException() { super("Backup already in progress"); }
    }

    public static final class InvalidNameException extends RuntimeException {
        public InvalidNameException(String name) { super("Invalid backup name: " + name); }
    }
}
