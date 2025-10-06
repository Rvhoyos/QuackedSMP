package mc.smpessentials.commands;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * Central command hook: subscribes to Architectury's cross-loader command registration event.
 * No Brigadier imports are required here; we rely on lambda type inference.
 */
public final class CommandRegistrar {
    private CommandRegistrar() {}

    /** Call this once from your common init. */
    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, buildContext, environment) -> {
            // --- /home ---
            dispatcher.register(
                net.minecraft.commands.Commands.literal("home")
                    .requires(src -> src.getEntity() instanceof net.minecraft.server.level.ServerPlayer)
                    .executes(ctx -> mc.smpessentials.commands.HomeCommand.execute(ctx.getSource()))
            );
            // --- /spawn ---
            dispatcher.register(
                Commands.literal("spawn")
                    .requires(src -> src.getEntity() instanceof ServerPlayer)
                    .executes(ctx -> mc.smpessentials.commands.SpawnCommand.execute(ctx.getSource()))
            );
        });
        
    }


    /** Simple OP check. Uses Minecraft's documented permission levels. */
    public static boolean isOp(CommandSourceStack source) {
        // LEVEL_GAMEMASTERS is the typical "op level 2+" gate for server commands.
        return source.hasPermission(Commands.LEVEL_GAMEMASTERS);
    }
}
