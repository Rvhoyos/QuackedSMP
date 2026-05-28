package mc.smpessentials.backup;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Streams the contents of a directory into a ZIP file. Entries are stored
 * under the source directory's own name (e.g. zipping {@code run/world/}
 * yields entries like {@code world/level.dat}) so extraction produces a
 * clean folder.
 */
public final class BackupZipper {

    private static final int BUFFER_SIZE = 8 * 1024;

    private BackupZipper() {}

    /** Files that disappear during the walk are skipped rather than aborting. */
    public static void zip(Path src, Path dst) throws IOException {
        if (!Files.isDirectory(src)) {
            throw new IOException("Source is not a directory: " + src);
        }
        Files.createDirectories(dst.getParent());
        String base = src.getFileName().toString();

        try (OutputStream raw = Files.newOutputStream(dst);
             BufferedOutputStream buf = new BufferedOutputStream(raw, BUFFER_SIZE);
             ZipOutputStream zos = new ZipOutputStream(buf)) {

            Files.walkFileTree(src, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.equals(src)) return FileVisitResult.CONTINUE;
                    String name = base + "/" + src.relativize(dir).toString().replace('\\', '/') + "/";
                    ZipEntry entry = new ZipEntry(name);
                    entry.setTime(attrs.lastModifiedTime().toMillis());
                    zos.putNextEntry(entry);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String name = base + "/" + src.relativize(file).toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(name);
                    entry.setTime(attrs.lastModifiedTime().toMillis());
                    zos.putNextEntry(entry);
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // Skip files that disappear mid-zip (e.g. temp save files) instead of aborting.
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
