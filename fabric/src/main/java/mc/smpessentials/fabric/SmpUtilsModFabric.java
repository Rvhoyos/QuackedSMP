package mc.smpessentials.fabric;

import net.fabricmc.api.ModInitializer;

import mc.smpessentials.SmpUtilsMod;

/**
 * Fabric mod entrypoint for QuackedSMP.
 *
 * <p>Runs common initialisation via {@link mc.smpessentials.SmpUtilsMod#init()} and
 * wires all Fabric API event callbacks that delegate to the platform-agnostic common module.
 * All game logic lives in {@code common}; this class is intentionally thin.
 */
public final class SmpUtilsModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        SmpUtilsMod.init();

        // 1. Commands
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT
                .register((dispatcher, registryAccess, environment) -> {
                    mc.smpessentials.commands.CommandRegistrar.registerCommands(dispatcher, registryAccess,
                            environment);
                });

        // 2. Server Started/Stopping
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            mc.smpessentials.chatfilter.ChatFilter.onServerStart(server);
            mc.smpessentials.voicechat.VoicechatIntegration.onServerStart(server);
            mc.smpessentials.keepinv.KeepInvSavedData.enforceGamerule(server);
            mc.smpessentials.dims.DimManager.restoreAll(server);
            mc.smpessentials.bluemap.BlueMapIntegration.onServerStart(server);
            mc.smpessentials.dashboard.DashboardManager.onServerStart(server);
            mc.smpessentials.votifier.VoteHandler.init(server);
            mc.smpessentials.votifier.VotifierListener.start();
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            mc.smpessentials.commands.EndResetLogic.onServerStopping(server);
            mc.smpessentials.dashboard.DashboardManager.onServerStop();
            mc.smpessentials.votifier.VotifierListener.stop();
        });
        // SERVER_STOPPED fires after level.dat is written — safe to patch it here.
        // Chunk regen runs first (needs dimension data intact), then scrubLevelDat cleans up deleted custom dims.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            mc.smpessentials.regen.ChunkRegenManager.onServerStopped(server);
            mc.smpessentials.dims.DimManager.scrubLevelDat(server);
        });

        // 3. Player Join / Disconnect
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            net.minecraft.server.level.ServerPlayer player = handler.getPlayer();
            mc.smpessentials.events.JoinMessageHandler.onPlayerJoin(player);
            mc.smpessentials.skills.SkillEvents.onPlayerJoin(player);
            mc.smpessentials.tier.TierService.onPlayerJoin(player);
            mc.smpessentials.punish.PunishManager pm =
                    mc.smpessentials.punish.PunishManager.get(server);
            if (pm.isPending(player.getUUID())) {
                pm.punish(player);
            }
            mc.smpessentials.dashboard.DashboardManager.broadcastPlayerJoin(player.getGameProfile().name());
            mc.smpessentials.votifier.VoteHandler.onPlayerJoin(player);
            mc.smpessentials.hardcore.HardcoreSavedData.get(server).onPlayerReconnect(player);
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            net.minecraft.server.level.ServerPlayer player = handler.getPlayer();
            mc.smpessentials.skills.SkillEvents.onPlayerLoggedOut(player);
            mc.smpessentials.teleport.TeleportService.clearForPlayer(player.getUUID());
            mc.smpessentials.dashboard.DashboardManager.broadcastPlayerLeave(player.getGameProfile().name());
        });

        // 9. Keep Inventory — drop items on death for opted-out players
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof net.minecraft.server.level.ServerPlayer sp) {
                mc.smpessentials.hardcore.HardcoreSavedData.get(((net.minecraft.server.level.ServerLevel) sp.level()).getServer()).onPlayerDeath(sp);
                mc.smpessentials.keepinv.KeepInvSavedData.onPlayerDeath(sp);
            }
        });

        // 10. Hardcore — set spectator on respawn
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            mc.smpessentials.hardcore.HardcoreSavedData.get(((net.minecraft.server.level.ServerLevel) newPlayer.level()).getServer()).onPlayerRespawn(newPlayer);
        });

        // 4. Server Tick + Player Tick processing
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            mc.smpessentials.events.MessageScheduler.onServerTick(server);
            mc.smpessentials.bluemap.BlueMapIntegration.onServerTick(server);
            for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                mc.smpessentials.teleport.TeleportScheduler.onPlayerTick(player);
                mc.smpessentials.skills.SkillEvents.onPlayerTick(player);
                mc.smpessentials.dims.EtherFallthrough.tick(player);
            }
        });

        // Scout Zoom activation is handled by PlayerActionMixin (common module)

        // 5. Chat Decorator
        net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent.EVENT.register(
                net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent.STYLING_PHASE, (sender, message) -> {
                    if (sender != null) {
                        mc.smpessentials.dashboard.DashboardManager.broadcastChat(
                                sender.getGameProfile().name(), message.getString());
                    }
                    return mc.smpessentials.chatfilter.ChatFilter.onDecorate(sender, message);
                });

        // 6. Block Break
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE
                .register((world, p, pos, state, blockEntity) -> {
                    mc.smpessentials.skills.SkillEvents.onBlockBreak(world, pos, state, p);
                    return mc.smpessentials.claims.ClaimProtection.onBlockBreak(world, pos, state, p);
                });

        // 7. Right Click Block
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((p, world, hand, hitResult) -> {
            net.minecraft.world.InteractionResult portal =
                    mc.smpessentials.dims.CustomPortalActivator.onRightClickBlock(p, world, hand, hitResult.getBlockPos(), hitResult.getDirection());
            if (portal != net.minecraft.world.InteractionResult.PASS) return portal;
            return mc.smpessentials.claims.ClaimProtection.onRightClickBlock(p, hitResult.getBlockPos());
        });

        // 8. Interact Entity
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((p, world, hand, entity, hitResult) -> {
            if (!mc.smpessentials.claims.ClaimProtection.onInteractEntity(p, entity)) {
                return net.minecraft.world.InteractionResult.FAIL;
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
    }
}
