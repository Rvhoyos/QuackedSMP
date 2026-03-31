package mc.smpessentials.tier;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.util.UUID;

/** Resolves a player's tier from a single persisted field, kept current by login bumps. */
public final class TierService {

    private TierService() {}

    /**
     * The player's effective tier. This is the single source of truth.
     * If the stored value references a tier definition that no longer exists,
     * it is clamped to the highest valid tier below it.
     */
    public static int getTier(UUID uuid, MinecraftServer server) {
        int stored = PlayerTierData.get(server).getTier(uuid);
        return clampToValidTier(stored);
    }

    /**
     * Called on player login. Promotes any pending migration entry to UUID-keyed storage,
     * then bumps the stored tier up if the player has earned a higher tier via playtime.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        PlayerTierData data = PlayerTierData.get(server);

        data.promotePendingMigration(player);

        int earned = getEarnedTier(player);
        int stored = data.getTier(player.getUUID());
        if (earned > stored) {
            data.set(player.getUUID(), earned);
            SmpUtilsMod.LOGGER.info("[QuackedSMP] {} earned tier {} (was {})",
                    player.getName().getString(), earned, stored);
        }
    }

    /** Returns the highest tier the player has earned through playtime. */
    public static int getEarnedTier(ServerPlayer player) {
        int ticks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        long hours = ticks / 72000L;
        int earned = 0;
        for (SmpConfig.TierDef def : SmpConfig.TIERS) {
            if (hours >= def.minPlaytimeHours() && def.tier() > earned) {
                earned = def.tier();
            }
        }
        return earned;
    }

    /** Returns bonus claims granted by a given tier level. 0 if tier is 0 or not found. */
    public static int getBonusClaims(int tier) {
        if (tier <= 0) return 0;
        for (SmpConfig.TierDef def : SmpConfig.TIERS) {
            if (def.tier() == tier) return def.bonusClaims();
        }
        return 0;
    }

    /** Returns the display name for a tier level, or "None" if tier is 0. */
    public static String getTierName(int tier) {
        if (tier <= 0) return "None";
        for (SmpConfig.TierDef def : SmpConfig.TIERS) {
            if (def.tier() == tier) return def.name();
        }
        return "Tier " + tier;
    }

    // If stored tier no longer exists in definitions, clamp to highest valid tier below it.
    private static int clampToValidTier(int tier) {
        if (tier <= 0) return 0;
        for (SmpConfig.TierDef def : SmpConfig.TIERS) {
            if (def.tier() == tier) return tier;
        }
        int best = 0;
        for (SmpConfig.TierDef def : SmpConfig.TIERS) {
            if (def.tier() < tier && def.tier() > best) best = def.tier();
        }
        return best;
    }
}
