package mc.smpessentials.punish;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handles the registration and execution of the /punish command.
 * This command allows operators to clear all belongings of a player, 
 * including their inventory and items stored in claimed chunks.
 */
public class PunishCommand {

    /**
     * Registers the /punish command with the dispatcher.
     * Requires LEVEL_GAMEMASTERS (OP level 2) permissions.
     * 
     * @param dispatcher The command dispatcher.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("punish")
                .requires(s -> net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(s.permissions()))
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(PunishCommand::punish)));
    }

    /**
     * Execution logic for the /punish command.
     * Identifies targeted players and applies punishments immediately if online, 
     * or queues them for next login if offline.
     * 
     * @param ctx The command context.
     * @return 1 on successful execution for all targeted profiles.
     */
    private static int punish(CommandContext<CommandSourceStack> ctx) {
        try {
            // Retrieve targeted profiles (supports wildcard/multi-select)
            var profiles = GameProfileArgument.getGameProfiles(ctx, "player");
            PunishManager manager = PunishManager.get(ctx.getSource().getServer());

            for (var profile : profiles) {
                // Attempt to find the online player object
                ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayer(profile.id());

                if (target != null) {
                    // Player is online: Apply full punishment immediately
                    manager.punish(target);
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\u00a7aPunished " + profile.name() + " (Online). Inventory and nearby containers cleared."), true);
                    target.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7cYour inventory and claims have been cleared by an operator."));
                } else {
                    // Player is offline: Queue the punishment for their return
                    manager.queuePunishment(profile.id());
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("\u00a7e" + profile.name() + " is offline. Punishment queued for next login."), true);
                }
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Failed to execute punishment: " + e.getMessage()));
            return 0;
        }
    }
}
