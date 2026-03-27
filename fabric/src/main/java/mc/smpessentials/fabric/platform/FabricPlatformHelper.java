package mc.smpessentials.fabric.platform;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.platform.PlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.Optional;

public class FabricPlatformHelper implements PlatformHelper {
    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    /**
     * Returns the JAR (or directory in dev) that FabricLoader loaded for this mod.
     * Returns empty if the mod container cannot be found — treated as a no-op by
     * {@link mc.smpessentials.SmpUtilsMod#purgeOldModVersions()}.
     */
    @Override
    public Optional<Path> getActiveModJarPath() {
        try {
            return FabricLoader.getInstance()
                    .getModContainer(SmpUtilsMod.MOD_ID)
                    .flatMap(c -> c.getOrigin().getPaths().stream().findFirst());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
