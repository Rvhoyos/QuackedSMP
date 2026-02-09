package mc.smpessentials.teleport;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
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
        // Register Tick
        dev.architectury.event.events.common.TickEvent.PLAYER_POST.register(player -> {
            if (player instanceof ServerPlayer sp) {
                tick(sp);
            }
        });

        // Register Damage (Combat Tag / Cancel)
        EntityEvent.LIVING_HURT.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer sp) {
                onDamage(sp);
            }
            return EventResult.pass();
        });
    }

    public static void schedule(ServerPlayer player, Runnable action) {
        // Cancel existing if any (resets timer)
        if (pending.remove(player.getUUID()) != null) {
            player.displayClientMessage(Component.literal("Rescheduling teleport..."), true);
        }

        long ticks = mc.smpessentials.config.SmpConfig.TP_WARMUP * 20L;
        long targetTime = ((net.minecraft.server.level.ServerLevel) player.level()).getGameTime() + ticks;
        PendingTeleport p = new PendingTeleport(player.getUUID(), action, player.position(), targetTime);
        pending.put(player.getUUID(), p);

        player.displayClientMessage(
                Component.literal(
                        "Teleporting in " + mc.smpessentials.config.SmpConfig.TP_WARMUP + " seconds... Don't move!"),
                false);
    }

    private static void cancel(ServerPlayer player, String reason) {
        if (pending.remove(player.getUUID()) != null) {
            player.displayClientMessage(Component.literal("Teleport cancelled: " + reason), true); // Action bar
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
        // Ensure we are on the same level/time reference? (Player might change dims?)
        // If player changed dims, pos check usually fails or throws. Safe to assume
        // cancellation if level mismatch.
        // Simple check:
        if (player.level().getGameTime() >= p.scheduledTick) {
            // Execute!
            pending.remove(player.getUUID());
            p.action.run();
            // Sound effect?
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
