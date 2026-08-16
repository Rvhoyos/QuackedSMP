package mc.smpessentials.timelapse;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /timelapse capture} triggers an immediate frame capture, delegating to
 * {@link TimelapseService} which owns the flush, render and store lifecycle.
 */
public final class TimelapseCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("timelapse")
                .requires(s -> Commands.LEVEL_GAMEMASTERS.check(s.permissions()))
                .then(Commands.literal("capture").executes(TimelapseCommand::capture)));
    }

    private static int capture(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (TimelapseService.get().isRunning()) {
            source.sendFailure(Component.literal("§cA timelapse capture is already in progress."));
            return 0;
        }
        TimelapseService.get().capture(source.getServer());
        source.sendSuccess(() -> Component.literal("§aTimelapse capture started."), false);
        return 1;
    }
}
