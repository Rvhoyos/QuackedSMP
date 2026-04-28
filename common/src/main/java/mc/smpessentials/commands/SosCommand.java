package mc.smpessentials.commands;

import mc.smpessentials.claims.storage.ClaimedSavedData;
import mc.smpessentials.claims.storage.WhitelistSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.TeleportTransition;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

/**
 * Implements the {@code /sos} command. Teleports any untrusted player currently standing
 * in the caller's claimed chunks to their respawn point (or world spawn as fallback).
 * OPs and spectators are never ejected. Iterates online players rather than all claims
 * since the player list is expected to be smaller.
 */
public final class SosCommand {
    private SosCommand() {
    }

    /**
     * Ejects all untrusted players from the caller's claims.
     *
     * @return 1 always (even if no one was ejected).
     */
    public static int execute(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer caller)) {
            source.sendFailure(Component.literal("Only players can use /sos."));
            return 0;
        }

        UUID callerId = caller.getUUID();

        // Performance note: Iterating players (O(N)) is significantly faster than
        // iterating all server claims (O(M)).
        // We only care about players currently online and standing in chunks.
        List<ServerPlayer> players = source.getServer().getPlayerList().getPlayers();
        int ejectedCount = 0;

        for (ServerPlayer target : players) {
            if (target.getUUID().equals(callerId))
                continue; // Don't eject self
            if (target.isSpectator())
                continue; // Ignore spectators
            if (((ServerLevel) target.level()).getServer().getPlayerList().isOp(target.nameAndId()))
                continue; // Ignore OPs

            // Check if target is in a chunk owned by caller
            ServerLevel targetLevel = (ServerLevel) target.level();
            ChunkPos targetChunk = ChunkPos.containing(target.blockPosition());

            Optional<mc.smpessentials.claims.model.ClaimData> claimData = ClaimedSavedData.get(targetLevel)
                    .getClaim(targetLevel, targetChunk);

            if (claimData.isPresent() && claimData.get().owner().equals(callerId)) {
                // It is caller's claim. Check trust in that dimension.
                boolean isTrusted = WhitelistSavedData.get(targetLevel).isTrusted(callerId, target.getUUID());

                if (!isTrusted) {
                    ejectPlayer(target);
                    // Notify caller
                    caller.sendSystemMessage(
                            Component.literal("Ejected " + target.getName().getString() + " from your claim."));
                    // Notify target
                    target.sendSystemMessage(Component.literal(
                            "You were ejected from " + caller.getName().getString() + "'s claim via /sos."), true);
                    ejectedCount++;
                }
            }
        }

        if (ejectedCount == 0) {
            source.sendSystemMessage(Component.literal("No untrusted players found in your claims."));
        }

        return 1;
    }

    private static void ejectPlayer(ServerPlayer player) {
        // Try bed/respawn anchor first
        if (player.getRespawnConfig() != null) {
            TeleportTransition transition = player.findRespawnPositionAndUseSpawnBlock(false, p -> {
            });
            if (transition != null) {
                player.teleport(transition);
                return;
            }
        }

        // Fallback to world spawn (Overworld)
        // We use overworld spawn to ensure they aren't stuck in a dangerous nether/end
        // spawn if they have no bed.
        ServerLevel overworld = ((ServerLevel) player.level()).getServer().overworld();
        BlockPos spawn = overworld.getRespawnData().pos();

        // TeleportTransition requires a Vec3 for position and float for rotation
        player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, Set.of(), player.getYRot(),
                player.getXRot(), false);
    }
}
