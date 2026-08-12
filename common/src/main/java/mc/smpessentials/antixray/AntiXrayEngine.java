package mc.smpessentials.antixray;

import io.netty.buffer.Unpooled;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side ore obfuscation engine.
 *
 * Replaces hidden stone, deepslate, netherrack, and ore blocks in outgoing chunk packets
 * with the plain base material for their depth (stone, deepslate, or netherrack) so x-ray
 * clients see uniform rock and no ore locations. Only blocks in {@link #OBFUSCATABLE} are
 * targeted; everything else (dirt, gravel, granite, etc.) is left untouched. A block is
 * considered hidden when all 6 neighbor faces pointing toward it are sturdy (full square
 * faces). Real block states are restored via block-break reveal, proximity reveal, and
 * normal chunk re-sends, so an exposed ore reads as a natural stone-to-ore transition.
 */
public final class AntiXrayEngine {
    private AntiXrayEngine() {}

    private static final int REVEAL_RADIUS = 2;
    private static final int MAX_REVEALED_PER_PLAYER = 10_000;

    // Per-player tracking for proximity reveal to avoid redundant packets.
    private static final Map<UUID, BlockPos> lastPositions = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<Long>> revealedBlocks = new ConcurrentHashMap<>();

    private static final BlockState[] OVERWORLD_ORES = {
            Blocks.STONE.defaultBlockState(),
            Blocks.DIAMOND_ORE.defaultBlockState(),
            Blocks.IRON_ORE.defaultBlockState(),
            Blocks.GOLD_ORE.defaultBlockState(),
            Blocks.COAL_ORE.defaultBlockState(),
            Blocks.EMERALD_ORE.defaultBlockState(),
            Blocks.LAPIS_ORE.defaultBlockState(),
            Blocks.COPPER_ORE.defaultBlockState(),
            Blocks.REDSTONE_ORE.defaultBlockState(),
    };

    private static final BlockState[] DEEP_ORES = {
            Blocks.DEEPSLATE.defaultBlockState(),
            Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_COAL_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState(),
    };

    private static final BlockState[] NETHER_ORES = {
            Blocks.NETHERRACK.defaultBlockState(),
            Blocks.NETHER_GOLD_ORE.defaultBlockState(),
            Blocks.NETHER_QUARTZ_ORE.defaultBlockState(),
            Blocks.ANCIENT_DEBRIS.defaultBlockState(),
    };

    // Only these blocks get replaced when hidden. Everything else (dirt, gravel, granite, etc.)
    // is left alone so the underground looks natural and only ores are masked.
    private static final Set<Block> OBFUSCATABLE = Set.of(
            Blocks.STONE,
            Blocks.DEEPSLATE,
            Blocks.NETHERRACK,
            Blocks.DIAMOND_ORE,
            Blocks.IRON_ORE,
            Blocks.GOLD_ORE,
            Blocks.COAL_ORE,
            Blocks.EMERALD_ORE,
            Blocks.LAPIS_ORE,
            Blocks.COPPER_ORE,
            Blocks.REDSTONE_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.DEEPSLATE_IRON_ORE,
            Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.DEEPSLATE_COAL_ORE,
            Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.NETHER_GOLD_ORE,
            Blocks.NETHER_QUARTZ_ORE,
            Blocks.ANCIENT_DEBRIS
    );

    /**
     * Builds an obfuscated copy of the chunk's section buffer. Called from
     * {@link mc.smpessentials.mixin.ChunkPacketDataMixin} during chunk packet construction.
     * Returns null if nothing was obfuscated.
     */
    public static byte[] obfuscate(LevelChunk chunk) {
        if (!SmpConfig.ANTIXRAY_ENABLED) return null;

        Level level = chunk.getLevel();
        if (level.isClientSide()) return null;

        boolean isNether = level.dimension() == Level.NETHER;
        ChunkPos chunkPos = chunk.getPos();
        LevelChunkSection[] sections = chunk.getSections();
        int minSectionY = chunk.getMinSectionY();
        int baseX = chunkPos.x() << 4;
        int baseZ = chunkPos.z() << 4;
        boolean modified = false;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection section = sections[i];
                if (section.hasOnlyAir()) {
                    section.write(buf);
                    continue;
                }

                int baseY = (minSectionY + i) << 4;
                LevelChunkSection copy = section.copy();
                boolean sectionModified = false;

                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (!OBFUSCATABLE.contains(state.getBlock())) continue;

                            int wx = baseX + x;
                            int wy = baseY + y;
                            int wz = baseZ + z;
                            if (isHidden(level, wx, wy, wz)) {
                                copy.setBlockState(x, y, z,
                                        replacement(wx, wy, wz, isNether), false);
                                sectionModified = true;
                            }
                        }
                    }
                }
                (sectionModified ? copy : section).write(buf);
                modified |= sectionModified;
            }

            if (!modified) {
                return null;
            }

            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        } finally {
            buf.release();
        }
    }

    /**
     * Sends real block states for the 6 neighbors of a broken block to all players
     * tracking that chunk. Called from platform block-break event handlers.
     */
    public static void revealNeighbors(ServerLevel level, BlockPos pos) {
        if (!SmpConfig.ANTIXRAY_ENABLED) return;

        ClientboundBlockUpdatePacket[] packets = new ClientboundBlockUpdatePacket[Direction.values().length];
        for (int i = 0; i < packets.length; i++) {
            BlockPos neighbor = pos.relative(Direction.values()[i]);
            packets[i] = new ClientboundBlockUpdatePacket(neighbor, level.getBlockState(neighbor));
        }

        int viewDist = level.getServer().getPlayerList().getViewDistance();
        ChunkPos cp = ChunkPos.containing(pos);
        for (ServerPlayer player : level.players()) {
            if (player.chunkPosition().getChessboardDistance(cp) <= viewDist) {
                for (ClientboundBlockUpdatePacket packet : packets) {
                    player.connection.send(packet);
                }
            }
        }
    }

    /**
     * Proximity reveal. Called once per server tick per player from both platform tick loops.
     * When a player moves to a new block position, sends real block states for any hidden
     * obfuscatable blocks within {@link #REVEAL_RADIUS}.
     */
    public static void tickPlayer(ServerPlayer player) {
        if (!SmpConfig.ANTIXRAY_ENABLED) return;

        BlockPos currentPos = player.blockPosition();
        UUID uuid = player.getUUID();
        // No movement gate: proximity reveal runs every tick (deduped by the revealed set) so
        // stationary miners still get pre-reveal before breaking, e.g. when digging straight down.
        lastPositions.put(uuid, currentPos);

        Set<Long> revealed = revealedBlocks.computeIfAbsent(uuid, k -> new HashSet<>());
        if (revealed.size() > MAX_REVEALED_PER_PLAYER) {
            revealed.clear();
        }

        ServerLevel level = (ServerLevel) player.level();
        int cx = currentPos.getX();
        int cy = currentPos.getY();
        int cz = currentPos.getZ();

        for (int dx = -REVEAL_RADIUS; dx <= REVEAL_RADIUS; dx++) {
            for (int dy = -REVEAL_RADIUS; dy <= REVEAL_RADIUS; dy++) {
                for (int dz = -REVEAL_RADIUS; dz <= REVEAL_RADIUS; dz++) {
                    int wx = cx + dx;
                    int wy = cy + dy;
                    int wz = cz + dz;
                    long packed = BlockPos.asLong(wx, wy, wz);

                    if (revealed.contains(packed)) continue;

                    BlockPos pos = new BlockPos(wx, wy, wz);
                    if (!level.isLoaded(pos)) continue;

                    BlockState state = level.getBlockState(pos);
                    if (!OBFUSCATABLE.contains(state.getBlock())) continue;

                    if (isHidden(level, wx, wy, wz)) {
                        player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
                        revealed.add(packed);
                    }
                }
            }
        }
    }

    public static void onPlayerDisconnect(UUID uuid) {
        lastPositions.remove(uuid);
        revealedBlocks.remove(uuid);
    }

    private static boolean isHidden(Level level, int worldX, int worldY, int worldZ) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = new BlockPos(worldX + dir.getStepX(),
                    worldY + dir.getStepY(), worldZ + dir.getStepZ());
            if (!level.isLoaded(neighbor)) return false;
            // Check the neighbor's face pointing TOWARDS our block. Unlike canOcclude() which
            // is just a property flag, isFaceSturdy checks the actual block shape — fences,
            // carpets, slabs, stairs etc. correctly count as not covering.
            if (!level.getBlockState(neighbor).isFaceSturdy(level, neighbor, dir.getOpposite())) return false;
        }
        return true;
    }

    private static BlockState replacement(int x, int y, int z, boolean nether) {
        // Stone-mode: hide every obfuscatable block as the plain base material for its depth.
        // Break/proximity reveal then flickers stone -> the real block, which reads as natural
        // (unlike fake-ore -> stone, which looks like a glitch). Also leaks no ore info to xrayers.
        // The *_ORES palettes are kept for a possible fake-ore mode revisit.
        if (nether) {
            return Blocks.NETHERRACK.defaultBlockState();
        } else if (y < 0) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }
}
