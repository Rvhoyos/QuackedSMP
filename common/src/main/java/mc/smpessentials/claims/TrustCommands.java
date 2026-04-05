package mc.smpessentials.claims;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import mc.smpessentials.config.SmpConfig;

// Registers /trust, /untrust, /trustlist at root. Also exposes nodes for /claim trust|untrust|trustlist aliases.
// Trust is owner-wide; it covers all of an owner's claims, not individual chunks.
public final class TrustCommands {
    private TrustCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(trustNode());
        dispatcher.register(untrustNode());
        dispatcher.register(trustlistNode());
    }

    static LiteralArgumentBuilder<CommandSourceStack> trustNode() {
        return Commands.literal("trust")
                .requires(src -> SmpConfig.CLAIMS_ENABLED && src.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            if (mc.smpessentials.hardcore.HardcoreSavedData.denyIfInSession(ctx.getSource())) return 0;
                            ServerPlayer self = ctx.getSource().getPlayerOrException();
                            ServerLevel level = (ServerLevel) self.level();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            boolean added = WhitelistSavedData.get(level).add(self.getUUID(), target.getUUID());
                            if (added) {
                                ctx.getSource().sendSystemMessage(Component.literal(
                                        "Trusted " + target.getName().getString() + " for all your claims."));
                            } else {
                                ctx.getSource().sendFailure(Component.literal(
                                        target.getName().getString() + " is already trusted."));
                            }
                            return 1;
                        }));
    }

    static LiteralArgumentBuilder<CommandSourceStack> untrustNode() {
        return Commands.literal("untrust")
                .requires(src -> SmpConfig.CLAIMS_ENABLED && src.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            if (mc.smpessentials.hardcore.HardcoreSavedData.denyIfInSession(ctx.getSource())) return 0;
                            ServerPlayer self = ctx.getSource().getPlayerOrException();
                            ServerLevel level = (ServerLevel) self.level();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            boolean removed = WhitelistSavedData.get(level).remove(self.getUUID(), target.getUUID());
                            if (removed) {
                                ctx.getSource().sendSystemMessage(Component.literal(
                                        "Removed trust for " + target.getName().getString() + "."));
                            } else {
                                ctx.getSource().sendFailure(Component.literal(
                                        target.getName().getString() + " wasn't trusted."));
                            }
                            return 1;
                        }));
    }

    static LiteralArgumentBuilder<CommandSourceStack> trustlistNode() {
        return Commands.literal("trustlist")
                .requires(src -> SmpConfig.CLAIMS_ENABLED && src.getEntity() instanceof ServerPlayer)
                .executes(ctx -> {
                    if (mc.smpessentials.hardcore.HardcoreSavedData.denyIfInSession(ctx.getSource())) return 0;
                    ServerPlayer self = ctx.getSource().getPlayerOrException();
                    ServerLevel level = (ServerLevel) self.level();
                    Set<UUID> trusted = WhitelistSavedData.get(level).list(self.getUUID());
                    if (trusted.isEmpty()) {
                        ctx.getSource().sendSystemMessage(Component.literal("Your trust list is empty."));
                        return 1;
                    }
                    String names = trusted.stream()
                            .map(uuid -> level.getServer().getPlayerList().getPlayer(uuid))
                            .filter(Objects::nonNull)
                            .map(p -> p.getName().getString())
                            .collect(Collectors.joining(", "));
                    if (names.isEmpty()) {
                        ctx.getSource().sendSystemMessage(
                                Component.literal("Trusted: " + trusted.size() + " player(s)."));
                    } else {
                        ctx.getSource().sendSystemMessage(Component.literal("Trusted: " + names));
                    }
                    return 1;
                });
    }
}
