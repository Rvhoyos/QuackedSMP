package mc.smpessentials.platform;

import java.nio.file.Path;
import java.util.Optional;

// Platform abstraction interface for loader-specific implementations.
public interface PlatformHelper {
    Path getConfigDir();

    // Returns the path of the currently loaded QuackedSMP JAR, or empty if undetermined.
    default Optional<Path> getActiveModJarPath() {
        return Optional.empty();
    }

    // Returns the mod version string from the platform's mod loader.
    default String getModVersion() {
        return "unknown";
    }

    // Human-readable loader name, e.g. "NeoForge" or "Fabric".
    default String getLoaderName() {
        return "unknown";
    }

    // Loader version string, e.g. the NeoForge or Fabric Loader version.
    default String getLoaderVersion() {
        return "unknown";
    }
}
