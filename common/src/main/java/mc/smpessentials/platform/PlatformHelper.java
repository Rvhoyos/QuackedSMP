package mc.smpessentials.platform;

import java.nio.file.Path;

/**
 * Platform abstraction interface.
 * Any methods that require varying implementations across Minecraft modloaders
 * should be stored here.
 */
public interface PlatformHelper {
    /**
     * Gets the path to the configuration directory.
     * 
     * @return the configuration path
     */
    Path getConfigDir();
}
