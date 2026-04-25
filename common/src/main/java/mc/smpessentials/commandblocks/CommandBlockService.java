package mc.smpessentials.commandblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans loaded chunks for command blocks and provides update/delete operations.
 * All methods must be called on the server thread.
 */
public final class CommandBlockService {
    private CommandBlockService() {}

    public static boolean isEnabled(MinecraftServer server) {
        return server.overworld().isCommandBlockEnabled();
    }

    public static List<CommandBlockInfo> findAll(MinecraftServer server) {
        List<CommandBlockInfo> result = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            String dim = level.dimension().identifier().toString();
            level.getChunkSource().chunkMap.forEachReadyToSendChunk(chunk -> {
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof CommandBlockEntity cbe) {
                        result.add(toInfo(cbe, dim));
                    }
                }
            });
        }
        return result;
    }

    // Applies partial updates to a command block. Null fields are left unchanged.
    // Mode switching follows the same logic as vanilla's ServerboundSetCommandBlockPacket handler:
    // swap the block type, preserve facing + conditional, re-attach the block entity.
    public static boolean update(MinecraftServer server, String dim, int x, int y, int z,
                                 String command, String mode, Boolean auto,
                                 Boolean conditional, Boolean trackOutput) {
        ServerLevel level = resolveLevel(server, dim);
        if (level == null) return false;

        BlockPos pos = new BlockPos(x, y, z);
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CommandBlockEntity cbe)) return false;

        BaseCommandBlock cmd = cbe.getCommandBlock();
        CommandBlockEntity.Mode oldMode = cbe.getMode();

        // Mode switch: swap block type, preserve facing and conditional
        if (mode != null) {
            CommandBlockEntity.Mode newMode = parseMode(mode);
            if (newMode != null && newMode != oldMode) {
                BlockState oldState = level.getBlockState(pos);
                Direction facing = oldState.getValue(CommandBlock.FACING);
                boolean cond = conditional != null ? conditional : oldState.getValue(CommandBlock.CONDITIONAL);

                BlockState newState = switch (newMode) {
                    case SEQUENCE -> Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState();
                    case AUTO -> Blocks.REPEATING_COMMAND_BLOCK.defaultBlockState();
                    default -> Blocks.COMMAND_BLOCK.defaultBlockState();
                };
                newState = newState.setValue(CommandBlock.FACING, facing).setValue(CommandBlock.CONDITIONAL, cond);
                level.setBlock(pos, newState, 2);
                cbe.setBlockState(newState);
                level.getChunkAt(pos).setBlockEntity(cbe);
                cbe.onModeSwitch();
                // conditional was already applied via block state
                conditional = null;
            }
        }

        if (command != null) cmd.setCommand(command);
        if (auto != null) cbe.setAutomatic(auto);
        if (trackOutput != null) {
            cmd.setTrackOutput(trackOutput);
            if (!trackOutput) cmd.setLastOutput(null);
        }
        if (conditional != null) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CommandBlock) {
                level.setBlock(pos, state.setValue(CommandBlock.CONDITIONAL, conditional), 3);
            }
        }

        cbe.setChanged();
        level.getChunkSource().blockChanged(pos);
        return true;
    }

    public static boolean delete(MinecraftServer server, String dim, int x, int y, int z) {
        ServerLevel level = resolveLevel(server, dim);
        if (level == null) return false;

        BlockPos pos = new BlockPos(x, y, z);
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CommandBlockEntity)) return false;

        level.removeBlock(pos, false);
        return true;
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String dim) {
        var key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim));
        return server.getLevel(key);
    }

    private static CommandBlockEntity.Mode parseMode(String mode) {
        return switch (mode.toUpperCase()) {
            case "REDSTONE", "IMPULSE" -> CommandBlockEntity.Mode.REDSTONE;
            case "AUTO", "REPEAT" -> CommandBlockEntity.Mode.AUTO;
            case "SEQUENCE", "CHAIN" -> CommandBlockEntity.Mode.SEQUENCE;
            default -> null;
        };
    }

    private static CommandBlockInfo toInfo(CommandBlockEntity cbe, String dim) {
        BaseCommandBlock cmd = cbe.getCommandBlock();
        BlockPos pos = cbe.getBlockPos();
        return new CommandBlockInfo(
                pos.getX(), pos.getY(), pos.getZ(), dim,
                cmd.getCommand(), cbe.getMode().name(),
                cbe.isConditional(), cbe.isAutomatic(), cbe.isPowered(),
                cmd.getName().getString(), cmd.isTrackOutput(), cmd.getSuccessCount());
    }
}
