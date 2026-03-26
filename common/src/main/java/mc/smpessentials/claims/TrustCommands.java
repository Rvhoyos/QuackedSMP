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

/**
 * Registers the trust management commands:
 * <ul>
 *   <li>{@code /trust <player>} — grant another online player access to all of your claims.</li>
 *   <li>{@code /untrust <player>} — revoke that access.</li>
 *   <li>{@code /trustlist} — list all players you currently trust (online names shown; offline
 *       players shown as a count).</li>
 * </ul>
 *
 * <p>Trust is owner-wide: it applies to <em>all</em> of an owner's claims, not individual
 * chunks.  Persistence is handled by {@link mc.smpessentials.claims.storage.WhitelistSavedData}.
 */
public final class TrustCommands {
    private TrustCommands() {
    }

    /** Registers all trust-related commands with the given dispatcher. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /trust <player>
        dispatcher.register(
                Commands.literal("trust")
                        .requires(src -> mc.smpessentials.config.SmpConfig.CLAIMS_ENABLED && src.getEntity() instanceof ServerPlayer)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer self = ctx.getSource().getPlayerOrException();
                                    ServerLevel level = (ServerLevel) self.level();
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

                                    boolean added = WhitelistSavedData.get(level).add(self.getUUID(), target.getUUID());
                                    if (added) {
                                        ctx.getSource().sendSystemMessage(Component.literal(
                                                "Trusted " + target.getName().getString() + " for all your claims."));
                                    } else {
                                        ctx.getSource().sendFailure(Component
                                                .literal(target.getName().getString() + " is already trusted."));
                                    }
                                    return 1;
                                })));

        // /untrust <player>
        dispatcher.register(
                Commands.literal("untrust")
                        .requires(src -> mc.smpessentials.config.SmpConfig.CLAIMS_ENABLED && src.getEntity() instanceof ServerPlayer)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer self = ctx.getSource().getPlayerOrException();
                                    ServerLevel level = (ServerLevel) self.level();
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

                                    boolean removed = WhitelistSavedData.get(level).remove(self.getUUID(),
                                            target.getUUID());
                                    if (removed) {
                                        ctx.getSource().sendSystemMessage(Component
                                                .literal("Removed trust for " + target.getName().getString() + "."));
                                    } else {
                                        ctx.getSource().sendFailure(
                                                Component.literal(target.getName().getString() + " wasn’t trusted."));
                                    }
                                    return 1;
                                })));

        // /trustlist
        dispatcher.register(
                Commands.literal("trustlist")
                        .requires(src -> mc.smpessentials.config.SmpConfig.CLAIMS_ENABLED && src.getEntity() instanceof ServerPlayer)
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
                                    .map(p -> p.getName().getString())
                                    .collect(Collectors.joining(", "));

                            if (names.isEmpty()) {
                                ctx.getSource().sendSystemMessage(
                                        Component.literal("Trusted: " + trusted.size() + " player(s)."));
                            } else {
                                ctx.getSource().sendSystemMessage(Component.literal("Trusted: " + names));
                            }
                            return 1;
                        }));
    }
}
