package mc.smpessentials.keepinv;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class KeepInvCommand {

    private KeepInvCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(keepInvSubtree());
    }

    /** Returns the keepinv subtree for use as a standalone command or as a subcommand (e.g. /smp keepinv). */
    public static LiteralArgumentBuilder<CommandSourceStack> keepInvSubtree() {
        return Commands.literal("keepinv")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .executes(ctx -> showStatus(ctx.getSource()))
                .then(Commands.literal("on")
                        .executes(ctx -> setKeepInv(ctx.getSource(), true)))

                .then(Commands.literal("off")
                        .executes(ctx -> setKeepInv(ctx.getSource(), false)));
    }

    private static int showStatus(CommandSourceStack src) {
        ServerPlayer player = (ServerPlayer) src.getEntity();
        boolean keeping = KeepInvSavedData.get((net.minecraft.server.level.ServerLevel) player.level()).isKeeping(player.getUUID());
        String status = keeping
                ? "\u00a7aON \u00a77(you keep items on death)"
                : "\u00a7cOFF \u00a77(you drop items on death — vanilla experience)";
        player.sendSystemMessage(Component.literal("\u00a7eKeep Inventory: " + status));
        player.sendSystemMessage(Component.literal("\u00a77Use \u00a7f/smp keepinv on\u00a77 or \u00a7f/smp keepinv off\u00a77 to change."));
        return 1;
    }

    private static int setKeepInv(CommandSourceStack src, boolean keep) {
        if (mc.smpessentials.hardcore.HardcoreSavedData.denyIfInSession(src)) return 0;
        ServerPlayer player = (ServerPlayer) src.getEntity();
        boolean changed = KeepInvSavedData.get((net.minecraft.server.level.ServerLevel) player.level()).setKeeping(player.getUUID(), keep);

        if (!changed) {
            player.sendSystemMessage(Component.literal(
                    "\u00a77Keep Inventory is already " + (keep ? "\u00a7aON" : "\u00a7cOFF") + "\u00a77."));
        } else if (keep) {
            player.sendSystemMessage(Component.literal(
                    "\u00a7aKeep Inventory enabled. \u00a77You will keep your items on death."));
        } else {
            player.sendSystemMessage(Component.literal(
                    "\u00a7cKeep Inventory disabled. \u00a77You will drop all items on death (XP kept)."));
        }
        return 1;
    }
}
