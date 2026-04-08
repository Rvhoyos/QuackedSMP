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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class AntiXrayEngine {
    private AntiXrayEngine() {}

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

    // Builds an obfuscated chunk buffer to replace the packet's real data.
    // Returns null if anti-xray is disabled, client-side, or nothing was hidden.
    public static byte[] obfuscate(LevelChunk chunk) {
        if (!SmpConfig.ANTIXRAY_ENABLED) return null;

        Level level = chunk.getLevel();
        if (level.isClientSide()) return null;

        boolean isNether = level.dimension() == Level.NETHER;
        ChunkPos chunkPos = chunk.getPos();
        LevelChunkSection[] sections = chunk.getSections();
        int minSectionY = chunk.getMinSectionY();
        boolean modified = false;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection section = sections[i];
                if (section.hasOnlyAir()) {
                    section.write(buf);
                    continue;
                }

                int sectionY = minSectionY + i;
                int baseY = sectionY << 4;
                int baseX = chunkPos.x << 4;
                int baseZ = chunkPos.z << 4;
                LevelChunkSection copy = section.copy();
                boolean sectionModified = false;

                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (state.isAir()) continue;
                            if (state.hasBlockEntity()) continue;

                            if (isHidden(sections, level, i, baseY, x, y, z, chunkPos)) {
                                copy.setBlockState(x, y, z,
                                        replacement(baseX | x, baseY | y, baseZ | z, isNether), false);
                                sectionModified = true;
                            }
                        }
                    }
                }
                (sectionModified ? copy : section).write(buf);
                modified |= sectionModified;
            }

            if (!modified) return null;

            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        } finally {
            buf.release();
        }
    }

    // Sends real block states for the 6 neighbors of a broken block to all tracking players.
    public static void revealNeighbors(ServerLevel level, BlockPos pos) {
        if (!SmpConfig.ANTIXRAY_ENABLED) return;

        ClientboundBlockUpdatePacket[] packets = new ClientboundBlockUpdatePacket[Direction.values().length];
        for (int i = 0; i < packets.length; i++) {
            BlockPos neighbor = pos.relative(Direction.values()[i]);
            packets[i] = new ClientboundBlockUpdatePacket(neighbor, level.getBlockState(neighbor));
        }

        int viewDist = level.getServer().getPlayerList().getViewDistance();
        ChunkPos cp = new ChunkPos(pos);
        for (ServerPlayer player : level.players()) {
            if (player.chunkPosition().getChessboardDistance(cp) <= viewDist) {
                for (ClientboundBlockUpdatePacket packet : packets) {
                    player.connection.send(packet);
                }
            }
        }
    }

    private static boolean isHidden(LevelChunkSection[] sections, Level level,
                                    int sectionIdx, int baseY, int x, int y, int z,
                                    ChunkPos chunkPos) {
        for (Direction dir : Direction.values()) {
            int nx = x + dir.getStepX();
            int ny = y + dir.getStepY();
            int nz = z + dir.getStepZ();

            BlockState neighbor;

            if (nx >= 0 && nx < 16 && ny >= 0 && ny < 16 && nz >= 0 && nz < 16) {
                neighbor = sections[sectionIdx].getBlockState(nx, ny, nz);
            } else if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) {
                int adjSection = sectionIdx + (ny < 0 ? -1 : 1);
                if (adjSection < 0 || adjSection >= sections.length) return false;
                neighbor = sections[adjSection].getBlockState(nx, ny & 0xF, nz);
            } else {
                int worldX = (chunkPos.x << 4) + x + dir.getStepX();
                int worldY = baseY + y + dir.getStepY();
                int worldZ = (chunkPos.z << 4) + z + dir.getStepZ();
                BlockPos neighborPos = new BlockPos(worldX, worldY, worldZ);
                if (!level.isLoaded(neighborPos)) return false;
                neighbor = level.getBlockState(neighborPos);
            }

            if (!neighbor.canOcclude()) return false;
        }
        return true;
    }

    private static BlockState replacement(int x, int y, int z, boolean nether) {
        long hash = x * 341873128712L + z * 132897987541L + y * 67890123456L;
        hash = (hash ^ (hash >>> 16)) & 0x7FFFFFFF;

        BlockState[] palette;
        if (nether) {
            palette = NETHER_ORES;
        } else if (y < 0) {
            palette = DEEP_ORES;
        } else {
            palette = OVERWORLD_ORES;
        }
        return palette[(int) (hash % palette.length)];
    }
}
