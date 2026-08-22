package mc.smpessentials.claims;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.MutableComponent;
import mc.smpessentials.util.TextUtil;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Registers /claim, /unclaim and related subcommands. Business logic lives in ClaimService.
public final class ClaimCommands {
    private ClaimCommands() {
    }

    /** Registers all claim-related commands with the given dispatcher. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /claim, build as a variable so trust subcommands can be appended
        var claim = Commands.literal("claim")
                .requires(src -> mc.smpessentials.config.SmpConfig.CLAIMS_ENABLED && src.getEntity() instanceof ServerPlayer)
                .executes(ctx -> {
                    if (mc.smpessentials.hardcore.HardcoreSavedData.denyIfInSession(ctx.getSource())) return 0;
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ServerLevel lvl = p.level();

                    int brush = ClaimBrush.get(p.getUUID());
                    List<ChunkPos> chunks = ClaimBrush.spiral(p.chunkPosition(), brush);

                    int claimed = 0;
                    int skipped = 0;
                    boolean hitCap = false;

                    for (ChunkPos cp : chunks) {
                        ClaimService.Result result = ClaimService.claim(p, lvl, cp);
                        switch (result) {
                            case SUCCESS -> claimed++;
                            case REACHED_CAP -> { hitCap = true; }
                            default -> skipped++;
                        }
                        if (hitCap) break;
                    }

                    if (brush == 1) {
                        // Single chunk: keep the original concise messages
                        if (claimed == 1) {
                            ctx.getSource().sendSystemMessage(Component.literal("Chunk claimed."));
                        } else if (hitCap) {
                            int max = mc.smpessentials.config.SmpConfig.MAX_CLAIMS
                                    + mc.smpessentials.tier.TierService.getBonusClaims(
                                            mc.smpessentials.tier.TierService.getTier(p.getUUID(), lvl.getServer()));
                            ctx.getSource().sendSystemMessage(
                                    Component.literal("You reached the claim limit (" + max + ")."));
                        } else {
                            // skipped (already claimed or spawn protected)
                            ctx.getSource().sendSystemMessage(
                                    Component.literal("This claim is already protected."));
                        }
                    } else {
                        // Multi-chunk: summary message
                        StringBuilder msg = new StringBuilder();
                        msg.append("Claimed ").append(claimed).append(" chunk(s)");
                        if (skipped > 0) msg.append(", skipped ").append(skipped).append(" (already claimed/protected)");
                        if (hitCap) msg.append(". Hit claim limit");
                        msg.append(".");
                        ctx.getSource().sendSystemMessage(Component.literal(msg.toString()));
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
                                    ChunkPos current = new ChunkPos(center.x() + dx, center.z() + dz);
                                    Optional<UUID> owner = ClaimService.getOwner(lvl, current);

                                    String symbol = "\u2588"; // Full Block
                                    net.minecraft.ChatFormatting color = net.minecraft.ChatFormatting.GRAY;
                                    String tooltip = "Wilderness (" + current.x() + ", " + current.z() + ")";

                                    if (dx == 0 && dz == 0) {
                                        symbol = "+"; // Player
                                        color = net.minecraft.ChatFormatting.WHITE;
                                        tooltip = "You are here (" + current.x() + ", " + current.z() + ")";
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
                                            tooltip = "Your Claim (" + current.x() + ", " + current.z() + ")";
                                        } else {
                                            color = net.minecraft.ChatFormatting.RED;
                                            tooltip = "Enemy Claim (" + current.x() + ", " + current.z() + ")";
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
                                    // ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim " + current.x() + " " +
                                    // current.z())));

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

                                    boolean isTierPlayer = mc.smpessentials.tier.TierService.getTier(p.getUUID(),
                                            ((ServerLevel) p.level()).getServer()) >= 1;
                                    boolean isOp = ((ServerLevel) p.level()).getServer().getPlayerList()
                                            .isOp(p.nameAndId());

                                    if (!isTierPlayer && !isOp) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("Only VIPs and OPs can name their claims."));
                                        return 0;
                                    }

                                    // A [word] prefix that is not a real tag is almost always a typo for one.
                                    // Rejecting beats storing a name whose brackets silently mean nothing.
                                    var unknown = RegionDecoration.unknownTag(name);
                                    if (unknown.isPresent()) {
                                        String suggestion = RegionTags.closest(unknown.get())
                                                .map(t -> " Did you mean \u00a7f[" + t + "]\u00a7c?")
                                                .orElse("");
                                        ctx.getSource().sendFailure(Component.literal(
                                                "\u00a7cUnknown region tag \u00a7f[" + unknown.get() + "]\u00a7c."
                                                        + suggestion + " Run \u00a7f/claim tags\u00a7c to see them all."));
                                        return 0;
                                    }

                                    ServerLevel lvl = p.level();
                                    String plain = RegionDecoration.strip(name);

                                    switch (ClaimService.nameClaim(p, lvl, name)) {
                                        case OK -> ctx.getSource().sendSystemMessage(
                                                Component.literal("Region named to: ")
                                                        .append(TextUtil.format(RegionNames.display(name)))
                                                        .append(Component.literal(
                                                                ". Use /visit " + plain + " to travel here.")));
                                        case NAME_TAKEN -> ctx.getSource().sendFailure(Component.literal(
                                                "That name is already taken. Pick another."));
                                        case EMPTY -> ctx.getSource().sendFailure(
                                                Component.literal("Region name cannot be blank."));
                                        case NOT_OWNER -> ctx.getSource().sendFailure(
                                                Component.literal("You don't own this claim."));
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("tags")
                        .executes(ctx -> {
                            MutableComponent msg = Component.literal("== Region Tags ==")
                                    .withStyle(net.minecraft.ChatFormatting.GOLD);
                            msg.append(Component.literal(
                                    "\nPut one in front of a name to give the region a map icon,"
                                            + "\ne.g. /claim name \"[shop] Emporium\"."
                                            + "\nThe tag is not part of the name: /visit Emporium still works.")
                                    .withStyle(net.minecraft.ChatFormatting.GRAY));
                            msg.append(Component.literal("\n" + String.join(", ", RegionTags.all()))
                                    .withStyle(net.minecraft.ChatFormatting.WHITE));
                            msg.append(Component.literal(
                                    "\nColour codes work too, e.g. &6Gold Fortress.")
                                    .withStyle(net.minecraft.ChatFormatting.GRAY));
                            ctx.getSource().sendSystemMessage(msg);
                            return 1;
                        }))
                .then(Commands.literal("info")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ServerLevel lvl = (ServerLevel) p.level();
                            ChunkPos pos = p.chunkPosition();

                            int owned = ClaimService.ownedCount(lvl, p.getUUID());
                            int effectiveTier = mc.smpessentials.tier.TierService.getTier(p.getUUID(), lvl.getServer());
                            int bonus = mc.smpessentials.tier.TierService.getBonusClaims(effectiveTier);
                            int limit = mc.smpessentials.config.SmpConfig.MAX_CLAIMS + bonus;
                            boolean isOp = lvl.getServer().getPlayerList().isOp(p.nameAndId());

                            MutableComponent msg = Component.literal("== Claim Info ==")
                                    .withStyle(net.minecraft.ChatFormatting.GOLD);
                            msg.append(Component.literal("\nOwned: " + owned)
                                    .withStyle(net.minecraft.ChatFormatting.WHITE));

                            if (isOp) {
                                msg.append(Component.literal("\nLimit: Unlimited (OP)")
                                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
                            } else {
                                String tierLabel = effectiveTier > 0
                                        ? " (+" + mc.smpessentials.tier.TierService.getTierName(effectiveTier) + ")"
                                        : "";
                                msg.append(Component.literal("\nLimit: " + limit + tierLabel)
                                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
                                msg.append(Component.literal("\nRemaining: " + Math.max(0, limit - owned))
                                        .withStyle(net.minecraft.ChatFormatting.GREEN));
                            }

                            Optional<UUID> chunkOwner = ClaimService.getOwner(lvl, pos);
                            if (chunkOwner.isPresent()) {
                                boolean you = chunkOwner.get().equals(p.getUUID());
                                msg.append(Component.literal(
                                        "\nThis chunk: " + (you ? "Protected by you" : "Protected by another player"))
                                        .withStyle(you ? net.minecraft.ChatFormatting.GREEN
                                                : net.minecraft.ChatFormatting.RED));
                            } else {
                                msg.append(Component.literal("\nThis chunk: Unclaimed")
                                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                            }

                            ctx.getSource().sendSystemMessage(msg);
                            return 1;
                        }));

        claim.then(Commands.literal("size")
                .then(Commands.argument("value", IntegerArgumentType.integer(1, 7))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            int raw = IntegerArgumentType.getInteger(ctx, "value");
                            ClaimBrush.set(p.getUUID(), raw);
                            int actual = ClaimBrush.get(p.getUUID());
                            String msg = "Claim brush set to " + actual + "x" + actual;
                            if (raw != actual) msg += " (rounded down to odd)";
                            msg += ".";
                            ctx.getSource().sendSystemMessage(Component.literal(msg));
                            return 1;
                        })));

        // /claim trust|untrust|trustlist, aliases for discoverability; root /trust etc. still work
        claim.then(TrustCommands.trustNode());
        claim.then(TrustCommands.untrustNode());
        claim.then(TrustCommands.trustlistNode());

        dispatcher.register(claim);

        // /unclaim
        dispatcher.register(
                Commands.literal("unclaim")
                        .requires(src -> mc.smpessentials.config.SmpConfig.CLAIMS_ENABLED && src.getEntity() instanceof ServerPlayer)
                        .executes(ctx -> {
                            if (mc.smpessentials.hardcore.HardcoreSavedData.denyIfInSession(ctx.getSource())) return 0;
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ServerLevel lvl = p.level();
                            ChunkPos pos = p.chunkPosition();

                            boolean ok = ClaimService.unclaim(p, lvl, pos);
                            if (ok) {
                                ctx.getSource().sendSystemMessage(Component.literal("Chunk unclaimed."));
                            } else {
                                ctx.getSource().sendFailure(Component.literal("You don't control this claim."));
                            }
                            return 1;
                        }));
    }
}
