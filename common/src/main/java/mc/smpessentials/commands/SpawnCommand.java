package mc.smpessentials.commands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * /spawn — Teleports the player to the world's main spawn point.
 *
 * Uses 1.21.8's teleport signature and restricts execution to players only.
 */
public final class SpawnCommand {
    private SpawnCommand() {}

    public static int execute(CommandSourceStack source) {
        // ---- SAFETY CHECK ----
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use /spawn."));
            return 0;
        }

        MinecraftServer server = player.getServer();
        ServerLevel overworld = server.overworld();

        // Use world’s shared spawn position
        BlockPos spawn = overworld.getSharedSpawnPos();
        BlockPos safe = player.adjustSpawnLocation(overworld, spawn);

        boolean ok = player.teleportTo(
            overworld,
            safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
            Set.of(), player.getYRot(), player.getXRot(), false
        );

        if (ok) {
            player.sendSystemMessage(Component.literal("Teleported to spawn."));
            return 1;
        } else {
            player.sendSystemMessage(Component.literal("Failed to teleport to spawn."));
            return 0;
        }
    }
}
