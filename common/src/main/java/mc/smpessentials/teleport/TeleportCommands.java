package mc.smpessentials.teleport;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * /tpr <player> and /tpa accept|deny (FIFO).
 */
public final class TeleportCommands {
    private TeleportCommands() {}

    @SuppressWarnings("unchecked")
    public static void register(CommandDispatcher<?> rawDispatcher) {
        CommandDispatcher<CommandSourceStack> dispatcher = (CommandDispatcher<CommandSourceStack>) rawDispatcher;

        dispatcher.register(Commands.literal("tpr")
            .requires(src -> src.getEntity() instanceof ServerPlayer)
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer requester = src.getPlayerOrException();
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

                    long now = System.currentTimeMillis();
                    TeleportService.Result r = TeleportService.sendRequest(requester, target, now);
                    switch (r) {
                        case OK -> {
                            requester.displayClientMessage(Component.literal("Teleport request sent to " + target.getGameProfile().getName()), false);
                            target.displayClientMessage(Component.literal(requester.getGameProfile().getName() + " requested to teleport to you. Use /tpa accept or /tpa deny."), false);
                            return 1;
                        }
                        case SELF_TARGET -> src.sendFailure(Component.literal("Cannot request teleport to self."));
                        case IN_COOLDOWN -> src.sendFailure(Component.literal("A request was sent recently. Please wait."));
                        case ALREADY_PENDING -> src.sendFailure(Component.literal("A request is already pending."));
                        case TARGET_QUEUE_FULL -> src.sendFailure(Component.literal("That player has too many pending requests."));
                    }
                    return 0;
                })
            )
        );

        dispatcher.register(Commands.literal("tpa")
            .requires(src -> src.getEntity() instanceof ServerPlayer)
            .then(Commands.literal("accept")
                .executes(ctx -> acceptOldest(ctx.getSource()))
            )
            .then(Commands.literal("yes")
                .executes(ctx -> acceptOldest(ctx.getSource()))
            )
            .then(Commands.literal("deny")
                .executes(ctx -> denyOldest(ctx.getSource()))
            )
            .then(Commands.literal("no")
                .executes(ctx -> denyOldest(ctx.getSource()))
            )
        );
    }

    private static int acceptOldest(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer target = src.getPlayerOrException();
        long now = System.currentTimeMillis();
        Optional<UUID> rq = TeleportService.acceptOldest(target, now);
        if (rq.isEmpty()) {
            src.sendFailure(Component.literal("No pending teleport requests."));
            return 0;
        }
        ServerPlayer requester = target.getServer().getPlayerList().getPlayer(rq.get());
        if (requester == null) {
            src.sendFailure(Component.literal("Requester is no longer online."));
            return 0;
        }
        if (requester.isPassenger() || requester.isSleeping()) {
            src.sendFailure(Component.literal("Requester cannot be teleported right now."));
            return 0;
        }
        boolean ok = SafeTeleport.teleportToPlayer(requester, target);
        if (!ok) {
            src.sendFailure(Component.literal("No valid teleport location."));
            return 0;
        }
        requester.displayClientMessage(Component.literal("Teleported to " + target.getGameProfile().getName()), false);
        target.displayClientMessage(Component.literal(requester.getGameProfile().getName() + " has been teleported."), false);
        return 1;
    }

    private static int denyOldest(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer target = src.getPlayerOrException();
        long now = System.currentTimeMillis();
        Optional<UUID> rq = TeleportService.denyOldest(target, now);
        if (rq.isEmpty()) {
            src.sendFailure(Component.literal("No pending teleport requests."));
            return 0;
        }
        ServerPlayer requester = target.getServer().getPlayerList().getPlayer(rq.get());
        if (requester != null) {
            requester.displayClientMessage(Component.literal("Teleport request denied by " + target.getGameProfile().getName()), false);
        }
        target.displayClientMessage(Component.literal("Denied the oldest pending request."), false);
        return 1;
    }
}
