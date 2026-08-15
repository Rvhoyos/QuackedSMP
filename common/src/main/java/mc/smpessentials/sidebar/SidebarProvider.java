package mc.smpessentials.sidebar;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

// A single feature's sidebar policy: decides WHEN it wants the slot and WHAT to show. One provider
// per feature (hardcore, welcome). SidebarManager owns the single display slot and picks the
// highest-priority provider that wants it, so providers never touch PlayerSidebar directly.
public interface SidebarProvider {

    // Per-tick housekeeping (advance schedules, refresh per-tick caches). Called once per server
    // tick before any contentFor.
    default void tick(MinecraftServer server) {}

    // The content this provider wants shown to the player right now, or empty if it does not want
    // the slot for this player.
    Optional<SidebarContent> contentFor(ServerPlayer player);

    // Drop any per-player state on disconnect.
    default void onDisconnect(ServerPlayer player) {}
}
