package mc.smpessentials.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import mc.smpessentials.claims.ClaimService;
import mc.smpessentials.claims.RegionNames;
import mc.smpessentials.config.SmpConfig;

import java.util.Locale;
import java.util.Set;

// /visit <name> travels to a named region the player owns or is trusted in, after the standard
// teleport warmup. Destination is the exact spot the owner named the region from.
public final class VisitCommand {
    private VisitCommand() {
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST = (ctx, builder) -> {
        if (ctx.getSource().getEntity() instanceof ServerPlayer p) {
            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (String name : ClaimService.visitableNames(p)) {
                String token = name.contains(" ") ? "\"" + name + "\"" : name;
                if (token.toLowerCase(Locale.ROOT).startsWith(remaining))
                    builder.suggest(token);
            }
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("visit")
                .requires(src -> SmpConfig.CLAIMS_ENABLED && src.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(SUGGEST)
                        .executes(ctx -> execute(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));
    }

    private static int execute(CommandSourceStack src, String name) throws CommandSyntaxException {
        if (mc.smpessentials.hardcore.HardcoreSavedData.denyIfInSession(src))
            return 0;
        ServerPlayer p = src.getPlayerOrException();

        RegionNames.VisitResult r = ClaimService.resolveVisit(p, name);
        switch (r.status()) {
            case UNKNOWN -> src.sendFailure(Component.literal("No region named \"" + name + "\"."));
            case NO_ACCESS -> src.sendFailure(Component.literal("You aren't trusted in that region."));
            case DIM_UNAVAILABLE -> src.sendFailure(Component.literal("That region's dimension isn't available."));
            case OK -> {
                mc.smpessentials.teleport.TeleportScheduler.schedule(p, () -> {
                    p.teleportTo(r.level(), r.x(), r.y(), r.z(), Set.of(), r.yaw(), r.pitch(), false);
                    p.sendSystemMessage(Component.literal("Traveled to " + r.name() + "."), false);
                });
                return 1;
            }
        }
        return 0;
    }
}
