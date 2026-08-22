package mc.smpessentials.rtp;

import mc.smpessentials.config.ConfigData;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns every in-flight /rtp. The command and the platform tick hooks talk to this and nothing
 * else in the package; where a player can land and what they receive on arrival live in
 * {@link RtpLocationFinder} and {@link RtpRewards}.
 *
 * Cooldowns are kept in memory rather than saved: a few minutes of movement throttling is not
 * worth persisting across a restart, unlike the arrival reward, which {@link RtpData} does save.
 */
public final class RtpService {
    /** Why a /rtp request was accepted or turned down. */
    public enum Result { OK, DISABLED, NO_PROFILE, ALREADY_RUNNING, ON_COOLDOWN }

    private static final RtpService INSTANCE = new RtpService();

    private final Map<UUID, RtpSearch> searches = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    private RtpService() {}

    public static RtpService get() {
        return INSTANCE;
    }

    /** Starts a search for this player, or explains why one cannot start. */
    public Result request(ServerPlayer player) {
        if (!SmpConfig.RTP_ENABLED) return Result.DISABLED;

        ConfigData.RtpProfile profile = RtpProfiles.forDimension(player.level().dimension());
        if (profile == null) return Result.NO_PROFILE;
        if (this.searches.containsKey(player.getUUID())) return Result.ALREADY_RUNNING;
        if (remainingCooldownSeconds(player.getUUID()) > 0) return Result.ON_COOLDOWN;

        this.searches.put(player.getUUID(),
                new RtpSearch(player, profile, SmpConfig.RTP_WARMUP_SECONDS));
        return Result.OK;
    }

    public long remainingCooldownSeconds(UUID uuid) {
        Long until = this.cooldownUntil.get(uuid);
        if (until == null) return 0L;
        long remainingMs = until - System.currentTimeMillis();
        return remainingMs > 0 ? (remainingMs + 999) / 1000 : 0L;
    }

    /** Called once per player per tick from both platform tick loops. */
    public void tick(ServerPlayer player) {
        RtpSearch search = this.searches.get(player.getUUID());
        if (search == null) return;

        switch (search.tick(player)) {
            case RUNNING -> showCountdown(player, search);
            case MOVED -> cancel(player, "You moved!");
            case HURT -> cancel(player, "Took damage!");
            case NO_SPOT -> cancel(player, "No safe spot found. Try again.");
            case WRONG_LEVEL -> cancel(player, "You changed dimension.");
            case ARRIVED -> arrive(player, search);
        }
    }

    public void forget(UUID uuid) {
        this.searches.remove(uuid);
        this.cooldownUntil.remove(uuid);
    }

    private void arrive(ServerPlayer player, RtpSearch search) {
        this.searches.remove(player.getUUID());

        BlockPos destination = search.destination();
        ServerLevel level = (ServerLevel) player.level();
        player.stopRiding();
        player.teleportTo(level,
                destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot(), false);

        this.cooldownUntil.put(player.getUUID(),
                System.currentTimeMillis() + Math.max(0, SmpConfig.RTP_COOLDOWN_SECONDS) * 1000L);

        // Re-read the profile rather than holding one from the request: a /smp reload during the
        // warmup can have changed it, and the reward should follow the live config.
        ConfigData.RtpProfile profile = RtpProfiles.forDimension(level.dimension());
        if (profile == null) return;

        grantRewardIfDue(player, profile);
        announceArrival(player, profile, search.distanceFromCenter());
    }

    private static void grantRewardIfDue(ServerPlayer player, ConfigData.RtpProfile profile) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        RtpData data = RtpData.get(server);
        long nowMs = System.currentTimeMillis();
        if (!data.isRewardDue(player.getUUID(), profile.dimension, profile.rewardCooldownSeconds, nowMs)) {
            return;
        }

        new RtpRewards(profile).applyTo(player);
        data.markRewarded(player.getUUID(), profile.dimension, nowMs);
    }

    private static void announceArrival(ServerPlayer player, ConfigData.RtpProfile profile, int distance) {
        if (profile.message == null || profile.message.isBlank()) return;
        player.sendSystemMessage(
                TextUtil.format(profile.message.replace("{distance}", String.valueOf(distance))));
    }

    private static void showCountdown(ServerPlayer player, RtpSearch search) {
        long secondsLeft = search.secondsLeft(player);
        String text = secondsLeft > 0
                ? "&eTeleporting in &f" + secondsLeft + "s&e... Don't move!"
                : "&eLooking for a safe spot...";
        player.sendSystemMessage(TextUtil.format(text), true);
    }

    private void cancel(ServerPlayer player, String reason) {
        this.searches.remove(player.getUUID());
        player.sendSystemMessage(TextUtil.format("&cRandom teleport cancelled: " + reason), true);
    }
}
