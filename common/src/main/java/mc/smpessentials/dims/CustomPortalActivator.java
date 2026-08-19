package mc.smpessentials.dims;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

// Handles right-click-with-water-bucket portal activation for custom dims.
// Blocked inside custom dims to prevent vanilla from generating obsidian return portals in the overworld.
public final class CustomPortalActivator {

    private CustomPortalActivator() {}

    // Attempts to activate a custom portal on right-click with a water bucket.
    // Returns SUCCESS (consume event) if a portal was created, PASS otherwise.
    // Blocked inside custom dims to prevent vanilla from creating unwanted obsidian return portals.
    public static InteractionResult onRightClickBlock(Player player, Level world,
                                                       InteractionHand hand, BlockPos pos,
                                                       Direction face) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!sp.getItemInHand(hand).is(Items.WATER_BUCKET)) return InteractionResult.PASS;

        ServerLevel serverLevel = (ServerLevel) sp.level();
        DimSavedData data = DimSavedData.get(serverLevel.getServer());

        // Block portal creation inside all custom dims, return portals are placed automatically
        // by the mod on first portal entry (ether: on the spawn island; others: at entry XZ).
        // Allowing activation here would trigger vanilla's cross-dim portal search and pollute
        // the overworld with auto-generated obsidian portal structures.
        if (data.getEntry(serverLevel.dimension().identifier().toString()).isPresent())
            return InteractionResult.PASS;

        Block frameBlock = world.getBlockState(pos).getBlock();
        String blockId = BuiltInRegistries.BLOCK.getKey(frameBlock).toString();

        Optional<String> linkedDim = data.getDimForPortalBlock(blockId);
        if (linkedDim.isEmpty()) return InteractionResult.PASS;

        CustomPortalShape shape = findValidShape(world, pos, face, frameBlock);
        if (shape == null) return InteractionResult.PASS;

        shape.createPortalBlocks(world);

        if (!sp.isCreative()) {
            sp.getItemInHand(hand).shrink(1);
            sp.getInventory().add(new ItemStack(Items.BUCKET));
        }

        return InteractionResult.SUCCESS;
    }

    // Tries pos.relative(face) first, then all neighbors, so clicking any face of any frame block works.
    @Nullable
    private static CustomPortalShape findValidShape(Level world, BlockPos framePos,
                                                     Direction face, Block frameBlock) {
        BlockPos[] seeds = {
            framePos.relative(face),
            framePos.north(), framePos.south(), framePos.east(), framePos.west(),
            framePos.above(), framePos.below()
        };
        for (BlockPos seed : seeds) {
            if (!world.getBlockState(seed).isAir()) continue;
            for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
                Optional<CustomPortalShape> shape =
                        CustomPortalShape.findEmptyShape(world, seed, axis, frameBlock);
                if (shape.isPresent() && shape.get().isValid()) return shape.get();
            }
        }
        return null;
    }
}
