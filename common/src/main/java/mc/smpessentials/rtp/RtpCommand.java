package mc.smpessentials.rtp;

import com.mojang.brigadier.CommandDispatcher;
import mc.smpessentials.hardcore.HardcoreSavedData;
import mc.smpessentials.util.TextUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /rtp, the player-facing entry point. Parses the command and turns a {@link RtpService.Result}
 * into text; all the behaviour lives behind the service.
 */
public final class RtpCommand {
    private RtpCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rtp")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .executes(ctx -> execute(ctx.getSource())));
    }

    private static int execute(CommandSourceStack source) {
        // Blocked during a hardcore session, matching /home and /spawn.
        if (HardcoreSavedData.denyIfInSession(source)) return 0;
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use /rtp."));
            return 0;
        }

        RtpService service = RtpService.get();
        return switch (service.request(player)) {
            case OK -> 1;
            case DISABLED -> refuse(player, "Random teleport is disabled on this server.");
            case NO_PROFILE -> refuse(player, "Random teleport is not available in this dimension.");
            case ALREADY_RUNNING -> refuse(player, "You already have a random teleport running.");
            case ON_COOLDOWN -> refuse(player, "Random teleport is on cooldown for another "
                    + service.remainingCooldownSeconds(player.getUUID()) + "s.");
        };
    }

    private static int refuse(ServerPlayer player, String reason) {
        player.sendSystemMessage(TextUtil.format("&c" + reason));
        return 0;
    }
}
