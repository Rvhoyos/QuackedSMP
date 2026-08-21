package mc.smpessentials.commands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * Central command hook: subscribes to Architectury's cross-loader command
 * registration event.
 * No Brigadier imports are required here; we rely on lambda type inference.
 */
public final class CommandRegistrar {
        private CommandRegistrar() {
        }

        public static void registerCommands(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher,
                        net.minecraft.commands.CommandBuildContext buildContext,
                        Commands.CommandSelection selection) {
                // --- /home ---
                dispatcher.register(
                                net.minecraft.commands.Commands.literal("home")
                                                .requires(src -> src
                                                                .getEntity() instanceof net.minecraft.server.level.ServerPlayer)
                                                .executes(ctx -> mc.smpessentials.commands.HomeCommand
                                                                .execute(ctx.getSource())));
                // --- /visit <name> ---
                mc.smpessentials.commands.VisitCommand.register(dispatcher);
                // --- /spawn ---
                dispatcher.register(
                                Commands.literal("spawn")
                                                .requires(src -> src.getEntity() instanceof ServerPlayer)
                                                .executes(ctx -> mc.smpessentials.commands.SpawnCommand
                                                                .execute(ctx.getSource())));
                dispatcher.register(
                                net.minecraft.commands.Commands.literal("rules")
                                                .requires(src -> src
                                                                .getEntity() instanceof net.minecraft.server.level.ServerPlayer)
                                                .executes(ctx -> mc.smpessentials.commands.RulesCommand
                                                                .execute(ctx.getSource())));
                // claims: /claim /unclaim /claims
                mc.smpessentials.claims.ClaimCommands.register(dispatcher);
                // claims: /trust /untrust
                mc.smpessentials.claims.TrustCommands.register(dispatcher);

                mc.smpessentials.chatfilter.ChatFilterCommands.register(dispatcher);
                mc.smpessentials.commands.GeneralCommands.register(dispatcher);
                mc.smpessentials.teleport.TeleportCommands.register(dispatcher);
                mc.smpessentials.rtp.RtpCommand.register(dispatcher);
                mc.smpessentials.welcomebook.WelcomeBookCommand.register(dispatcher);
                mc.smpessentials.commands.SkillCommands.register(dispatcher);
                mc.smpessentials.punish.PunishCommand.register(dispatcher);
                mc.smpessentials.keepinv.KeepInvCommand.register(dispatcher);
                mc.smpessentials.ageverify.AgeVerifyCommand.register(dispatcher);
                mc.smpessentials.commands.VipCommand.register(dispatcher);
                mc.smpessentials.dims.DimCommands.register(dispatcher);
                mc.smpessentials.shops.ShopCommands.register(dispatcher);
                mc.smpessentials.timelapse.TimelapseCommand.register(dispatcher);
                dispatcher.register(net.minecraft.commands.Commands.literal("sos")
                                .requires(src -> src.getEntity() instanceof net.minecraft.server.level.ServerPlayer)
                                .executes(ctx -> mc.smpessentials.commands.SosCommand.execute(ctx.getSource())));

        }

        /** Simple OP check. Uses Minecraft's documented permission levels. */
        public static boolean isOp(CommandSourceStack source) {
                // LEVEL_GAMEMASTERS is the typical "op level 2+" gate for server commands.
                return Commands.LEVEL_GAMEMASTERS.check(source.permissions());
        }
}
