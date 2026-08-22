package mc.smpessentials.antixray;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.UUID;

/**
 * Server-side ore obfuscation engine.
 *
 * Replaces hidden stone, deepslate, netherrack and ore blocks in outgoing chunk packets with the
 * plain base material for their depth, so x-ray clients see uniform rock and no ore locations.
 * {@link ObfuscatedBlocks} owns which blocks are targeted and what they are shown as;
 * {@link ChunkNeighborhood} owns the "is this block fully enclosed" test. Real block states come
 * back via block-break reveal, proximity reveal and normal chunk re-sends, so an exposed ore
 * reads as a natural stone-to-ore transition.
 */
public final class AntiXrayEngine {
    private AntiXrayEngine() {}

    private static final int REVEAL_RADIUS = 2;

    private static final RevealedPositions REVEALED = new RevealedPositions();

    /**
     * Builds an obfuscated copy of the chunk's section buffer. Called from
     * {@link mc.smpessentials.mixin.ChunkPacketDataMixin} during chunk packet construction.
     * Returns null if nothing was obfuscated, leaving the vanilla buffer in place.
     */
    public static byte[] obfuscate(LevelChunk chunk) {
        if (!SmpConfig.ANTIXRAY_ENABLED) return null;

        Level level = chunk.getLevel();
        if (level.isClientSide()) return null;

        LevelChunkSection[] sections = chunk.getSections();
        LevelChunkSection[] masked = maskHiddenBlocks(chunk, sections);
        return masked == null ? null : serialize(sections, masked);
    }

    /**
     * Scans every section for hidden target blocks. Returns an array parallel to {@code sections}
     * holding a masked copy for each section that changed and null elsewhere, or null when the
     * chunk needs no obfuscation at all. Sections are only copied once they are known to change,
     * so a chunk full of exposed terrain costs no allocation.
     */
    private static LevelChunkSection[] maskHiddenBlocks(LevelChunk chunk, LevelChunkSection[] sections) {
        boolean nether = chunk.getLevel().dimension() == Level.NETHER;
        int minSectionY = chunk.getMinSectionY();
        int baseX = chunk.getPos().x() << 4;
        int baseZ = chunk.getPos().z() << 4;
        LevelChunkSection[] masked = null;
        // Built on first use: with the section test below, plenty of chunks never need it.
        ChunkNeighborhood neighborhood = null;

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir()) continue;

            // Sections are 16 aligned and maskFor only branches on the sign of worldY, so one
            // section always paints one mask.
            int baseY = (minSectionY + i) << 4;
            BlockState mask = ObfuscatedBlocks.maskFor(baseY, nether);

            // The palette test skips whole sections that cannot change: no target block, or only
            // the base material this section would paint anyway. Neither visits any of the 4096
            // blocks. That covers the sky, the surface, and plain stone or netherrack at depth.
            if (!section.maybeHas(ObfuscatedBlocks.predicateExcluding(mask))) continue;

            LevelChunkSection copy = null;

            // Nested in storage order: Strategy.getIndex is (y << 4 | z) << 4 | x, so x varies
            // fastest and this walks the bit storage sequentially.
            for (int y = 0; y < 16; y++) {
                int worldY = baseY + y;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        // Already the mask, so masking it would write the state over itself.
                        // Stone, deepslate and netherrack have no properties, so each has exactly
                        // one state and reference equality is the whole test.
                        if (state == mask) continue;
                        if (!ObfuscatedBlocks.matches(state)) continue;

                        if (neighborhood == null) neighborhood = ChunkNeighborhood.around(chunk);
                        if (!neighborhood.isEnclosed(baseX + x, worldY, baseZ + z)) continue;

                        if (copy == null) {
                            copy = section.copy();
                            if (masked == null) masked = new LevelChunkSection[sections.length];
                            masked[i] = copy;
                        }
                        copy.setBlockState(x, y, z, mask, false);
                    }
                }
            }
        }
        return masked;
    }

    /** Writes the section buffer the client will read, taking the masked copy where one exists. */
    private static byte[] serialize(LevelChunkSection[] sections, LevelChunkSection[] masked) {
        int capacity = 0;
        for (int i = 0; i < sections.length; i++) {
            capacity += sectionToSend(sections, masked, i).getSerializedSize();
        }

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(capacity));
        try {
            for (int i = 0; i < sections.length; i++) {
                sectionToSend(sections, masked, i).write(buf);
            }
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        } finally {
            buf.release();
        }
    }

    private static LevelChunkSection sectionToSend(LevelChunkSection[] sections,
                                                   LevelChunkSection[] masked, int index) {
        return masked[index] != null ? masked[index] : sections[index];
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
     * Sends real block states for any hidden target block within {@link #REVEAL_RADIUS}. There is
     * no movement gate, so a stationary miner still gets the reveal before breaking, e.g. when
     * digging straight down; {@link RevealedPositions} is what keeps it from re-sending.
     */
    public static void tickPlayer(ServerPlayer player) {
        if (!SmpConfig.ANTIXRAY_ENABLED) return;

        ServerLevel level = (ServerLevel) player.level();
        BlockPos center = player.blockPosition();
        LongOpenHashSet revealed = REVEALED.forPlayer(player.getUUID());

        // X and Z outermost so every Y in a column shares one chunk, keeping the neighbourhood
        // built at most a handful of times for the whole box.
        ChunkNeighborhood neighborhood = null;
        long neighborhoodKey = Long.MIN_VALUE;

        for (int x = center.getX() - REVEAL_RADIUS; x <= center.getX() + REVEAL_RADIUS; x++) {
            for (int z = center.getZ() - REVEAL_RADIUS; z <= center.getZ() + REVEAL_RADIUS; z++) {
                int chunkX = x >> 4;
                int chunkZ = z >> 4;
                long chunkKey = ChunkPos.pack(chunkX, chunkZ);
                if (chunkKey != neighborhoodKey) {
                    neighborhoodKey = chunkKey;
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    neighborhood = chunk == null ? null : ChunkNeighborhood.around(chunk);
                }
                if (neighborhood == null) continue;

                for (int y = center.getY() - REVEAL_RADIUS; y <= center.getY() + REVEAL_RADIUS; y++) {
                    reveal(player, neighborhood, revealed, x, y, z);
                }
            }
        }
    }

    /** Sends one position's real state if it is a target block that is currently hidden. */
    private static void reveal(ServerPlayer player, ChunkNeighborhood neighborhood,
                               LongOpenHashSet revealed, int x, int y, int z) {
        long packed = BlockPos.asLong(x, y, z);
        if (revealed.contains(packed)) return;

        BlockState state = neighborhood.stateAt(x, y, z);
        if (state == null || !ObfuscatedBlocks.matches(state)) return;
        if (!neighborhood.isEnclosed(x, y, z)) return;

        player.connection.send(new ClientboundBlockUpdatePacket(new BlockPos(x, y, z), state));
        revealed.add(packed);
    }

    public static void onPlayerDisconnect(UUID uuid) {
        REVEALED.forget(uuid);
    }
}
