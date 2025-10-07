package mc.smpessentials.claims;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import mc.smpessentials.claims.storage.WhitelistSavedData;

public final class TrustCommands {
    private TrustCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /trust <player>
        dispatcher.register(
            Commands.literal("trust")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> {
                        ServerPlayer self = ctx.getSource().getPlayerOrException();
                        ServerLevel level = (ServerLevel) self.level();
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

                        boolean added = WhitelistSavedData.get(level).add(self.getUUID(), target.getUUID());
                        if (added) {
                            ctx.getSource().sendSystemMessage(Component.literal("Trusted " + target.getGameProfile().getName() + " for all your claims."));
                        } else {
                            ctx.getSource().sendFailure(Component.literal(target.getGameProfile().getName() + " is already trusted."));
                        }
                        return 1;
                    })
                )
        );

        // /untrust <player>
        dispatcher.register(
            Commands.literal("untrust")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> {
                        ServerPlayer self = ctx.getSource().getPlayerOrException();
                        ServerLevel level = (ServerLevel) self.level();
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

                        boolean removed = WhitelistSavedData.get(level).remove(self.getUUID(), target.getUUID());
                        if (removed) {
                            ctx.getSource().sendSystemMessage(Component.literal("Removed trust for " + target.getGameProfile().getName() + "."));
                        } else {
                            ctx.getSource().sendFailure(Component.literal(target.getGameProfile().getName() + " wasn’t trusted."));
                        }
                        return 1;
                    })
                )
        );

        // /trustlist
        dispatcher.register(
            Commands.literal("trustlist")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .executes(ctx -> {
                    ServerPlayer self = ctx.getSource().getPlayerOrException();
                    ServerLevel level = (ServerLevel) self.level();

                    Set<UUID> trusted = WhitelistSavedData.get(level).list(self.getUUID());
                    if (trusted.isEmpty()) {
                        ctx.getSource().sendSystemMessage(Component.literal("Your trust list is empty."));
                        return 1;
                    }

                    // Resolve online names; fall back to count if no one online
                    String names = trusted.stream()
                        .map(uuid -> level.getServer().getPlayerList().getPlayer(uuid))
                        .filter(Objects::nonNull)
                        .map(p -> p.getGameProfile().getName())
                        .collect(Collectors.joining(", "));

                    if (names.isEmpty()) {
                        ctx.getSource().sendSystemMessage(Component.literal("Trusted: " + trusted.size() + " player(s)."));
                    } else {
                        ctx.getSource().sendSystemMessage(Component.literal("Trusted: " + names));
                    }
                    return 1;
                })
        );
    }
}
