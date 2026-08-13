package mc.smpessentials.dashboard;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.platform.SmpServices;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;

// Everything a downloader needs to restore the world on another machine with matching
// terrain at unloaded chunks: the seed plus the environment it was generated in.
// Pure value object. See RestoreInfoHandler for serialization and routing.
public record RestoreInfo(long seed, String mcVersion, String loader,
                          String loaderVersion, String modVersion) {

    // Reads a live snapshot from the running server, or null if the server is not ready.
    public static RestoreInfo capture(MinecraftServer server) {
        if (server == null) return null;
        return new RestoreInfo(
                server.overworld().getSeed(),
                SharedConstants.getCurrentVersion().name(),
                SmpServices.PLATFORM.getLoaderName(),
                SmpServices.PLATFORM.getLoaderVersion(),
                SmpUtilsMod.VERSION);
    }
}
