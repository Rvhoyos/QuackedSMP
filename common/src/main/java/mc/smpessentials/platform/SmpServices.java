package mc.smpessentials.platform;

import java.util.ServiceLoader;

/**
 * Service locator for platform-specific implementations.
 */
public class SmpServices {
    public static final PlatformHelper PLATFORM = load(PlatformHelper.class);

    private static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        return loadedService;
    }
}
