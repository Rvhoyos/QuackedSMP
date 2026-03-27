package mc.smpessentials.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers miscellaneous operator and utility commands under the {@code /smp} prefix,
 * plus standalone {@code /mute} and {@code /unmute}.
 *
 * <ul>
 *   <li>{@code /smp reload} — hot-reload {@code quackedsmp.json} and chat-filter rules (OP).</li>
 *   <li>{@code /smp config} — open the in-game config GUI (OP).</li>
 *   <li>{@code /smp config reset} — reset config to factory defaults (OP).</li>
 *   <li>{@code /smp end reset dragon} — respawn the Ender Dragon live without a world reset (OP).</li>
 *   <li>{@code /smp end reset world} — queue a full End-dimension reset on next server restart (OP).</li>
 *   <li>{@code /smp help} — print the command reference to the player's chat.</li>
 *   <li>{@code /smp admin setpassword <password>} — set or update the admin panel password; also
 *       enables the panel if it was disabled. Min 8 characters. Invalidates all active sessions (OP).</li>
 *   <li>{@code /mute <player> <minutes>} — mute a player for a fixed duration (OP).</li>
 *   <li>{@code /unmute <player>} — remove an active mute (OP).</li>
 * </ul>
 */
public class GeneralCommands {
        /** Registers all commands provided by this class with the given dispatcher. */
        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
                dispatcher.register(Commands.literal("smp")
                                .then(Commands.literal("reload")
                                                .requires(s -> net.minecraft.commands.Commands.LEVEL_GAMEMASTERS
                                                                .check(s.permissions())) // OP only
                                                .executes(GeneralCommands::reloadConfig))
                                .then(Commands.literal("bluemap")
                                                .requires(s -> net.minecraft.commands.Commands.LEVEL_GAMEMASTERS
                                                                .check(s.permissions()))
                                                .executes(GeneralCommands::forceBlueMapUpdate))
                                .then(Commands.literal("config")
                                                .requires(s -> net.minecraft.commands.Commands.LEVEL_GAMEMASTERS
                                                                .check(s.permissions())) // OP only
                                                .executes(GeneralCommands::openConfig)
                                                .then(Commands.literal("reset")
                                                                .executes(GeneralCommands::resetConfig)))
                                .then(Commands.literal("end")
                                                .requires(s -> net.minecraft.commands.Commands.LEVEL_GAMEMASTERS
                                                                .check(s.permissions()))
                                                .then(Commands.literal("reset")
                                                                .then(Commands.literal("dragon")
                                                                                .executes(GeneralCommands::resetEndDragon))
                                                                .then(Commands.literal("world")
                                                                                .executes(GeneralCommands::resetEndWorld))))
                                .then(Commands.literal("help")
                                                .executes(GeneralCommands::sendHelp))
                                .then(Commands.literal("admin")
                                                .requires(s -> net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(s.permissions()))
                                                .then(Commands.literal("setpassword")
                                                                .then(Commands.argument("password",
                                                                                com.mojang.brigadier.arguments.StringArgumentType.string())
                                                                                .executes(GeneralCommands::setAdminPassword)))));

                dispatcher.register(Commands.literal("mute")
                                .requires(s -> net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(s.permissions()))
                                .then(Commands.argument("player",
                                                net.minecraft.commands.arguments.EntityArgument.player())
                                                .then(Commands.argument("minutes",
                                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                                                .integer(1))
                                                                .executes(GeneralCommands::mutePlayer))));

                dispatcher.register(Commands.literal("unmute")
                                .requires(s -> net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(s.permissions()))
                                .then(Commands.argument("player",
                                                net.minecraft.commands.arguments.EntityArgument.player())
                                                .executes(GeneralCommands::unmutePlayer)));
        }

        private static int mutePlayer(CommandContext<CommandSourceStack> ctx) {
                try {
                        ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                        int minutes = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "minutes");

                        var data = mc.smpessentials.chatfilter.ChatFilter.getData(ctx.getSource().getServer());
                        data.mute(target.getUUID(), minutes * 60 * 1000L);

                        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aMuted "
                                        + target.getName().getString() + " for " + minutes + " minutes."), true);
                        target.sendSystemMessage(Component.literal(
                                        "\u00a7cYou have been muted for " + minutes + " minutes by an operator."));
                        return 1;
                } catch (Exception e) {
                        ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
                        return 0;
                }
        }

        private static int unmutePlayer(CommandContext<CommandSourceStack> ctx) {
                try {
                        ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");

                        var data = mc.smpessentials.chatfilter.ChatFilter.getData(ctx.getSource().getServer());
                        data.unmute(target.getUUID());

                        ctx.getSource().sendSuccess(
                                        () -> Component.literal("\u00a7aUnmuted " + target.getName().getString() + "."),
                                        true);
                        target.sendSystemMessage(Component.literal("\u00a7aYou have been unmuted."));
                        return 1;
                } catch (Exception e) {
                        ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
                        return 0;
                }
        }

        private static int openConfig(CommandContext<CommandSourceStack> ctx) {
                if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        mc.smpessentials.config.ConfigGui.open(sp);
                        return 1;
                }
                return 0;
        }

        private static int sendHelp(CommandContext<CommandSourceStack> ctx) {
                net.minecraft.ChatFormatting title = net.minecraft.ChatFormatting.GOLD;
                net.minecraft.ChatFormatting cmd = net.minecraft.ChatFormatting.YELLOW;
                net.minecraft.ChatFormatting desc = net.minecraft.ChatFormatting.WHITE;

                CommandSourceStack src = ctx.getSource();
                src.sendSystemMessage(
                                Component.literal("== QuackedSMP Commands ==").withStyle(title,
                                                net.minecraft.ChatFormatting.BOLD));

                src.sendSystemMessage(Component.literal("/claim").withStyle(cmd)
                                .append(Component.literal(" - Claim current chunk to protect it").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/trust <player>").withStyle(cmd)
                                .append(Component.literal(" - Give friend permission in claims").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/tpr <player>").withStyle(cmd)
                                .append(Component.literal(" - Request teleport to friend").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/skills").withStyle(cmd)
                                .append(Component.literal(" - View your RPG skill progression").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/keepinv on/off").withStyle(cmd)
                                .append(Component.literal(" - Toggle keeping items on death").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/verify confirm").withStyle(cmd)
                                .append(Component.literal(" - Enable proximity voice chat").withStyle(desc)));
                src.sendSystemMessage(Component.literal("/rules").withStyle(cmd)
                                .append(Component.literal(" - Read the server rules").withStyle(desc)));

                return 1;
        }

        private static int forceBlueMapUpdate(CommandContext<CommandSourceStack> ctx) {
                var mgr = mc.smpessentials.bluemap.BlueMapIntegration.getMarkerManager();
                if (mgr == null) {
                        ctx.getSource().sendFailure(Component.literal("BlueMap is not loaded."));
                        return 0;
                }
                mgr.updateAll();
                ctx.getSource().sendSuccess(() -> Component.literal("BlueMap markers refreshed."), true);
                return 1;
        }

        private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
                try {
                        SmpConfig.load();
                        mc.smpessentials.votifier.VotifierListener.restart();
                        var res = mc.smpessentials.chatfilter.ChatFilterConfig
                                        .mergeFromConfig(ctx.getSource().getServer());
                        ctx.getSource().sendSuccess(() -> Component.literal("QuackedSMP config reloaded!"), true);
                        if (!res.warnings().isEmpty()) {
                                ctx.getSource().sendSystemMessage(
                                                Component.literal("\u00a7e[Warning] Chat Filter issues:"));
                                for (String w : res.warnings()) {
                                        ctx.getSource().sendSystemMessage(Component.literal("\u00a7e - " + w));
                                }
                        }
                        return 1;
                } catch (mc.smpessentials.config.ConfigIO.ConfigParseException e) {
                        ctx.getSource().sendFailure(
                                        Component.literal("\u00a7c[Error] Malformed JSON config: " + e.getMessage()));
                        return 0;
                } catch (Exception e) {
                        ctx.getSource().sendFailure(Component.literal("Failed to reload config: " + e.getMessage()));
                        e.printStackTrace();
                        return 0;
                }
        }

        private static int resetConfig(CommandContext<CommandSourceStack> ctx) {
                try {
                        mc.smpessentials.config.ConfigIO.resetToFactory();
                        ctx.getSource().sendSuccess(
                                        () -> Component.literal("\u00a7aConfiguration reset to factory defaults!"),
                                        true);
                        return 1;
                } catch (Exception e) {
                        ctx.getSource().sendFailure(Component.literal("Failed to reset config: " + e.getMessage()));
                        e.printStackTrace();
                        return 0;
                }
        }

        private static int resetEndDragon(CommandContext<CommandSourceStack> ctx) {
                int result = EndResetLogic.resetDragon(ctx.getSource().getServer(), false);
                if (result == 1) {
                        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aEnd Dragon fight has been reset!"),
                                        true);
                } else if (result == 0) {
                        ctx.getSource().sendFailure(Component.literal("Could not find the End or Dragon fight state."));
                } else {
                        ctx.getSource().sendFailure(Component.literal("An error occurred while resetting the dragon."));
                }
                return result;
        }

        /**
         * Sets or updates the admin panel password and enables the panel.
         * This is the only in-game way to enable the admin panel — there is no separate enable command.
         * Requires OP. Min 8 characters. Invalidates all active sessions on success.
         */
        private static int setAdminPassword(CommandContext<CommandSourceStack> ctx) {
                try {
                        String password = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "password");
                        if (password.length() < 8) {
                                ctx.getSource().sendFailure(Component.literal("Password must be at least 8 characters."));
                                return 0;
                        }
                        mc.smpessentials.config.SmpConfig.ADMIN_ENABLED = true;
                        mc.smpessentials.dashboard.AdminAuth.setPassword(password);
                        mc.smpessentials.dashboard.AdminAuth.clearSessions();
                        mc.smpessentials.dashboard.DashboardManager.scheduleEnable();
                        ctx.getSource().sendSuccess(
                                        () -> Component.literal("\u00a7aAdmin panel enabled and password updated. All sessions invalidated."),
                                        true);
                        return 1;
                } catch (Exception e) {
                        ctx.getSource().sendFailure(Component.literal("Failed to set password: " + e.getMessage()));
                        return 0;
                }
        }


        private static int resetEndWorld(CommandContext<CommandSourceStack> ctx) {
                int result = EndResetLogic.resetWorld(ctx.getSource().getServer());
                if (result == 2) {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                        "\u00a7eEnd dimension queued for reset. \u00a7cRestart the server whenever you're ready to regenerate the terrain."),
                                        true);
                } else if (result == 0) {
                        ctx.getSource().sendFailure(Component.literal("Could not find the End dimension."));
                } else {
                        ctx.getSource().sendFailure(Component.literal("An error occurred while queuing End reset."));
                }
                return result;
        }
}
