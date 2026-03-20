package mc.smpessentials.dims;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Mirrors {@link net.minecraft.world.level.portal.PortalShape} but accepts any
 * block as the portal frame instead of the hardcoded obsidian. Used to detect
 * and fill custom portal frames wired via {@code /dim setportal}.
 */
public final class CustomPortalShape {

    /** Minimum interior width of a valid portal frame, in blocks. */
    private static final int MIN_WIDTH  = 2;
    /** Maximum interior width of a valid portal frame, in blocks. Matches vanilla limits. */
    private static final int MAX_WIDTH  = 21;
    /** Minimum interior height of a valid portal frame, in blocks. */
    private static final int MIN_HEIGHT = 3;
    /** Maximum interior height of a valid portal frame, in blocks. Matches vanilla limits. */
    private static final int MAX_HEIGHT = 21;

    /** The axis along which the portal interior extends (X = east-west, Z = north-south). */
    private final Direction.Axis axis;
    /** The direction from the bottom-left corner towards the bottom-right corner of the frame. */
    private final Direction      rightDir;
    /** Count of existing {@link Blocks#NETHER_PORTAL} blocks already inside the frame interior. */
    private final int            numPortalBlocks;
    /** World position of the bottom-left interior corner of the portal frame. */
    private final BlockPos       bottomLeft;
    /** Interior height of the portal frame, in blocks. */
    private final int            height;
    /** Interior width of the portal frame, in blocks. */
    private final int            width;

    /**
     * Constructs a portal shape descriptor. All instances are created by the static factory
     * methods {@link #findEmptyShape} and {@link #findAnyShape}.
     */
    private CustomPortalShape(Direction.Axis axis, int numPortalBlocks, Direction rightDir,
                               BlockPos bottomLeft, int width, int height) {
        this.axis            = axis;
        this.numPortalBlocks = numPortalBlocks;
        this.rightDir        = rightDir;
        this.bottomLeft      = bottomLeft;
        this.width           = width;
        this.height          = height;
    }

    /**
     * Finds a valid, completely empty portal frame (no portal blocks inside yet)
     * made of {@code frameBlock} centred around {@code pos} on the given axis.
     */
    public static Optional<CustomPortalShape> findEmptyShape(LevelAccessor level, BlockPos pos,
                                                              Direction.Axis axis, Block frameBlock) {
        Predicate<BlockState> isFrame = state -> state.is(frameBlock);
        return Optional.of(findAnyShape(level, pos, axis, isFrame))
                .filter(s -> s.isValid() && s.numPortalBlocks == 0);
    }

    /**
     * Scans the level to characterise the portal frame around {@code pos} on the given axis,
     * regardless of whether the interior is empty or already filled.
     *
     * <p>Returns a zero-size shape if the frame cannot be detected (e.g. no frame block found,
     * width out of range). Callers check {@link #isValid()} before use.
     */
    private static CustomPortalShape findAnyShape(BlockGetter level, BlockPos pos,
                                                   Direction.Axis axis, Predicate<BlockState> isFrame) {
        Direction direction = axis == Direction.Axis.X ? Direction.WEST : Direction.SOUTH;
        BlockPos bottomLeft = calculateBottomLeft(level, direction, pos, isFrame);
        if (bottomLeft == null) {
            return new CustomPortalShape(axis, 0, direction, pos, 0, 0);
        }
        int width = calculateWidth(level, bottomLeft, direction, isFrame);
        if (width == 0) {
            return new CustomPortalShape(axis, 0, direction, bottomLeft, 0, 0);
        }
        MutableInt portalBlocks = new MutableInt();
        int height = calculateHeight(level, bottomLeft, direction, width, portalBlocks, isFrame);
        return new CustomPortalShape(axis, portalBlocks.intValue(), direction, bottomLeft, width, height);
    }

    /**
     * Starting from {@code pos}, descends to the lowest air block in the interior column and then
     * walks horizontally opposite to {@code direction} until a frame block is hit, returning the
     * bottom-left interior corner. Returns {@code null} if no frame block is found in that direction.
     */
    private static @Nullable BlockPos calculateBottomLeft(BlockGetter level, Direction direction,
                                                           BlockPos pos, Predicate<BlockState> isFrame) {
        for (int i = Math.max(level.getMinY(), pos.getY() - MAX_HEIGHT);
             pos.getY() > i && isEmpty(level.getBlockState(pos.below())); pos = pos.below()) {
        }
        Direction opposite = direction.getOpposite();
        int j = getDistanceUntilEdge(level, pos, opposite, isFrame) - 1;
        return j < 0 ? null : pos.relative(opposite, j);
    }

    /**
     * Counts the interior width of the frame starting from {@code bottomLeft} in {@code direction}.
     * Returns the width if it falls within [{@link #MIN_WIDTH}, {@link #MAX_WIDTH}], or {@code 0}
     * if the frame is missing, too narrow, or too wide.
     */
    private static int calculateWidth(BlockGetter level, BlockPos bottomLeft,
                                       Direction direction, Predicate<BlockState> isFrame) {
        int i = getDistanceUntilEdge(level, bottomLeft, direction, isFrame);
        return (i >= MIN_WIDTH && i <= MAX_WIDTH) ? i : 0;
    }

    /**
     * Walks from {@code pos} in {@code direction} until a non-air block is encountered.
     * Returns the distance to that block if it satisfies {@code isFrame} (i.e. is a frame block),
     * or {@code 0} if a non-frame solid block is encountered first or the bottom drops away.
     */
    private static int getDistanceUntilEdge(BlockGetter level, BlockPos pos,
                                             Direction direction, Predicate<BlockState> isFrame) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= MAX_WIDTH; i++) {
            mutable.set(pos).move(direction, i);
            BlockState state = level.getBlockState(mutable);
            if (!isEmpty(state)) {
                return isFrame.test(state) ? i : 0;
            }
            BlockState below = level.getBlockState(mutable.move(Direction.DOWN));
            if (!isFrame.test(below)) break;
        }
        return 0;
    }

    /**
     * Measures the interior height of the frame starting from {@code pos}.
     * Increments {@code portalBlocks} for each existing portal block found in the interior.
     * Returns the height if it is in [{@link #MIN_HEIGHT}, {@link #MAX_HEIGHT}] and the top
     * frame row is intact, otherwise returns {@code 0}.
     */
    private static int calculateHeight(BlockGetter level, BlockPos pos, Direction direction,
                                        int width, MutableInt portalBlocks, Predicate<BlockState> isFrame) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int height = getDistanceUntilTop(level, pos, direction, mutable, width, portalBlocks, isFrame);
        if (height >= MIN_HEIGHT && height <= MAX_HEIGHT
                && hasTopFrame(level, pos, direction, mutable, width, height, isFrame)) {
            return height;
        }
        return 0;
    }

    /**
     * Verifies that every block in the top row of the portal frame (at {@code pos.above(height)})
     * satisfies {@code isFrame}.
     */
    private static boolean hasTopFrame(BlockGetter level, BlockPos pos, Direction direction,
                                        BlockPos.MutableBlockPos mutable, int width, int height,
                                        Predicate<BlockState> isFrame) {
        for (int i = 0; i < width; i++) {
            mutable.set(pos).move(Direction.UP, height).move(direction, i);
            if (!isFrame.test(level.getBlockState(mutable))) return false;
        }
        return true;
    }

    /**
     * Walks upward row by row, verifying the left and right pillars are frame blocks and
     * that each interior cell is empty (air, fire, or portal). Increments {@code portalBlocks}
     * for each existing portal block encountered. Stops and returns the current row index when
     * a pillar is missing or an interior cell is non-empty.
     *
     * <p>Returns at most {@link #MAX_HEIGHT} to bound the search.
     */
    private static int getDistanceUntilTop(BlockGetter level, BlockPos pos, Direction direction,
                                            BlockPos.MutableBlockPos mutable, int width,
                                            MutableInt portalBlocks, Predicate<BlockState> isFrame) {
        for (int i = 0; i < MAX_HEIGHT; i++) {
            mutable.set(pos).move(Direction.UP, i).move(direction, -1);
            if (!isFrame.test(level.getBlockState(mutable))) return i;

            mutable.set(pos).move(Direction.UP, i).move(direction, width);
            if (!isFrame.test(level.getBlockState(mutable))) return i;

            for (int j = 0; j < width; j++) {
                mutable.set(pos).move(Direction.UP, i).move(direction, j);
                BlockState state = level.getBlockState(mutable);
                if (!isEmpty(state)) return i;
                if (state.is(Blocks.NETHER_PORTAL)) portalBlocks.increment();
            }
        }
        return MAX_HEIGHT;
    }

    /**
     * Returns {@code true} if {@code state} is considered passable for portal interior cells:
     * air, any fire block, or an existing nether portal block.
     */
    private static boolean isEmpty(BlockState state) {
        return state.isAir() || state.is(BlockTags.FIRE) || state.is(Blocks.NETHER_PORTAL);
    }

    /**
     * Returns {@code true} if the detected frame dimensions are within the allowed
     * [{@link #MIN_WIDTH}..{@link #MAX_WIDTH}] x [{@link #MIN_HEIGHT}..{@link #MAX_HEIGHT}] bounds.
     */
    public boolean isValid() {
        return width >= MIN_WIDTH && width <= MAX_WIDTH && height >= MIN_HEIGHT && height <= MAX_HEIGHT;
    }

    /** Fills the interior of this portal shape with NETHER_PORTAL blocks. */
    public void createPortalBlocks(LevelAccessor level) {
        BlockState portalState = Blocks.NETHER_PORTAL.defaultBlockState()
                .setValue(NetherPortalBlock.AXIS, this.axis);
        BlockPos.betweenClosed(
                this.bottomLeft,
                this.bottomLeft.relative(Direction.UP, this.height - 1)
                               .relative(this.rightDir, this.width - 1)
        ).forEach(pos -> level.setBlock(pos, portalState, 18));
    }
}
