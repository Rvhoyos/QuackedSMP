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

    @Override
    public String getModVersion() {
        return FabricLoader.getInstance()
                .getModContainer(SmpUtilsMod.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public String getLoaderName() {
        return "Fabric";
    }

    @Override
    public String getLoaderVersion() {
        return FabricLoader.getInstance()
                .getModContainer("fabricloader")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

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
