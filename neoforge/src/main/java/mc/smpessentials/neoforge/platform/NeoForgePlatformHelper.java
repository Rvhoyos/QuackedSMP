package mc.smpessentials.neoforge.platform;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.platform.PlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Optional;

public class NeoForgePlatformHelper implements PlatformHelper {
    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    /**
     * Returns the JAR path from NeoForge's ModList for the running QuackedSMP mod file.
     * Returns empty if the mod file cannot be resolved — treated as a no-op by
     * {@link mc.smpessentials.SmpUtilsMod#purgeOldModVersions()}.
     */
    @Override
    public Optional<Path> getActiveModJarPath() {
        try {
            return Optional.of(
                    ModList.get().getModFileById(SmpUtilsMod.MOD_ID).getFile().getFilePath());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
