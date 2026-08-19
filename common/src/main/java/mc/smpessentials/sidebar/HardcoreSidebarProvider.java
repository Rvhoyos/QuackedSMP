package mc.smpessentials.sidebar;

import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.hardcore.HardcoreFormat;
import mc.smpessentials.hardcore.HardcoreLeaderboard;
import mc.smpessentials.hardcore.HardcoreSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Decides WHEN the hardcore run-time board is shown (periodic pulse + on session entry) and builds
// its per-player content. The viewer's own live session + career, then the shared leaderboard.
// Runs on the server thread; SidebarManager owns the actual packet send.
public final class HardcoreSidebarProvider implements SidebarProvider {

    private static final Component TITLE =
            Component.literal("HARDCORE").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

    // Server tick at which each member's current show window ends.
    private final Map<UUID, Long> showUntilTick = new HashMap<>();
    // Server tick of the next periodic pulse. -1 means schedule on the first tick.
    private long nextPeriodicTick = -1L;

    // Per-tick context, refreshed in tick() and read by contentFor().
    private long now;
    private HardcoreSavedData data;
    private HardcoreLeaderboard board; // built lazily, at most once per tick, only when pushing

    @Override
    public void tick(MinecraftServer server) {
        if (!SmpConfig.HARDCORE_SIDEBAR_ENABLED) {
            showUntilTick.clear();
            data = null;
            return;
        }
        now = server.getTickCount();
        data = HardcoreSavedData.get(server);
        board = null;

        if (nextPeriodicTick < 0) nextPeriodicTick = now + randomIntervalTicks();
        if (now >= nextPeriodicTick) {
            openWindowForMembers(server, seconds(SmpConfig.HARDCORE_SIDEBAR_SHOW_SECONDS));
            nextPeriodicTick = now + randomIntervalTicks();
        }
    }

    @Override
    public Optional<SidebarContent> contentFor(ServerPlayer player) {
        if (data == null) return Optional.empty();
        UUID id = player.getUUID();
        if (data.getPlayerSessionName(id) == null) return Optional.empty();

        Long until = showUntilTick.get(id);
        if (until == null || now >= until) {
            showUntilTick.remove(id);
            return Optional.empty();
        }
        if (board == null) board = HardcoreLeaderboard.of(data, System.currentTimeMillis());
        return Optional.of(new SidebarContent(TITLE, buildLines(data, board, player)));
    }

    // Flashes the board for all current members on any session entry (create/join/rejoin).
    public void onSessionEntry(MinecraftServer server) {
        if (!SmpConfig.HARDCORE_SIDEBAR_ENABLED) return;
        openWindowForMembers(server, seconds(SmpConfig.HARDCORE_SIDEBAR_ON_ENTRY_SECONDS));
    }

    // Offers the board to a player who just connected while already in a session, which fires none
    // of the entry triggers. Returns true if this provider claimed the join board, so the caller
    // knows not to offer the welcome board instead.
    public boolean offerOnJoin(ServerPlayer player) {
        if (!SmpConfig.HARDCORE_SIDEBAR_ENABLED) return false;
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        if (HardcoreSavedData.get(server).getPlayerSessionName(player.getUUID()) == null) return false;
        showUntilTick.put(player.getUUID(),
                server.getTickCount() + seconds(SmpConfig.HARDCORE_SIDEBAR_ON_ENTRY_SECONDS));
        return true;
    }

    @Override
    public void onDisconnect(ServerPlayer player) {
        showUntilTick.remove(player.getUUID());
    }

    private void openWindowForMembers(MinecraftServer server, long durationTicks) {
        HardcoreSavedData d = HardcoreSavedData.get(server);
        long end = server.getTickCount() + durationTicks;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (d.getPlayerSessionName(player.getUUID()) != null) {
                showUntilTick.put(player.getUUID(), end);
            }
        }
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
                    ((ServerLevel) player.level()).getServer(), board.champion().player())
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
    // so the board appears at irregular times instead of on a fixed clock.
    private static long randomIntervalTicks() {
        long base = seconds(SmpConfig.HARDCORE_SIDEBAR_INTERVAL_SECONDS);
        double factor = 0.5 + java.util.concurrent.ThreadLocalRandom.current().nextDouble();
        return Math.max(1L, (long) (base * factor));
    }
}
