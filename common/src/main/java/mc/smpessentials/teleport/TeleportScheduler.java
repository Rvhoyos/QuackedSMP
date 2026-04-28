package mc.smpessentials.teleport;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class TeleportScheduler {
    // public static final long WARMUP_TICKS = 100L; // Moved to config
    private static final Map<UUID, PendingTeleport> pending = new ConcurrentHashMap<>();

    private TeleportScheduler() {
    }

    public static void init() {
        // Nothing to init anymore; platforms subscribe and call statically
    }

    public static void onPlayerTick(ServerPlayer sp) {
        tick(sp);
    }

    public static void onPlayerHurt(ServerPlayer sp) {
        onDamage(sp);
    }

    public static void schedule(ServerPlayer player, Runnable action) {
        // Cancel existing if any (resets timer)
        if (pending.remove(player.getUUID()) != null) {
            player.sendSystemMessage(Component.literal("\u00a7eTeleport rescheduled."), true);
        }

        long ticks = mc.smpessentials.config.SmpConfig.TP_WARMUP * 20L;
        long targetTime = ((net.minecraft.server.level.ServerLevel) player.level()).getGameTime() + ticks;
        PendingTeleport p = new PendingTeleport(player.getUUID(), action, player.position(), targetTime);
        pending.put(player.getUUID(), p);
    }

    private static void cancel(ServerPlayer player, String reason) {
        if (pending.remove(player.getUUID()) != null) {
            player.sendSystemMessage(Component.literal("Teleport cancelled: " + reason), true); // Action bar
        }
    }

    // Called every tick via event
    private static void tick(ServerPlayer player) {
        PendingTeleport p = pending.get(player.getUUID());
        if (p == null)
            return;

        // 1. Check Move (> 0.1 block drift)
        if (player.position().distanceToSqr(p.startPos) > 0.1) {
            cancel(player, "You moved!");
            return;
        }

        // 2. Check Time
        long gameTime = player.level().getGameTime();
        if (gameTime >= p.scheduledTick) {
            pending.remove(player.getUUID());
            p.action.run();
        } else {
            // Live countdown on action bar (refreshed every tick so it stays visible)
            long ticksLeft = p.scheduledTick - gameTime;
            long secsLeft = Math.max(1, (ticksLeft + 19) / 20);
            player.sendSystemMessage(
                    Component.literal("\u00a7eTeleporting in \u00a7f" + secsLeft + "s\u00a7e... Don't move!"), true);
        }
    }

    // Hook: Hurt
    private static void onDamage(ServerPlayer player) {
        if (pending.containsKey(player.getUUID())) {
            cancel(player, "Took damage!");
        }
    }

    private record PendingTeleport(UUID playerId, Runnable action, Vec3 startPos, long scheduledTick) {
    }
}
