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

    @Override
    public String getModVersion() {
        try {
            return ModList.get().getModContainerById(SmpUtilsMod.MOD_ID)
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }

    @Override
    public String getLoaderName() {
        return "NeoForge";
    }

    @Override
    public String getLoaderVersion() {
        try {
            return ModList.get().getModContainerById("neoforge")
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }

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
