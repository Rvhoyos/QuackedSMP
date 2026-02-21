package mc.smpessentials.neoforge;

import net.neoforged.fml.common.Mod;

import net.neoforged.neoforge.common.NeoForge;
import mc.smpessentials.SmpUtilsMod;

@Mod(SmpUtilsMod.MOD_ID)
public final class SmpUtilsModNeoForge {
    public SmpUtilsModNeoForge() {
        // Run our common setup.
        SmpUtilsMod.init();

        // Register our events.
        NeoForge.EVENT_BUS.register(SmpEvents.class);
    }
}
