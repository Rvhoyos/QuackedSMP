package mc.smpessentials.punish;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mc.smpessentials.claims.storage.ClaimedSavedData;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/**
 * Manages player punishments, specifically clearing inventories and containers in claimed chunks.
 * Handles both immediate punishments for online players and queued punishments for offline players.
 * 
 * <p>This system avoids direct NBT file manipulation for offline players by using a join-listener 
 * approach, ensuring player data is modified only when safely loaded by the server.</p>
 */
public final class PunishManager extends SavedData {
    private final Set<UUID> pendingPunishments;

    /**
     * Codec for serializing the pending punishment list to NBT.
     */
    public static final Codec<PunishManager> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.listOf().fieldOf("pending").forGetter(s -> new ArrayList<>(s.pendingPunishments))
    ).apply(i, list -> new PunishManager(new HashSet<>(list))));

    /**
     * SavedDataType for registering PunishManager with Minecraft's data storage system.
     */
    public static final SavedDataType<PunishManager> TYPE = new SavedDataType<>(
            "quackedsmp_punishments",
            () -> new PunishManager(new HashSet<>()),
            PunishManager.CODEC,
            DataFixTypes.LEVEL
    );

    private PunishManager(Set<UUID> pending) {
        this.pendingPunishments = pending;
    }

    /**
     * Retrieves the PunishManager instance from the server's overworld data storage.
     * 
     * @param server The Minecraft server instance.
     * @return The singleton PunishManager instance.
     */
    public static PunishManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Queues a punishment for a player to be applied on their next login.
     * 
     * @param uuid The UUID of the player to punish.
     */
    public void queuePunishment(UUID uuid) {
        if (pendingPunishments.add(uuid)) {
            setDirty();
        }
    }

    /**
     * Checks if a player has a pending punishment.
     * 
     * @param uuid The UUID of the player to check.
     * @return true if a punishment is queued, false otherwise.
     */
    public boolean isPending(UUID uuid) {
        return pendingPunishments.contains(uuid);
    }

    /**
     * Removes a player from the pending punishment list.
     * 
     * @param uuid The UUID of the player to remove.
     */
    public void removePending(UUID uuid) {
        if (pendingPunishments.remove(uuid)) {
            setDirty();
        }
    }

    /**
     * Executes a full punishment on an online player.
     * This clears:
     * 1. Personal inventory (including armor and offhand).
     * 2. Ender chest contents.
     * 3. All containers (Chests, Shulkers, etc.) in chunks owned by the player.
     * 
     * @param player The ServerPlayer instance to punish.
     */
    public void punish(ServerPlayer player) {
        UUID uuid = player.getUUID();
        MinecraftServer server = ((ServerLevel) player.level()).getServer();

        // 1. Clear Inventory and Ender Chest
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
        
        // Ensure changes are synced to the client immediately
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();

        // 2. Clear all containers in chunks owned by this player across ALL dimensions.
        // Safety note: This filter strictly checks 'owner()'. If the punished player is 
        // merely trusted on another player's claim, those containers are NOT cleared.
        for (ServerLevel level : server.getAllLevels()) {
            ClaimedSavedData.get(level).listClaims(level).stream()
                    .filter(claim -> claim.owner().equals(uuid))
                    .forEach(claim -> clearContainersInChunk(level, new ChunkPos(claim.chunk())));
        }

        removePending(uuid);
    }

    /**
     * Iterates through all block entities in a specified chunk and wipes any containers.
     * 
     * @param level The dimension the chunk resides in.
     * @param chunkPos The position of the chunk to clear.
     */
    private void clearContainersInChunk(ServerLevel level, ChunkPos chunkPos) {
        var chunk = level.getChunk(chunkPos.x, chunkPos.z);
        if (chunk == null) return;
        
        // Use a copy of the block entity values to avoid concurrent modification issues
        for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
            if (be instanceof Clearable clearable) {
                clearable.clearContent();
                be.setChanged();
                // Notify nearby clients of the block's current empty state
                level.getChunkSource().blockChanged(be.getBlockPos());
            }
        }
        chunk.markUnsaved();
    }
}
