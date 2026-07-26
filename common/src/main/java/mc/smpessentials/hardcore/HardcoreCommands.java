package mc.smpessentials.hardcore;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class HardcoreCommands {

    private HardcoreCommands() {}

    private static boolean checkEnabled(CommandContext<CommandSourceStack> ctx) {
        if (!SmpConfig.HARDCORE_ENABLED) {
            ctx.getSource().sendFailure(Component.literal("Hardcore mode is disabled on this server."));
            return false;
        }
        return true;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> hardcoreSubtree() {
        return Commands.literal("hardcore")
                .then(Commands.literal("create")
                        .requires(s -> s.getEntity() instanceof ServerPlayer)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("password", StringArgumentType.word())
                                        .executes(HardcoreCommands::create))))
                .then(Commands.literal("join")
                        .requires(s -> s.getEntity() instanceof ServerPlayer)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("password", StringArgumentType.word())
                                        .executes(HardcoreCommands::join))))
                .then(Commands.literal("leave")
                        .requires(s -> s.getEntity() instanceof ServerPlayer)
                        .executes(HardcoreCommands::leave))
                .then(Commands.literal("status")
                        .requires(s -> s.getEntity() instanceof ServerPlayer)
                        .executes(HardcoreCommands::statusSelf)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(HardcoreCommands::statusNamed)))
                .then(Commands.literal("list")
                        .requires(s -> s.getEntity() instanceof ServerPlayer)
                        .executes(HardcoreCommands::list));
    }

    private static int create(CommandContext<CommandSourceStack> ctx) {
        if (!checkEnabled(ctx)) return 0;
        ServerPlayer player = (ServerPlayer) ctx.getSource().getEntity();
        String name = StringArgumentType.getString(ctx, "name");
        String password = StringArgumentType.getString(ctx, "password");

        var server = ((ServerLevel) player.level()).getServer();
        HardcoreSavedData data = HardcoreSavedData.get(server);
        String error = data.createSession(name, password, player, (ServerLevel) player.level());
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }

        // Auto-join the creator
        String joinError = data.joinSession(name, password, player);
        if (joinError != null) {
            ctx.getSource().sendFailure(Component.literal("Session created but failed to join: " + joinError));
            return 0;
        }

        // Broadcast to server (no password)
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("\u00a76[Hardcore] \u00a7e" + player.getName().getString()
                        + " \u00a7astarted a hardcore session: \u00a7f" + name
                        + "\u00a7a. Join with \u00a7f/smp hardcore join " + name + " <password>"),
                false);
        return 1;
    }

    private static int join(CommandContext<CommandSourceStack> ctx) {
        if (!checkEnabled(ctx)) return 0;
        ServerPlayer player = (ServerPlayer) ctx.getSource().getEntity();
        String name = StringArgumentType.getString(ctx, "name");
        String password = StringArgumentType.getString(ctx, "password");

        HardcoreSavedData data = HardcoreSavedData.get(((ServerLevel) player.level()).getServer());
        String error = data.joinSession(name, password, player);
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        // joinSession sends the appropriate joined/resumed message
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) {
        // Always allow leave even when disabled, so players can exit active sessions
        ServerPlayer player = (ServerPlayer) ctx.getSource().getEntity();
        HardcoreSavedData data = HardcoreSavedData.get(((ServerLevel) player.level()).getServer());
        String error = data.leaveSession(player);
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }

        player.sendSystemMessage(Component.literal(
                "\u00a7e[Hardcore] Left session. Your inventory has been restored."));
        return 1;
    }

    private static int statusSelf(CommandContext<CommandSourceStack> ctx) {
        if (!checkEnabled(ctx)) return 0;
        ServerPlayer player = (ServerPlayer) ctx.getSource().getEntity();
        HardcoreSavedData data = HardcoreSavedData.get(((ServerLevel) player.level()).getServer());
        String sessionName = data.getPlayerSessionName(player.getUUID());
        if (sessionName == null) {
            player.sendSystemMessage(Component.literal("\u00a77You are not in a hardcore session."));
            return 1;
        }
        return showStatus(player, data.getSession(sessionName));
    }

    private static int statusNamed(CommandContext<CommandSourceStack> ctx) {
        if (!checkEnabled(ctx)) return 0;
        ServerPlayer player = (ServerPlayer) ctx.getSource().getEntity();
        String name = StringArgumentType.getString(ctx, "name");
        HardcoreSavedData data = HardcoreSavedData.get(((ServerLevel) player.level()).getServer());
        HardcoreSavedData.SessionData session = data.getSession(name);
        if (session == null) {
            ctx.getSource().sendFailure(Component.literal("Session '" + name + "' not found."));
            return 0;
        }
        return showStatus(player, session);
    }

    private static int showStatus(ServerPlayer player, HardcoreSavedData.SessionData session) {
        if (session == null) {
            player.sendSystemMessage(Component.literal("\u00a7cSession not found."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("\u00a76== Hardcore: " + session.getName() + " =="));
        player.sendSystemMessage(Component.literal("\u00a7eAlive: \u00a7a" + session.getAliveCount()
                + " \u00a7eDead: \u00a7c" + session.getDeadCount()
                + " \u00a7ePeak: \u00a7f" + session.getPeakPlayers()));
        player.sendSystemMessage(Component.literal("\u00a7eDeaths: \u00a7c" + session.getDeaths()
                + "\u00a77/\u00a7c" + session.getThreshold()
                + " \u00a77(" + SmpConfig.HARDCORE_DEATH_PERCENT + "% of peak)"));
        player.sendSystemMessage(Component.literal("\u00a7eStart: \u00a7f"
                + session.getStartX() + ", " + session.getStartY() + ", " + session.getStartZ()));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        if (!checkEnabled(ctx)) return 0;
        ServerPlayer player = (ServerPlayer) ctx.getSource().getEntity();
        HardcoreSavedData data = HardcoreSavedData.get(((ServerLevel) player.level()).getServer());
        var sessions = data.allSessions();

        if (sessions.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "\u00a77No active hardcore sessions. Create one with /smp hardcore create <name> <password>"));
            return 1;
        }

        player.sendSystemMessage(Component.literal("\u00a76== Hardcore Sessions =="));
        for (var session : sessions) {
            player.sendSystemMessage(Component.literal(
                    "\u00a7e" + session.getName() + " \u00a77- \u00a7a" + session.getAliveCount() + " alive"
                            + " \u00a7c" + session.getDeaths() + "/" + session.getThreshold() + " deaths"));
        }
        return 1;
    }
}
