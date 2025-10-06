package mc.smpessentials.commands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public final class HomeCommand {
    private HomeCommand() {}

    public static int execute(CommandSourceStack source) {
        // Safety: registration already ensures this is a player, but keep a cheap check.
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            // console / command block -> fail cleanly
            source.sendFailure(Component.literal("Only players can use /home."));
            return 0;
        }

        // Prefer the player's bed/respawn-anchor if set.
        if (player.getRespawnConfig() != null) {
            TeleportTransition transition = player.findRespawnPositionAndUseSpawnBlock(false, p -> {});
            player.teleport(transition);
            source.sendSuccess(() -> Component.literal("Teleported to your respawn point."), false);
            return 1;
        }

        // Fallback to world spawn
        ServerLevel level = source.getLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        Vec3 target = new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);

        // Signature: teleportTo(ServerLevel, x, y, z, Set<Relative>, yaw, pitch, setCamera)
        player.teleportTo(level, target.x, target.y, target.z, Set.of(), player.getYRot(), player.getXRot(), false);
        source.sendSuccess(() -> Component.literal("No bed/anchor set. Teleported to world spawn."), false);
        return 1;
    }
}
