package mc.smpessentials.commands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

// Implements /home. Teleports to bed/respawn anchor after warmup, falls back to world spawn.
public final class HomeCommand {
    private HomeCommand() {
    }

    public static int execute(CommandSourceStack source) {
        if (mc.smpessentials.hardcore.HardcoreSavedData.denyIfInSession(source)) return 0;
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use /home."));
            return 0;
        }

        if (player.getRespawnConfig() != null) {
            mc.smpessentials.teleport.TeleportScheduler.schedule(player, () -> {
                TeleportTransition transition = player.findRespawnPositionAndUseSpawnBlock(false, p -> {
                });
                if (transition != null) {
                    player.teleport(transition);
                    player.sendSystemMessage(Component.literal("Teleported to your respawn point."), false);
                } else {
                    player.sendSystemMessage(Component.literal("Could not find respawn point."), false);
                }
            });
            return 1;
        }

        // Fallback to world spawn
        ServerLevel level = source.getLevel();
        BlockPos spawn = level.getRespawnData().pos();
        Vec3 target = new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);

        mc.smpessentials.teleport.TeleportScheduler.schedule(player, () -> {
            player.teleportTo(level, target.x, target.y, target.z, Set.of(), player.getYRot(), player.getXRot(), false);
            player.sendSystemMessage(Component.literal("No bed/anchor set. Teleported to world spawn."), false);
        });
        return 1;
    }
}
