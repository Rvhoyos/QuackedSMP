package mc.smpessentials.votifier;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.tier.TierService;
import mc.smpessentials.util.TextUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Random;

// Processes validated votes: dispatches rewards and manages the offline queue.
public final class VoteHandler {

    private static volatile MinecraftServer server;
    private static final Random RNG = new Random();

    private VoteHandler() {}

    // Stores the server reference so the listener thread can dispatch to the server thread.
    public static void init(MinecraftServer srv) {
        server = srv;
    }

    public static void onVote(VoteData vote) {
        MinecraftServer srv = server; // capture volatile once, safe for cross-thread handoff
        if (srv == null) return;
        srv.execute(() -> {
            ServerPlayer player = srv.getPlayerList().getPlayerByName(vote.username());
            if (player != null) {
                dispatchReward(srv, player);
                broadcast(srv, vote);
            } else {
                VoteQueueData.get(srv).queue(vote.username());
                SmpUtilsMod.LOGGER.info("[Votifier] {} is offline, vote queued", vote.username());
            }
        });
    }

    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer srv = server; // capture volatile once
        if (srv == null) return;
        int pending = VoteQueueData.get(srv).take(player.getGameProfile().name());
        for (int i = 0; i < pending; i++) {
            dispatchReward(srv, player);
        }
        if (pending > 0) {
            player.sendSystemMessage(TextUtil.format(
                    "&aYou had &e" + pending + " &apending vote reward" + (pending > 1 ? "s" : "") + "!"));
        }
    }

    // Dispatches the base reward plus stacked VIP bonus rewards for each tier <= player's tier.
    private static void dispatchReward(MinecraftServer srv, ServerPlayer player) {
        String username = player.getGameProfile().name();

        // Base reward (all players)
        List<String> rewards = List.copyOf(SmpConfig.VOTE_REWARDS);
        if (!rewards.isEmpty()) {
            String cmd = rewards.get(RNG.nextInt(rewards.size())).replace("{player}", username);
            srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack(), cmd);
        }

        // Stacked VIP bonus rewards, isolated so a tier lookup failure can't block base rewards
        try {
            Map<Integer, List<String>> vipRewards = SmpConfig.VOTE_VIP_REWARDS;
            if (vipRewards.isEmpty()) return;
            int tier = TierService.getTier(player.getUUID(), srv);
            if (tier <= 0) return;
            for (int t = 1; t <= tier; t++) {
                List<String> tierRewards = vipRewards.get(t);
                if (tierRewards != null && !tierRewards.isEmpty()) {
                    String cmd = tierRewards.get(RNG.nextInt(tierRewards.size())).replace("{player}", username);
                    srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack(), cmd);
                }
            }
        } catch (Exception e) {
            SmpUtilsMod.LOGGER.warn("[Votifier] VIP reward dispatch failed for {}: {}", username, e.getMessage());
        }
    }

    private static void broadcast(MinecraftServer srv, VoteData vote) {
        String msg = SmpConfig.VOTE_BROADCAST
                .replace("{player}", vote.username())
                .replace("{service}", vote.serviceName());
        srv.getPlayerList().broadcastSystemMessage(TextUtil.format(msg), false);
    }
}
