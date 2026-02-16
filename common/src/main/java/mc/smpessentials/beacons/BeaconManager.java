package mc.smpessentials.beacons;

import mc.smpessentials.config.SmpConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.ChunkPos;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BeaconManager {
    private static final BeaconManager INSTANCE = new BeaconManager();
    // Map<DimensionID, Map<BlockPos, BeaconInfo>>
    private final Map<String, Map<BlockPos, BeaconInfo>> activeBeacons = new ConcurrentHashMap<>();

    private BeaconManager() {
    }

    public static BeaconManager get() {
        return INSTANCE;
    }

    public void register(BlockPos pos, String dimensionId, BeaconInfo info) {
        activeBeacons.computeIfAbsent(dimensionId, k -> new ConcurrentHashMap<>()).put(pos, info);
    }

    public void unregister(BlockPos pos, String dimensionId) {
        if (activeBeacons.containsKey(dimensionId)) {
            activeBeacons.get(dimensionId).remove(pos);
        }
    }

    public void tick(MinecraftServer server) {
        if (!SmpConfig.ENABLE_CUSTOM_BEACONS)
            return;
        if (server.getTickCount() % 80 != 0)
            return; // Run every 4 seconds (matches vanilla beacon tick)

        // Cleanup invalid beacons and apply effects
        String[] dimIds = activeBeacons.keySet().toArray(new String[0]);

        for (String dimId : dimIds) {
            Map<BlockPos, BeaconInfo> beacons = activeBeacons.get(dimId);
            if (beacons == null || beacons.isEmpty())
                continue;

            // We need a server level to check for blocks
            net.minecraft.server.level.ServerLevel level = null;
            for (net.minecraft.server.level.ServerLevel world : server.getAllLevels()) {
                if (world.dimension().location().toString().equals(dimId)) {
                    level = world;
                    break;
                }
            }

            if (level == null)
                continue; // Dimension not loaded?

            // Use an iterator to safely remove items while iterating
            java.util.Iterator<Map.Entry<BlockPos, BeaconInfo>> iterator = beacons.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<BlockPos, BeaconInfo> entry = iterator.next();
                BlockPos pos = entry.getKey();
                // BeaconInfo info = entry.getValue(); // Unused

                // Validation: Check if chunk is loaded first to avoid generating chunks
                if (level.isLoaded(pos)) {
                    // If loaded, verify it is still a beacon and has power
                    net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
                    if (!(be instanceof net.minecraft.world.level.block.entity.BeaconBlockEntity)) {
                        // Beacon is gone (broken/replaced)
                        iterator.remove();
                        continue;
                    }
                }
                // If chunk is unloaded, we keep the beacon active (persistence)
                // This matches vanilla behavior where effects persist if you are in range,
                // even if the beacon chunk itself is unloaded (usually).
                // Actually, our custom range is huge, so we treat it as "active if registered".
            }
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            applyEffectsToPlayer(player);
        }
    }

    private void applyEffectsToPlayer(ServerPlayer player) {
        String dimId = player.level().dimension().location().toString();
        if (!activeBeacons.containsKey(dimId))
            return;

        ChunkPos playerChunk = player.chunkPosition();

        for (BeaconInfo beacon : activeBeacons.get(dimId).values()) {
            // Check chunk range
            int radius = beacon.tier().getChunkRadius();
            ChunkPos beaconChunk = new ChunkPos(beacon.pos());

            // Check if player is within the chunk grid relative to beacon
            if (Math.abs(playerChunk.x - beaconChunk.x) <= radius &&
                    Math.abs(playerChunk.z - beaconChunk.z) <= radius) {

                // specific logic: Apply effects
                int duration = 300; // 15 seconds

                if (beacon.primary() != null) {
                    // Check for secondary promotion (if primary == secondary, amp++)
                    // For Tier 4 (Netherite/Diamond with Lvl 4 base), vanilla promotes primary to
                    // II
                    // We'll mimic this: if tier >= DIAMOND and primary == secondary
                    int primaryAmp = 0;
                    if (beacon.tier().level >= 3 && beacon.primary().equals(beacon.secondary())) {
                        primaryAmp = 1;
                    }

                    player.addEffect(new MobEffectInstance(beacon.primary(), duration, primaryAmp, true, true));
                }

                if (beacon.secondary() != null && !beacon.secondary().equals(beacon.primary())) {
                    player.addEffect(new MobEffectInstance(beacon.secondary(), duration, 0, true, true));
                }
            }
        }
    }
}
