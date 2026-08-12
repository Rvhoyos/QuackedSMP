package mc.smpessentials.hardcore.sidebar;

import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.hardcore.HardcoreFormat;
import mc.smpessentials.hardcore.HardcoreLeaderboard;
import mc.smpessentials.hardcore.HardcoreSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Decides WHEN the per-player sidebar is shown (periodic pulse + on session entry) and builds
// its per-player content. Runs on the server thread. Delegates all packet work to PlayerSidebar.
public final class HardcoreSidebarController {

    public static final HardcoreSidebarController INSTANCE = new HardcoreSidebarController();

    // Server tick at which each player's current show window ends.
    private final Map<UUID, Long> showUntilTick = new HashMap<>();
    // Server tick of the next periodic pulse. -1 means schedule on the first tick.
    private long nextPeriodicTick = -1L;

    private HardcoreSidebarController() {}

    public void tick(MinecraftServer server) {
        if (!SmpConfig.HARDCORE_SIDEBAR_ENABLED) {
            if (!showUntilTick.isEmpty()) clearAll(server);
            return;
        }

        long now = server.getTickCount();
        if (nextPeriodicTick < 0) nextPeriodicTick = now + randomIntervalTicks();
        if (now >= nextPeriodicTick) {
            openWindowForMembers(server, seconds(SmpConfig.HARDCORE_SIDEBAR_SHOW_SECONDS));
            nextPeriodicTick = now + randomIntervalTicks();
        }

        HardcoreSavedData data = HardcoreSavedData.get(server);
        HardcoreLeaderboard board = null; // built lazily, at most once per tick, only when pushing
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            boolean member = data.getPlayerSessionName(id) != null;
            Long until = showUntilTick.get(id);
            boolean windowOpen = member && until != null && now < until;
            if (windowOpen) {
                // Push on open, then refresh about once a second while the window lasts.
                if (!PlayerSidebar.INSTANCE.isShown(id) || now % 20 == 0) {
                    if (board == null) board = HardcoreLeaderboard.of(data, System.currentTimeMillis());
                    PlayerSidebar.INSTANCE.show(player, buildLines(data, board, player));
                }
            } else if (PlayerSidebar.INSTANCE.isShown(id)) {
                PlayerSidebar.INSTANCE.clear(player);
                showUntilTick.remove(id);
            }
        }
    }

    // Flashes the sidebar for all current members on any session entry (create/join/rejoin).
    public void onSessionEntry(MinecraftServer server) {
        if (!SmpConfig.HARDCORE_SIDEBAR_ENABLED) return;
        openWindowForMembers(server, seconds(SmpConfig.HARDCORE_SIDEBAR_ON_ENTRY_SECONDS));
    }

    public void onDisconnect(ServerPlayer player) {
        UUID id = player.getUUID();
        showUntilTick.remove(id);
        PlayerSidebar.INSTANCE.forget(id);
    }

    private void openWindowForMembers(MinecraftServer server, long durationTicks) {
        HardcoreSavedData data = HardcoreSavedData.get(server);
        long end = server.getTickCount() + durationTicks;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (data.getPlayerSessionName(player.getUUID()) != null) {
                showUntilTick.put(player.getUUID(), end);
            }
        }
    }

    private void clearAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (PlayerSidebar.INSTANCE.isShown(player.getUUID())) PlayerSidebar.INSTANCE.clear(player);
        }
        showUntilTick.clear();
    }

    // Builds the viewer's own data (live session + career) followed by the shared leaderboard,
    // stacked so both are on screen together.
    private List<Component> buildLines(HardcoreSavedData data, HardcoreLeaderboard board, ServerPlayer player) {
        List<Component> lines = new ArrayList<>();

        // Own live session
        String sessionName = data.getPlayerSessionName(player.getUUID());
        HardcoreSavedData.SessionData session = sessionName == null ? null : data.getSession(sessionName);
        if (session != null) {
            boolean dead = session.isDead(player.getUUID());
            lines.add(kv("Session:", session.getName(), ChatFormatting.WHITE));
            lines.add(kv("Run:", HardcoreFormat.duration(session.runMillis(System.currentTimeMillis())), ChatFormatting.GREEN));
            lines.add(kv("State:", dead ? "Dead" : "Alive", dead ? ChatFormatting.RED : ChatFormatting.GREEN));
            lines.add(kv("Deaths:", session.getDeaths() + "/" + session.getThreshold(), ChatFormatting.YELLOW));
        }

        // Own career
        HardcoreLeaderboard.PlayerStat me = board.statFor(player.getUUID());
        if (!me.isEmpty()) {
            lines.add(kv("Record:", me.wins() + "W/" + me.losses() + "L ("
                    + Math.round(me.winRate() * 100) + "%)", ChatFormatting.GOLD));
        }

        lines.add(Component.literal(" "));

        // Shared leaderboard: compact fun records
        lines.add(Component.literal("Server").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        lines.add(kv("Dragons slain:", String.valueOf(board.dragonsSlain()), ChatFormatting.WHITE));
        lines.add(kv("Body count:", String.valueOf(board.bodyCount()), ChatFormatting.RED));
        if (board.champion() != null) {
            lines.add(kv("Champion:", HardcoreSavedData.resolveName(
                    ((net.minecraft.server.level.ServerLevel) player.level()).getServer(), board.champion().player())
                    + " (" + board.champion().wins() + "W)", ChatFormatting.YELLOW));
        }
        if (board.biggestParty() != null) {
            lines.add(kv("Biggest party:", board.biggestParty().peakPlayers() + " players", ChatFormatting.AQUA));
        }
        lines.add(kv("Active runs:", String.valueOf(board.activeRuns().size()), ChatFormatting.AQUA));
        return lines;
    }

    private static Component kv(String label, String value, ChatFormatting valueColor) {
        return Component.literal(label + " ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(valueColor));
    }

    private static long seconds(int s) {
        return Math.max(1, s) * 20L;
    }

    // Randomized gap until the next periodic pulse: the configured interval jittered by +/-50%,
    // so the sidebar appears at irregular times instead of on a fixed clock.
    private static long randomIntervalTicks() {
        long base = seconds(SmpConfig.HARDCORE_SIDEBAR_INTERVAL_SECONDS);
        double factor = 0.5 + java.util.concurrent.ThreadLocalRandom.current().nextDouble();
        return Math.max(1L, (long) (base * factor));
    }
}
