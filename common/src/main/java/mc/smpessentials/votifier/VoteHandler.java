package mc.smpessentials.votifier;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.util.TextUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Random;

/** Processes validated votes: dispatches rewards and manages the offline queue. */
public final class VoteHandler {

    private static volatile MinecraftServer server;
    private static final Random RNG = new Random();

    private VoteHandler() {}

    /**
     * Stores the running server so that votes submitted from the listener thread
     * can be dispatched onto the server tick thread via {@link MinecraftServer#execute}.
     * Must be called from the server-started event on both platforms.
     */
    public static void init(MinecraftServer srv) {
        server = srv;
    }

    /** Called from the Votifier listener thread; submits work to the server thread. */
    public static void onVote(VoteData vote) {
        MinecraftServer srv = server; // capture volatile once — safe for cross-thread handoff
        if (srv == null) return;
        srv.execute(() -> {
            ServerPlayer player = srv.getPlayerList().getPlayerByName(vote.username());
            if (player != null) {
                dispatchReward(srv, vote.username());
                broadcast(srv, vote);
            } else {
                VoteQueueData.get(srv).queue(vote.username());
                SmpUtilsMod.LOGGER.info("[Votifier] {} is offline, vote queued", vote.username());
            }
        });
    }

    /** Call on player join to flush any queued rewards they earned while offline. */
    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer srv = server; // capture volatile once
        if (srv == null) return;
        String name = player.getGameProfile().name();
        int pending = VoteQueueData.get(srv).take(name);
        for (int i = 0; i < pending; i++) {
            dispatchReward(srv, name);
        }
        if (pending > 0) {
            int n = pending;
            player.sendSystemMessage(TextUtil.format(
                    "&aYou had &e" + n + " &apending vote reward" + (n > 1 ? "s" : "") + "!"));
        }
    }

    private static void dispatchReward(MinecraftServer srv, String username) {
        List<String> rewards = List.copyOf(SmpConfig.VOTE_REWARDS); // snapshot before use
        if (rewards.isEmpty()) return;
        String cmd = rewards.get(RNG.nextInt(rewards.size())).replace("{player}", username);
        srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack(), cmd);
    }

    private static void broadcast(MinecraftServer srv, VoteData vote) {
        String msg = SmpConfig.VOTE_BROADCAST
                .replace("{player}", vote.username())
                .replace("{service}", vote.serviceName());
        srv.getPlayerList().broadcastSystemMessage(TextUtil.format(msg), false);
    }
}
