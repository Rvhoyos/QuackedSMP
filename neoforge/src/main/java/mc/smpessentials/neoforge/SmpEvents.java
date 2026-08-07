package mc.smpessentials.neoforge;

import mc.smpessentials.commands.CommandRegistrar;
import mc.smpessentials.chatfilter.ChatFilter;
import mc.smpessentials.events.JoinMessageHandler;
import mc.smpessentials.events.MessageScheduler;
import mc.smpessentials.skills.SkillEvents;
import mc.smpessentials.teleport.TeleportScheduler;
import mc.smpessentials.claims.ClaimProtection;
import mc.smpessentials.claims.SpawnProtection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge event hub for QuackedSMP.
 *
 * <p>Every method is a static {@link SubscribeEvent} handler registered via
 * {@code NeoForge.EVENT_BUS.register(SmpEvents.class)} in
 * {@link mc.smpessentials.neoforge.SmpUtilsModNeoForge}. Each handler delegates
 * immediately to the platform-agnostic common module; no game logic lives here.
 */
public class SmpEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandRegistrar.registerCommands(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection());
    }

    @SubscribeEvent
    public static void onServerStarting(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        mc.smpessentials.SavedDataMigration.migrate(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ChatFilter.onServerStart(event.getServer());
        mc.smpessentials.voicechat.VoicechatIntegration.onServerStart(event.getServer());
        mc.smpessentials.keepinv.KeepInvSavedData.enforceGamerule(event.getServer());
        mc.smpessentials.dims.DimManager.restoreAll(event.getServer());
        mc.smpessentials.bluemap.BlueMapIntegration.onServerStart(event.getServer());
        mc.smpessentials.dashboard.DashboardManager.onServerStart(event.getServer());
        mc.smpessentials.votifier.VoteHandler.init(event.getServer());
        mc.smpessentials.votifier.VotifierListener.start();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        mc.smpessentials.commands.EndResetLogic.onServerStopping(event.getServer());
        mc.smpessentials.dashboard.DashboardManager.onServerStop();
        mc.smpessentials.votifier.VotifierListener.stop();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        mc.smpessentials.regen.ChunkRegenManager.onServerStopped(event.getServer());

        mc.smpessentials.SmpUtilsMod.scheduleExitGuard();
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JoinMessageHandler.onPlayerJoin(player);
            SkillEvents.onPlayerJoin(player);
            mc.smpessentials.tier.TierService.onPlayerJoin(player);
            mc.smpessentials.punish.PunishManager pm =
                    mc.smpessentials.punish.PunishManager.get(((net.minecraft.server.level.ServerLevel) player.level()).getServer());
            if (pm.isPending(player.getUUID())) {
                pm.punish(player);
            }
            mc.smpessentials.dashboard.DashboardManager.broadcastPlayerJoin(player.getGameProfile().name());
            mc.smpessentials.votifier.VoteHandler.onPlayerJoin(player);
            mc.smpessentials.hardcore.HardcoreSavedData hc =
                    mc.smpessentials.hardcore.HardcoreSavedData.get(((net.minecraft.server.level.ServerLevel) player.level()).getServer());
            hc.markHeartsSent(player);
            hc.onPlayerReconnect(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SkillEvents.onPlayerLoggedOut(event.getEntity());
        mc.smpessentials.teleport.TeleportService.clearForPlayer(event.getEntity().getUUID());
        mc.smpessentials.antixray.AntiXrayEngine.onPlayerDisconnect(event.getEntity().getUUID());
        mc.smpessentials.dashboard.DashboardManager.broadcastPlayerLeave(event.getEntity().getGameProfile().name());
    }

    // Order matters: hardcore must run first. See SmpUtilsModFabric for explanation.
    @SubscribeEvent
    public static void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            mc.smpessentials.hardcore.HardcoreSavedData.get(((net.minecraft.server.level.ServerLevel) sp.level()).getServer()).onPlayerDeath(sp);
            mc.smpessentials.keepinv.KeepInvSavedData.onPlayerDeath(sp);
        } else if (event.getEntity() instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon
                && dragon.level() instanceof net.minecraft.server.level.ServerLevel end) {
            mc.smpessentials.hardcore.HardcoreSavedData.get(end.getServer()).onDragonKilled(end);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            mc.smpessentials.hardcore.HardcoreSavedData.get(((net.minecraft.server.level.ServerLevel) sp.level()).getServer()).onPlayerRespawn(sp);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MessageScheduler.onServerTick(event.getServer());
        mc.smpessentials.bluemap.BlueMapIntegration.onServerTick(event.getServer());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            TeleportScheduler.onPlayerTick(player);
            SkillEvents.onPlayerTick(player);
            mc.smpessentials.dims.EtherFallthrough.tick(player);
            mc.smpessentials.antixray.AntiXrayEngine.tickPlayer(player);
            mc.smpessentials.teams.TeamAutoAssign.tick(player);
            mc.smpessentials.hardcore.HardcoreSavedData hc =
                    mc.smpessentials.hardcore.HardcoreSavedData.get(event.getServer());
            hc.tickSpectatorHud(player);
            hc.tickEndEntry(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            mc.smpessentials.teams.TeamAutoAssign.onDimensionChange(player, event.getTo());
        }
    }

    // Scout Zoom activation is handled by PlayerActionMixin (common module)

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        Component decorated = ChatFilter.onDecorate(event.getPlayer(), event.getMessage());
        if (decorated != null) {
            // Muted: ChatFilter returns Component.empty() for blocked messages
            if (decorated.getString().isEmpty() && !event.getMessage().getString().isEmpty()) {
                event.setCanceled(true);
                return; // don't forward blocked messages to the dashboard or Discord
            }
            event.setMessage(decorated);
        }
        // Broadcast the final message (decorated if filtered, original if not)
        String finalMessage = decorated != null ? decorated.getString() : event.getMessage().getString();
        mc.smpessentials.dashboard.DashboardManager.broadcastChat(
                event.getPlayer().getGameProfile().name(),
                finalMessage);
    }

    @SubscribeEvent
    public static void onBlockBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && event.getLevel() instanceof net.minecraft.world.level.Level level) {
            SkillEvents.onBlockBreak(level, event.getPos(), event.getState(), player);
            if (!ClaimProtection.onBlockBreak(level, event.getPos(), event.getState(), player)) {
                event.setCanceled(true);
            } else if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                mc.smpessentials.shops.ShopService.onBlockBreak(sl, event.getPos());
                mc.smpessentials.antixray.AntiXrayEngine.revealNeighbors(sl, event.getPos());
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            InteractionResult portal = mc.smpessentials.dims.CustomPortalActivator.onRightClickBlock(
                    player, event.getLevel(), event.getHand(), event.getPos(), event.getFace());
            if (portal == InteractionResult.SUCCESS) {
                event.setCanceled(true);
                return;
            }
            // Shop check runs before claim check so non-owners can buy from shops in claims
            // Only process main hand to avoid double-firing (opens GUI then immediately replaces it)
            if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
                InteractionResult shop = mc.smpessentials.shops.ShopService.onRightClickBlock(
                        player, event.getLevel(), event.getPos());
                if (shop == InteractionResult.SUCCESS) {
                    event.setCanceled(true);
                    return;
                }
            }
            if (ClaimProtection.onRightClickBlock(player, event.getPos()) == InteractionResult.FAIL) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!SpawnProtection.onInteractEntity(player, event.getTarget())) {
                event.setCanceled(true);
                return;
            }
            if (!ClaimProtection.onInteractEntity(player, event.getTarget())) {
                event.setCanceled(true);
            }
        }
    }
}
