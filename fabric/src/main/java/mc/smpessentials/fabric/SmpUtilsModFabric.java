package mc.smpessentials.fabric;

import net.fabricmc.api.ModInitializer;

import mc.smpessentials.SmpUtilsMod;

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
            mc.smpessentials.bluemap.BlueMapIntegration.onServerStart(server);
            mc.smpessentials.keepinv.KeepInvSavedData.enforceGamerule(server);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            mc.smpessentials.commands.EndResetLogic.onServerStopping(server);
        });

        // 3. Player Join / Disconnect
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            mc.smpessentials.events.JoinMessageHandler.onPlayerJoin(handler.getPlayer());
            mc.smpessentials.skills.SkillEvents.onPlayerJoin(handler.getPlayer());
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            mc.smpessentials.skills.SkillEvents.onPlayerLoggedOut(handler.getPlayer());
        });

        // 9. Keep Inventory — drop items on death for opted-out players
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof net.minecraft.server.level.ServerPlayer sp) {
                mc.smpessentials.keepinv.KeepInvSavedData.onPlayerDeath(sp);
            }
        });

        // 4. Server Tick + Player Tick processing
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            mc.smpessentials.events.MessageScheduler.onServerTick(server);
            mc.smpessentials.bluemap.BlueMapIntegration.onServerTick(server);
            for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                mc.smpessentials.teleport.TeleportScheduler.onPlayerTick(player);
            }
        });

        // 5. Chat Decorator
        net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent.EVENT.register(
                net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent.STYLING_PHASE, (sender, message) -> {
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
