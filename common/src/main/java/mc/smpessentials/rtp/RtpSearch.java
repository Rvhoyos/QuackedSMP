package mc.smpessentials.rtp;

import mc.smpessentials.config.ConfigData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * One player's pending /rtp: the warmup countdown, the cancel conditions, and the hunt for a
 * landing spot. Exactly one candidate is tested per tick, so a searching player can never cause
 * more than one chunk to generate in a single tick.
 *
 * The move and damage cancels are written here rather than reused from TeleportScheduler, which
 * takes a Runnable to fire once at the end of a warmup and offers no hook for per-tick work.
 * Bending that class to this shape would pull a working, unrelated system into this feature.
 */
final class RtpSearch {
    enum State { RUNNING, ARRIVED, MOVED, HURT, NO_SPOT, WRONG_LEVEL }

    private static final double MOVE_TOLERANCE_SQR = 0.1;
    private static final int TICKS_PER_SECOND = 20;

    private final RtpLocationFinder finder;
    private final ServerLevel level;
    private final Vec3 origin;
    private final long arriveAtTick;
    private final int maxAttempts;

    private float lastHealth;
    private int attempts;
    private BlockPos destination;

    RtpSearch(ServerPlayer player, ConfigData.RtpProfile profile, int warmupSeconds) {
        this.level = (ServerLevel) player.level();
        this.finder = new RtpLocationFinder(this.level, profile);
        this.origin = player.position();
        this.arriveAtTick = player.level().getGameTime()
                + (long) Math.max(0, warmupSeconds) * TICKS_PER_SECOND;
        this.maxAttempts = Math.max(1, profile.maxAttempts);
        this.lastHealth = player.getHealth();
    }

    /** Advances one tick: tests at most one candidate and reports where the request stands. */
    State tick(ServerPlayer player) {
        // The landing spot was found in one level, so it is only valid there. A dimension change
        // normally trips the move check too, but not if the coordinates happen to match.
        if (player.level() != this.level) return State.WRONG_LEVEL;
        if (player.position().distanceToSqr(this.origin) > MOVE_TOLERANCE_SQR) return State.MOVED;

        // Comparing health beats subscribing to a damage event: it needs no extra platform hook
        // and catches every source, including drowning and poison.
        float health = player.getHealth();
        if (health < this.lastHealth) return State.HURT;
        this.lastHealth = health;

        if (this.destination == null && this.attempts < this.maxAttempts) {
            this.attempts++;
            this.destination = this.finder.tryOnce().orElse(null);
        }

        if (secondsLeft(player) > 0) return State.RUNNING;
        if (this.destination != null) return State.ARRIVED;
        // Warmup is up but the search still has candidates left, so keep looking rather than
        // failing a player who happened to draw a run of bad spots.
        return this.attempts >= this.maxAttempts ? State.NO_SPOT : State.RUNNING;
    }

    BlockPos destination() {
        return this.destination;
    }

    /** How far the chosen landing spot is from the centre distances are measured from. */
    int distanceFromCenter() {
        return this.destination == null ? 0 : this.finder.distanceFromCenter(this.destination);
    }

    /** Whole seconds left on the warmup, for the action bar countdown. */
    long secondsLeft(ServerPlayer player) {
        long ticksLeft = this.arriveAtTick - player.level().getGameTime();
        return Math.max(0L, (ticksLeft + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
    }
}
