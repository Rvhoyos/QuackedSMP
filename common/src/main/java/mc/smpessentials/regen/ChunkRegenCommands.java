package mc.smpessentials.regen;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

// /smp regen command tree: warn, confirm, cancel, status.
public final class ChunkRegenCommands {

    private ChunkRegenCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> regenSubtree() {
        return Commands.literal("regen")
                .requires(s -> Commands.LEVEL_GAMEMASTERS.check(s.permissions()))
                .executes(ChunkRegenCommands::showWarning)
                .then(Commands.literal("confirm")
                        .executes(ChunkRegenCommands::confirmRegen))
                .then(Commands.literal("cancel")
                        .executes(ChunkRegenCommands::cancelRegen))
                .then(Commands.literal("status")
                        .executes(ChunkRegenCommands::showStatus));
    }

    private static int showWarning(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        src.sendSystemMessage(Component.empty());
        src.sendSystemMessage(Component.literal("\u26a0 Wilderness Regeneration")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        src.sendSystemMessage(Component.literal("This will regenerate ALL unclaimed chunks across all dimensions.")
                .withStyle(ChatFormatting.YELLOW));
        src.sendSystemMessage(Component.literal("Claimed chunks and a 3-chunk buffer around them will be preserved.")
                .withStyle(ChatFormatting.YELLOW));
        src.sendSystemMessage(Component.literal("Unclaimed builds, chests, and entities will be permanently lost.")
                .withStyle(ChatFormatting.RED));
        src.sendSystemMessage(Component.literal("The regen will execute on the next server shutdown.")
                .withStyle(ChatFormatting.GRAY));
        src.sendSystemMessage(Component.empty());

        Component confirm = Component.literal("[CONFIRM]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand("/smp regen confirm"))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.literal("Click to queue wilderness regen"))));

        Component cancel = Component.literal("[CANCEL]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.RED)
                        .withBold(true));

        src.sendSystemMessage(Component.literal("  ")
                .append(confirm)
                .append(Component.literal("  "))
                .append(cancel));
        src.sendSystemMessage(Component.empty());

        return 1;
    }

    private static int confirmRegen(CommandContext<CommandSourceStack> ctx) {
        // Regen destroys wilderness chunks and pregen builds them, so a server that ran both would
        // spend every restart undoing the last one.
        if (mc.smpessentials.config.SmpConfig.PREGEN_ENABLED) {
            ctx.getSource().sendFailure(Component.literal(
                    "Chunk pre-generation is enabled. Turn it off before queueing a regen."));
            return 0;
        }
        try {
            ChunkRegenManager.queueRegen(ctx.getSource().getServer());
            ctx.getSource().sendSuccess(
                    () -> Component.literal("\u00a7eWilderness regen queued. Restart the server to apply.")
                            .append(Component.literal(" Use "))
                            .append(Component.literal("/smp regen cancel").withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(" to undo.")),
                    true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Failed to queue regen: " + e.getMessage()));
            mc.smpessentials.SmpUtilsMod.LOGGER.error("Failed to queue wilderness regen", e);
            return 0;
        }
    }

    private static int cancelRegen(CommandContext<CommandSourceStack> ctx) {
        try {
            if (!ChunkRegenManager.isPending(ctx.getSource().getServer())) {
                ctx.getSource().sendFailure(Component.literal("No wilderness regen is pending."));
                return 0;
            }
            ChunkRegenManager.cancelRegen(ctx.getSource().getServer());
            ctx.getSource().sendSuccess(
                    () -> Component.literal("\u00a7aWilderness regen cancelled."),
                    true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Failed to cancel regen: " + e.getMessage()));
            return 0;
        }
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        boolean pending = ChunkRegenManager.isPending(ctx.getSource().getServer());
        if (pending) {
            ctx.getSource().sendSystemMessage(Component.literal("\u00a7eWilderness regen is PENDING.")
                    .append(Component.literal(" It will execute on next server shutdown.").withStyle(ChatFormatting.GRAY)));
        } else {
            ctx.getSource().sendSystemMessage(Component.literal("\u00a7aNo wilderness regen pending."));
        }
        return 1;
    }
}
