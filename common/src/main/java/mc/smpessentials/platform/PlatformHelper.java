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
}
