package mc.smpessentials.endfinder;

import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.hardcore.HardcoreSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.Waypoint;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Guides a player to the nearest stronghold while they hold an eye of ender.
 *
 * Sends a position waypoint straight to the player's connection, which the vanilla Locator Bar
 * renders as a dot with a direction and an up/down arrow, plus an action bar readout of the
 * horizontal distance. Nothing is required on the client, and no entity is spawned.
 *
 * The waypoint is sent per connection rather than through {@code ServerWaypointManager}, so the
 * {@code locator_bar} gamerule does not apply to it and player positions are never broadcast.
 */
public final class EndFinder {

    private EndFinder() {}

    // Tint for the stronghold dot, so it reads as ours and not as another player.
    private static final int MARKER_COLOR = 0x54C99B;

    // How often the action bar distance is refreshed.
    private static final int ACTION_BAR_INTERVAL_TICKS = 20;

    // Last stronghold search per player: where it was run from, what it found (null when nothing
    // was found), and when. The nearest stronghold changes as a player travels, so this is
    // invalidated by distance travelled rather than kept for the session.
    private static final Map<UUID, Search> SEARCHES = new HashMap<>();

    // Waypoint position currently on each player's client, so we only send a packet on a change.
    private static final Map<UUID, BlockPos> SHOWN = new HashMap<>();

    private record Search(BlockPos origin, BlockPos result, long tick) {}

    /**
     * Runs the guide for one player. Called once per player per server tick. Retracts the waypoint
     * whenever the player is not holding an eye, leaves the overworld, or the feature is turned off.
     */
    public static void tick(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();

        if (!SmpConfig.END_FINDER_ENABLED
                || level.dimension() != Level.OVERWORLD
                || !holdingEye(player)) {
            hide(player);
            return;
        }

        BlockPos target = resolve(level, player);
        if (target == null) {
            // No stronghold within the search radius: retract rather than leave a stale dot.
            hide(player);
            return;
        }

        show(player, target);
        if (SmpConfig.END_FINDER_ACTION_BAR
                && player.tickCount % ACTION_BAR_INTERVAL_TICKS == 0
                && !hasSpectatorHud(player)) {
            player.sendSystemMessage(distanceText(player, target), true);
        }
    }

    /** Drops all per-player state for a player who left. No packet: the connection is gone. */
    public static void onDisconnect(UUID uuid) {
        SEARCHES.remove(uuid);
        SHOWN.remove(uuid);
    }

    private static boolean holdingEye(ServerPlayer player) {
        return player.getMainHandItem().is(Items.ENDER_EYE)
                || player.getOffhandItem().is(Items.ENDER_EYE);
    }

    /**
     * Returns the nearest stronghold, reusing the cached result until the player has both travelled
     * past the recheck distance and waited out the cooldown. Only ever called in the overworld,
     * where strongholds are guaranteed to exist; running a structure search in a dimension that has
     * none is what makes this call hang the server thread.
     */
    private static BlockPos resolve(ServerLevel level, ServerPlayer player) {
        UUID id = player.getUUID();
        BlockPos here = player.blockPosition();
        Search last = SEARCHES.get(id);

        if (last != null && !stale(level, last, here)) return last.result();

        BlockPos found = level.findNearestMapStructure(
                StructureTags.EYE_OF_ENDER_LOCATED, here,
                Math.max(1, SmpConfig.END_FINDER_SEARCH_RADIUS), false);
        // A miss is cached too, so a world with no strongholds does not re-search every tick.
        SEARCHES.put(id, new Search(here, found, level.getGameTime()));
        return found;
    }

    private static boolean stale(ServerLevel level, Search last, BlockPos here) {
        long cooldown = Math.max(0, SmpConfig.END_FINDER_RECHECK_COOLDOWN_SECONDS) * 20L;
        if (level.getGameTime() - last.tick() < cooldown) return false;
        double moved = Math.max(0, SmpConfig.END_FINDER_RECHECK_DISTANCE);
        return here.distSqr(last.origin()) > moved * moved;
    }

    private static void show(ServerPlayer player, BlockPos target) {
        UUID id = player.getUUID();
        BlockPos shown = SHOWN.get(id);
        if (target.equals(shown)) return;

        Waypoint.Icon icon = new Waypoint.Icon();
        icon.color = Optional.of(MARKER_COLOR);
        player.connection.send(shown == null
                ? ClientboundTrackedWaypointPacket.addWaypointPosition(markerId(id), icon, target)
                : ClientboundTrackedWaypointPacket.updateWaypointPosition(markerId(id), icon, target));
        SHOWN.put(id, target);
    }

    private static void hide(ServerPlayer player) {
        UUID id = player.getUUID();
        if (SHOWN.remove(id) == null) return;
        player.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(markerId(id)));
    }

    // Horizontal distance only: the locate position carries no real Y, and how far to walk is what
    // the player is actually asking.
    private static Component distanceText(ServerPlayer player, BlockPos target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        long blocks = Math.round(Math.sqrt(dx * dx + dz * dz));
        return Component.literal("Stronghold ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(blocks + " blocks").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" away").withStyle(ChatFormatting.GRAY));
    }

    // The hardcore spectator HUD owns the action bar for dead session members; do not fight it.
    private static boolean hasSpectatorHud(ServerPlayer player) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        HardcoreSavedData data = HardcoreSavedData.get(server);
        String session = data.getPlayerSessionName(player.getUUID());
        if (session == null) return false;
        HardcoreSavedData.SessionData s = data.getSession(session);
        return s != null && s.isDead(player.getUUID());
    }

    // Stable per-player marker id, derived so it can never collide with a player's own waypoint.
    private static UUID markerId(UUID player) {
        return UUID.nameUUIDFromBytes(("quacksmp:stronghold:" + player).getBytes(StandardCharsets.UTF_8));
    }
}
