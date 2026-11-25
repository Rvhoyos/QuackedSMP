package mc.smpessentials.pregen;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** /smp pregen: where each dimension stands, and what finishing it would cost. */
public final class PregenCommands {

    private PregenCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> pregenSubtree() {
        return Commands.literal("pregen")
                .requires(s -> Commands.LEVEL_GAMEMASTERS.check(s.permissions()))
                .executes(PregenCommands::showStatus)
                .then(Commands.literal("status").executes(PregenCommands::showStatus))
                .then(Commands.literal("estimate").executes(PregenCommands::showEstimate));
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!SmpConfig.PREGEN_ENABLED) {
            src.sendSystemMessage(Component.literal("Chunk pre-generation is off.")
                    .withStyle(ChatFormatting.GRAY));
            return 1;
        }

        List<PregenReport> reports = PregenReport.forServer(src.getServer());
        if (reports.isEmpty()) {
            src.sendSystemMessage(Component.literal("Chunk pre-generation is on, but no dimensions are listed.")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        src.sendSystemMessage(Component.literal("Chunk pre-generation")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (PregenReport report : reports) {
            src.sendSystemMessage(line(report));
        }
        if (PregenReport.restartRequired(reports)) {
            src.sendSystemMessage(Component.literal(
                    "Restart to run it. Nobody can join until it finishes.")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    // Same shape the panel row uses, so the two never read differently.
    private static Component line(PregenReport report) {
        if (!report.present()) {
            return Component.literal("  " + report.dimension() + ": not on this server")
                    .withStyle(ChatFormatting.RED);
        }
        long percent = report.chunks() > 0L ? report.done() * 100L / report.chunks() : 0L;
        return Component.literal(String.format(Locale.US, "  %s: %d%% of %,d chunks, radius %,d",
                report.dimension(), percent, report.chunks(), report.radiusBlocks()))
                .withStyle(report.complete() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
    }

    private static int showEstimate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        List<PregenReport> reports = PregenReport.forServer(src.getServer());

        long chunks = 0L;
        long bytes = 0L;
        long seconds = 0L;
        long free = 0L;
        for (PregenReport report : reports) {
            if (!report.present()) continue;
            chunks += report.remaining();
            bytes += report.estimate().bytes();
            seconds += report.estimate().seconds();
            free = report.estimate().freeBytes();
        }

        src.sendSystemMessage(Component.literal(String.format(Locale.US,
                "Left to generate: %,d chunks, ~%s, ~%s",
                chunks, PregenEstimate.formatBytes(bytes), PregenEstimate.formatDuration(seconds)))
                .withStyle(ChatFormatting.YELLOW));
        src.sendSystemMessage(Component.literal("Free disk: " + PregenEstimate.formatBytes(free))
                .withStyle(bytes < free ? ChatFormatting.GRAY : ChatFormatting.RED));
        return 1;
    }
}
