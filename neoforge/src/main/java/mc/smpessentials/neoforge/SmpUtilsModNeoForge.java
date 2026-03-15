package mc.smpessentials.neoforge;

import net.neoforged.fml.common.Mod;

import net.neoforged.neoforge.common.NeoForge;
import mc.smpessentials.SmpUtilsMod;

/**
 * NeoForge mod entrypoint for QuackedSMP.
 *
 * <p>Runs common initialisation via {@link mc.smpessentials.SmpUtilsMod#init()} and
 * registers {@link SmpEvents} on the NeoForge event bus.  All game logic lives in
 * the {@code common} module; this class is intentionally thin.
 */
@Mod(SmpUtilsMod.MOD_ID)
public final class SmpUtilsModNeoForge {
    public SmpUtilsModNeoForge() {
        // Run our common setup.
        SmpUtilsMod.init();

        // Register our events.
        NeoForge.EVENT_BUS.register(SmpEvents.class);
    }
}
