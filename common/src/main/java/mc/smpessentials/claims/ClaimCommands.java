// common/src/main/java/mc/smpessentials/claims/ClaimCommands.java
package mc.smpessentials.claims;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.MutableComponent;
import java.util.Optional;
import java.util.UUID;

/**
 * Registers the player-facing claim commands:
 * <ul>
 *   <li>{@code /claim} — claim the current chunk.</li>
 *   <li>{@code /claim map} — render a 7×7 ASCII mini-map of nearby claims.</li>
 *   <li>{@code /claim name <name>} — set a display name for the current claim (VIP/OP only).</li>
 *   <li>{@code /claim transfer <player>} — transfer all of your claims to another player.</li>
 *   <li>{@code /claim info} — show owned count vs. limit.</li>
 *   <li>{@code /unclaim} — unclaim the current chunk.</li>
 *   <li>{@code /claims} — show total owned chunks and current-chunk owner.</li>
 * </ul>
 *
 * <p>All commands require a player source; console use is rejected by the {@code .requires}
 * predicate.  Business logic is delegated to {@link ClaimService}.
 */
public final class ClaimCommands {
    private ClaimCommands() {
    }

    /** Registers all claim-related commands with the given dispatcher. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /claim
        dispatcher.register(
                Commands.literal("claim")
                        .requires(src -> mc.smpessentials.config.SmpConfig.CLAIMS_ENABLED && src.getEntity() instanceof ServerPlayer)
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException(); // may throw, Brigadier allows it
                            ServerLevel lvl = p.level();
                            ChunkPos pos = p.chunkPosition();

                            ClaimService.Result result = ClaimService.claim(p, lvl, pos);
                            switch (result) {
                                case SUCCESS -> ctx.getSource().sendSystemMessage(Component.literal("Chunk claimed."));
                                case ALREADY_CLAIMED -> ctx.getSource()
                                        .sendSystemMessage(Component.literal("This claim is already protected."));
                                case REACHED_CAP -> {
                                    int max = mc.smpessentials.config.SmpConfig.MAX_CLAIMS;
                                    if (mc.smpessentials.config.SmpConfig.VIPS
                                            .contains(p.getName().getString())) {
                                        max += mc.smpessentials.config.SmpConfig.VIP_BONUS_CLAIMS;
                                    }
                                    ctx.getSource().sendSystemMessage(Component
                                            .literal("You reached the claim limit (" + max + ")."));
                                }
                                case SPAWN_PROTECTED -> ctx.getSource().sendSystemMessage(
                                        Component.literal("You can’t claim inside spawn protection."));
                            }
                            return 1;
                        })
                        .then(Commands.literal("map")
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    ServerLevel lvl = p.level();
                                    ChunkPos center = p.chunkPosition();
                                    int radius = 3; // 7x7 grid

                                    MutableComponent header = Component.literal("  Claim Map (Radius " + radius + ")  ")
                                            .withStyle(net.minecraft.ChatFormatting.GOLD,
                                                    net.minecraft.ChatFormatting.BOLD);
                                    ctx.getSource().sendSystemMessage(header);

                                    // North is negative Z
                                    ctx.getSource().sendSystemMessage(Component.literal("      N (Z-)")
                                            .withStyle(net.minecraft.ChatFormatting.GRAY));

                                    for (int dz = -radius; dz <= radius; dz++) {
                                        MutableComponent line = Component.literal("");
                                        for (int dx = -radius; dx <= radius; dx++) {
                                            ChunkPos current = new ChunkPos(center.x + dx, center.z + dz);
                                            Optional<UUID> owner = ClaimService.getOwner(lvl, current);

                                            String symbol = "\u2588"; // Full Block
                                            net.minecraft.ChatFormatting color = net.minecraft.ChatFormatting.GRAY;
                                            String tooltip = "Wilderness (" + current.x + ", " + current.z + ")";

                                            if (dx == 0 && dz == 0) {
                                                symbol = "+"; // Player
                                                color = net.minecraft.ChatFormatting.WHITE;
                                                tooltip = "You are here (" + current.x + ", " + current.z + ")";
                                                if (owner.isPresent()) {
                                                    if (owner.get().equals(p.getUUID())) {
                                                        color = net.minecraft.ChatFormatting.GREEN;
                                                        tooltip += "\nOwned by You";
                                                    } else {
                                                        color = net.minecraft.ChatFormatting.RED;
                                                        tooltip += "\nOwned by Enemy";
                                                    }
                                                }
                                            } else if (owner.isPresent()) {
                                                if (owner.get().equals(p.getUUID())) {
                                                    color = net.minecraft.ChatFormatting.GREEN;
                                                    tooltip = "Your Claim (" + current.x + ", " + current.z + ")";
                                                } else {
                                                    color = net.minecraft.ChatFormatting.RED;
                                                    tooltip = "Enemy Claim (" + current.x + ", " + current.z + ")";
                                                    // Resolve name async if possible? For now, simplistic.
                                                }
                                            } else {
                                                symbol = "-"; // Wilderness
                                            }

                                            MutableComponent cell = Component.literal(symbol).withStyle(color);

                                            String finalTooltip = tooltip;
                                            // Add Hover
                                            cell.withStyle(style -> style
                                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                                                            Component.literal(finalTooltip))));

                                            // Add Click to claim? (Maybe dangerous, but fancy)
                                            // cell.withStyle(style -> style.withClickEvent(new
                                            // ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim " + current.x + " " +
                                            // current.z)));

                                            line.append(cell).append(Component.literal(" "));
                                        }
                                        ctx.getSource().sendSystemMessage(line);
                                    }
                                    ctx.getSource().sendSystemMessage(Component.literal("      S (Z+)")
                                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                                    return 1;
                                }))
                        .then(Commands.literal("name")
                                .then(Commands
                                        .argument("claimName",
                                                com.mojang.brigadier.arguments.StringArgumentType.string())
                                        .executes(ctx -> {
                                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                                            String name = com.mojang.brigadier.arguments.StringArgumentType
                                                    .getString(ctx, "claimName");

                                            boolean isVip = mc.smpessentials.config.SmpConfig.VIPS
                                                    .contains(p.getName().getString());
                                            boolean isOp = ((ServerLevel) p.level()).getServer().getPlayerList()
                                                    .isOp(p.nameAndId());

                                            if (!isVip && !isOp) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("Only VIPs and OPs can name their claims."));
                                                return 0;
                                            }

                                            ChunkPos pos = p.chunkPosition();
                                            ServerLevel lvl = p.level();

                                            boolean success = ClaimService.setName(p, lvl, pos, name);
                                            if (success) {
                                                ctx.getSource().sendSystemMessage(
                                                        Component.literal("Claim named to: " + name));
                                            } else {
                                                ctx.getSource()
                                                        .sendFailure(Component.literal("You don't own this claim."));
                                            }
                                            return 1;
                                        })))
                        .then(Commands.literal("transfer")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer sender = ctx.getSource().getPlayerOrException();
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            int result = ClaimService.transfer(sender, target);
                                            switch (result) {
                                                case -1 -> ctx.getSource().sendFailure(
                                                        Component.literal("You cannot transfer claims to yourself."));
                                                case -2 -> ctx.getSource().sendFailure(
                                                        Component.literal("You have no claims to transfer."));
                                                case -3 -> ctx.getSource().sendFailure(
                                                        Component.literal("That would exceed " + target.getName().getString() + "'s claim limit."));
                                                default -> ctx.getSource().sendSystemMessage(Component.literal(
                                                        "Transferred " + result + " claim(s) to " + target.getName().getString() + "."));
                                            }
                                            return 1;
                                        })))
                        .then(Commands.literal("info")
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    int owned = ClaimService.ownedCount(p.level(), p.getUUID());
                                    int limit = mc.smpessentials.config.SmpConfig.MAX_CLAIMS;
                                    boolean isVip = mc.smpessentials.config.SmpConfig.VIPS
                                            .contains(p.getName().getString());
                                    if (isVip) {
                                        limit += mc.smpessentials.config.SmpConfig.VIP_BONUS_CLAIMS;
                                    }
                                    boolean isOp = ((ServerLevel) p.level()).getServer().getPlayerList()
                                            .isOp(p.nameAndId());

                                    MutableComponent msg = Component.literal("== Claim Info ==")
                                            .withStyle(net.minecraft.ChatFormatting.GOLD);
                                    msg.append(Component.literal("\nOwned: " + owned)
                                            .withStyle(net.minecraft.ChatFormatting.WHITE));

                                    if (isOp) {
                                        msg.append(Component.literal("\nLimit: Unlimited (OP)")
                                                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
                                    } else {
                                        String limitStr = String.valueOf(limit);
                                        if (isVip) {
                                            limitStr += " (+VIP)";
                                        }
                                        msg.append(Component.literal("\nLimit: " + limitStr)
                                                .withStyle(net.minecraft.ChatFormatting.YELLOW));
                                        msg.append(Component.literal("\nRemaining: " + Math.max(0, limit - owned))
                                                .withStyle(net.minecraft.ChatFormatting.GREEN));
                                    }

                                    ctx.getSource().sendSystemMessage(msg);
                                    return 1;
                                })));

        // /unclaim
        dispatcher.register(
                Commands.literal("unclaim")
                        .requires(src -> mc.smpessentials.config.SmpConfig.CLAIMS_ENABLED && src.getEntity() instanceof ServerPlayer)
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ServerLevel lvl = p.level();
                            ChunkPos pos = p.chunkPosition();

                            boolean ok = ClaimService.unclaim(p, lvl, pos);
                            if (ok) {
                                ctx.getSource().sendSystemMessage(Component.literal("Chunk unclaimed."));
                            } else {
                                ctx.getSource().sendFailure(Component.literal("You don’t control this claim."));
                            }
                            return 1;
                        }));

        // /claims (global count + current chunk owner)
        dispatcher.register(
                Commands.literal("claims")
                        .requires(src -> mc.smpessentials.config.SmpConfig.CLAIMS_ENABLED && src.getEntity() instanceof ServerPlayer)
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ServerLevel lvl = p.level();
                            ChunkPos pos = p.chunkPosition();

                            int mine = ClaimService.ownedCount(lvl, p.getUUID()); // <-- updated
                            ctx.getSource().sendSystemMessage(
                                    Component.literal("You own " + mine + " chunk(s) total."));

                            Optional<UUID> owner = ClaimService.getOwner(lvl, pos);
                            if (owner.isPresent()) {
                                boolean you = owner.get().equals(p.getUUID());
                                ctx.getSource().sendSystemMessage(Component.literal(
                                        you ? "This chunk is protected by you." : "This chunk is protected."));
                            } else {
                                ctx.getSource().sendSystemMessage(Component.literal("Current chunk is unclaimed."));
                            }
                            return 1;
                        }));
    }
}
