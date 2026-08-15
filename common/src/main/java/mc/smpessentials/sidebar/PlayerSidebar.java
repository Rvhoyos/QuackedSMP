package mc.smpessentials.sidebar;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Low-level per-connection sidebar sender (mechanism only, no policy). Sends scoreboard packets
// directly to individual players so each can show different content, which vanilla's shared
// ServerScoreboard cannot do. The objective is never registered on the live scoreboard; it
// exists only as client-side state built from the packets we send. Title is supplied per show,
// so the single slot can host different boards (e.g. hardcore vs welcome) over time.
// One logical board occupies the slot at a time; SidebarManager guarantees that.
public final class PlayerSidebar {

    public static final PlayerSidebar INSTANCE = new PlayerSidebar();

    private static final String OBJECTIVE_NAME = "qsmp_sidebar";

    // Built lazily (needs a scoreboard reference) and reused for every player. Its display name is
    // mutated to the caller's title immediately before each ADD/CHANGE send, so it holds the right
    // title at packet-construction time even though the instance is shared across players.
    private Objective objective;
    // Players who currently have the objective + display slot set.
    private final Set<UUID> shown = new HashSet<>();
    // Last title sent per player, so a board swap emits a CHANGE only when the title actually differs.
    private final Map<UUID, Component> lastTitle = new HashMap<>();
    // Last line count sent per player, so shrinking boards reset their stale lines.
    private final Map<UUID, Integer> lineCounts = new HashMap<>();

    private PlayerSidebar() {}

    private Objective objective(ServerPlayer player, Component title) {
        if (objective == null) {
            objective = new Objective(player.level().getScoreboard(), OBJECTIVE_NAME,
                    ObjectiveCriteria.DUMMY, title,
                    ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
        }
        return objective;
    }

    // Shows or refreshes the sidebar for one player. Top line first.
    public void show(ServerPlayer player, Component title, List<Component> lines) {
        Objective obj = objective(player, title);
        UUID id = player.getUUID();
        if (shown.add(id)) {
            obj.setDisplayName(title);
            player.connection.send(new ClientboundSetObjectivePacket(obj, ClientboundSetObjectivePacket.METHOD_ADD));
            player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, obj));
            lastTitle.put(id, title);
        } else if (!title.equals(lastTitle.get(id))) {
            obj.setDisplayName(title);
            player.connection.send(new ClientboundSetObjectivePacket(obj, ClientboundSetObjectivePacket.METHOD_CHANGE));
            lastTitle.put(id, title);
        }
        int n = lines.size();
        for (int i = 0; i < n; i++) {
            // Higher score renders higher; number itself is hidden by the objective's blank format.
            player.connection.send(new ClientboundSetScorePacket(
                    lineKey(i), OBJECTIVE_NAME, n - i, Optional.of(lines.get(i)), Optional.empty()));
        }
        int prev = lineCounts.getOrDefault(id, 0);
        for (int i = n; i < prev; i++) {
            player.connection.send(new ClientboundResetScorePacket(lineKey(i), OBJECTIVE_NAME));
        }
        lineCounts.put(id, n);
    }

    // Removes the sidebar for one player. No-op if not currently shown.
    public void clear(ServerPlayer player) {
        UUID id = player.getUUID();
        lineCounts.remove(id);
        lastTitle.remove(id);
        if (!shown.remove(id)) return;
        if (objective != null) {
            player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, null));
            player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_REMOVE));
        }
    }

    public boolean isShown(UUID id) {
        return shown.contains(id);
    }

    // Drops tracking for a player without sending packets (use on disconnect).
    public void forget(UUID id) {
        shown.remove(id);
        lastTitle.remove(id);
        lineCounts.remove(id);
    }

    // Stable, unique per-line score-holder key. Never rendered (each line sets a display component).
    private static String lineKey(int index) {
        return "qsmp_sidebar_" + index;
    }
}
