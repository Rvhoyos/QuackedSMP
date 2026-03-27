package mc.smpessentials.platform;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Platform abstraction interface.
 * Any methods that require varying implementations across Minecraft modloaders
 * should be stored here.
 */
public interface PlatformHelper {
    /**
     * Gets the path to the configuration directory.
     */
    Path getConfigDir();

    /**
     * Returns the filesystem path of the currently loaded QuackedSMP JAR,
     * or empty if it cannot be determined on this platform.
     */
    default Optional<Path> getActiveModJarPath() {
        return Optional.empty();
    }
}
