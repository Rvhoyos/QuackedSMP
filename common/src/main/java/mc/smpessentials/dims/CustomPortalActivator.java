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

/**
 * Handles custom portal activation via a right-click with a water bucket.
 *
 * <h2>Portal lifecycle</h2>
 * <ol>
 *   <li><strong>Link a block to a dim</strong> — {@code /dim setportal <dim> <block>}.
 *       Each custom dim has exactly one portal frame block; each block links to at most one dim.
 *       When a dim is first created, {@code minecraft:glowstone} is assigned automatically
 *       as its default frame block (if glowstone is not already claimed by another dim).</li>
 *   <li><strong>Build the frame</strong> — arrange the frame block in the standard nether-portal
 *       shape (2–21 wide, 3–21 tall) anywhere in a vanilla dimension.</li>
 *   <li><strong>Activate</strong> — right-click any frame block with a water bucket.
 *       The interior fills with {@link net.minecraft.world.level.block.Blocks#NETHER_PORTAL} blocks
 *       and the bucket is consumed (returned as an empty bucket in survival).</li>
 *   <li><strong>Travel</strong> — stepping through the portal routes the player via
 *       {@link mc.smpessentials.mixin.NetherPortalBlockMixin}, which handles both directions:
 *       vanilla dim → custom dim, and custom dim → overworld.</li>
 * </ol>
 *
 * <h2>Custom-dim restriction</h2>
 * <p>Portal activation is intentionally blocked inside custom (non-vanilla) dimensions.
 * Allowing it there would trigger vanilla's cross-dimension portal search, which auto-generates
 * return portals and pollutes the overworld with unwanted portal structures.
 * Players return from a custom dim by stepping through any existing portal block in that dim,
 * which is routed back to the overworld by the mixin.
 *
 * <p>Call {@link #onRightClickBlock} from both platform event hooks, passing the face of the
 * clicked block so the interior seed position can be derived correctly.
 */
public final class CustomPortalActivator {

    private CustomPortalActivator() {}

    /**
     * Attempts to activate a custom portal when a player right-clicks a frame block with a
     * water bucket. Activation is blocked inside custom dimensions (see class javadoc).
     *
     * <p>{@code face} is the face of {@code pos} that was clicked. It is used to derive
     * the interior seed position ({@code pos.relative(face)}) for shape detection, since
     * {@link CustomPortalShape#findEmptyShape} requires a position inside the frame, not on it.
     *
     * @param face the face of the clicked block (from the platform right-click event)
     * @return {@link InteractionResult#SUCCESS} if a portal was created (consume the event),
     *         {@link InteractionResult#PASS} otherwise.
     */
    public static InteractionResult onRightClickBlock(Player player, Level world,
                                                       InteractionHand hand, BlockPos pos,
                                                       Direction face) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!sp.getItemInHand(hand).is(Items.WATER_BUCKET)) return InteractionResult.PASS;

        ServerLevel serverLevel = (ServerLevel) sp.level();
        DimSavedData data = DimSavedData.get(serverLevel.getServer());

        // Block portal creation inside all custom dims — return portals are placed automatically
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

    /**
     * Finds a valid, empty portal shape made of {@code frameBlock} near {@code framePos}.
     *
     * <p>{@link CustomPortalShape#findEmptyShape} requires a seed position inside the frame
     * (air), not on the frame itself. We derive candidate seeds by trying {@code pos.relative(face)}
     * first (the natural interior when clicking the inner face), then all other adjacent positions
     * as a fallback so that clicking any face of any frame block works reliably.
     */
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
