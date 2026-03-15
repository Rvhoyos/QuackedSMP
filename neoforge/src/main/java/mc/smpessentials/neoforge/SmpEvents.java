package mc.smpessentials.neoforge;

import mc.smpessentials.commands.CommandRegistrar;
import mc.smpessentials.chatfilter.ChatFilter;
import mc.smpessentials.events.JoinMessageHandler;
import mc.smpessentials.events.MessageScheduler;
import mc.smpessentials.skills.SkillEvents;
import mc.smpessentials.teleport.TeleportScheduler;
import mc.smpessentials.claims.ClaimProtection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
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
    public static void onServerStarted(ServerStartedEvent event) {
        ChatFilter.onServerStart(event.getServer());
        mc.smpessentials.bluemap.BlueMapIntegration.onServerStart(event.getServer());
        mc.smpessentials.keepinv.KeepInvSavedData.enforceGamerule(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        mc.smpessentials.commands.EndResetLogic.onServerStopping(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JoinMessageHandler.onPlayerJoin(player);
            SkillEvents.onPlayerJoin(player);
            mc.smpessentials.punish.PunishManager pm =
                    mc.smpessentials.punish.PunishManager.get(((net.minecraft.server.level.ServerLevel) player.level()).getServer());
            if (pm.isPending(player.getUUID())) {
                pm.punish(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SkillEvents.onPlayerLoggedOut(event.getEntity());
        mc.smpessentials.teleport.TeleportService.clearForPlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            mc.smpessentials.keepinv.KeepInvSavedData.onPlayerDeath(sp);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MessageScheduler.onServerTick(event.getServer());
        mc.smpessentials.bluemap.BlueMapIntegration.onServerTick(event.getServer());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            TeleportScheduler.onPlayerTick(player);
            SkillEvents.onPlayerTick(player);
        }
    }

    // Scout Zoom activation is handled by PlayerActionMixin (common module)

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        Component decorated = ChatFilter.onDecorate(event.getPlayer(), event.getMessage());
        if (decorated != null) {
            // Check if it was cancelled by being empty (muted logic in common returns
            // Component.empty())
            if (decorated.getString().isEmpty() && !event.getMessage().getString().isEmpty()) {
                event.setCanceled(true);
            } else {
                event.setMessage(decorated);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && event.getLevel() instanceof net.minecraft.world.level.Level level) {
            SkillEvents.onBlockBreak(level, event.getPos(), event.getState(), player);
            if (!ClaimProtection.onBlockBreak(level, event.getPos(), event.getState(), player)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (ClaimProtection.onRightClickBlock(player, event.getPos()) == InteractionResult.FAIL) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!ClaimProtection.onInteractEntity(player, event.getTarget())) {
                event.setCanceled(true);
            }
        }
    }
}
