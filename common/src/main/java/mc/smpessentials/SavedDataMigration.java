package mc.smpessentials;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// One-time migration for the 1.21.11 → 26.1 world storage restructure.
// Mojang's FileFixerUpper moves vanilla data but ignores mod files.
// Moves quackedsmp_*.dat from data/ to dimensions/minecraft/overworld/data/minecraft/.
public final class SavedDataMigration {

    private SavedDataMigration() {}

    public static void migrate(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path oldDir = root.resolve("data");
        Path newDir = root.resolve("dimensions/minecraft/overworld/data/minecraft");

        if (!Files.isDirectory(oldDir)) return;

        try (var files = Files.list(oldDir)) {
            var toMigrate = files
                    .filter(p -> p.getFileName().toString().startsWith("quackedsmp_")
                            && p.getFileName().toString().endsWith(".dat"))
                    .toList();

            if (toMigrate.isEmpty()) return;

            Files.createDirectories(newDir);
            for (Path old : toMigrate) {
                Path dest = newDir.resolve(old.getFileName());
                if (Files.exists(dest) && Files.size(dest) > 0) continue;
                Files.move(old, dest);
                SmpUtilsMod.LOGGER.info("[QuackedSMP] Migrated {} to 26.1 data path", old.getFileName());
            }
        } catch (IOException e) {
            SmpUtilsMod.LOGGER.error("[QuackedSMP] SavedData migration failed", e);
        }
    }
}
