package mc.smpessentials.sidebar;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Single owner of the per-player DisplaySlot.SIDEBAR. Each tick it asks its providers, in priority
// order, what to show each player; the first provider that wants the slot wins, and nothing else
// touches the slot. This is what keeps the hardcore and welcome sidebars from clobbering each
// other, since only one board can occupy the slot at a time. Content is re-sent only when it
// changes, so a static board costs no per-tick packets.
public final class SidebarManager {

    public static final SidebarManager INSTANCE = new SidebarManager();

    // Concrete providers, exposed so feature code can drive their triggers.
    public final HardcoreSidebarProvider hardcore = new HardcoreSidebarProvider();
    public final WelcomeSidebarProvider welcome = new WelcomeSidebarProvider();

    // Priority order, highest first. A hardcore member never also wants the welcome board, so this
    // ordering only makes the (never-hit) tie deterministic.
    private final List<SidebarProvider> providers = List.of(hardcore, welcome);

    // Last content rendered per player, so we skip re-sending unchanged boards.
    private final Map<UUID, SidebarContent> lastRendered = new HashMap<>();

    private SidebarManager() {}

    public void tick(MinecraftServer server) {
        for (SidebarProvider provider : providers) provider.tick(server);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            SidebarContent content = pick(player);
            if (content != null) {
                if (!content.equals(lastRendered.get(id))) {
                    PlayerSidebar.INSTANCE.show(player, content.title(), content.lines());
                    lastRendered.put(id, content);
                }
            } else if (lastRendered.remove(id) != null) {
                PlayerSidebar.INSTANCE.clear(player);
            }
        }
    }

    // First provider that wants the slot for this player, or null if none do.
    private SidebarContent pick(ServerPlayer player) {
        for (SidebarProvider provider : providers) {
            Optional<SidebarContent> content = provider.contentFor(player);
            if (content.isPresent()) return content.get();
        }
        return null;
    }

    // Player just connected. A member of a live hardcore session gets the run board (a plain
    // reconnect fires none of the session-entry triggers); everyone else gets the welcome board.
    // When the hardcore board is disabled the welcome board still self-suppresses for members, so
    // they stay boardless either way.
    public void onJoin(ServerPlayer player) {
        if (hardcore.offerOnJoin(player)) return;
        welcome.openFor(player);
    }

    // A hardcore session was entered (create/join/resume): flash the hardcore board for members.
    public void onSessionEntry(MinecraftServer server) {
        hardcore.onSessionEntry(server);
    }

    // Player returned to normal survival from a hardcore session: offer the welcome board.
    public void onEnterSurvival(ServerPlayer player) {
        welcome.openFor(player);
    }

    public void onDisconnect(ServerPlayer player) {
        lastRendered.remove(player.getUUID());
        PlayerSidebar.INSTANCE.forget(player.getUUID());
        for (SidebarProvider provider : providers) provider.onDisconnect(player);
    }
}
