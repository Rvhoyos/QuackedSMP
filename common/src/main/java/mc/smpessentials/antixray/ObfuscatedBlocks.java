package mc.smpessentials.antixray;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Which blocks the anti-xray engine hides, and what it hides them as.
 *
 * Everything outside {@link #TARGETS} (dirt, gravel, granite, etc.) is left untouched so the
 * underground still looks natural and only ore-bearing rock is masked.
 */
final class ObfuscatedBlocks {
    private ObfuscatedBlocks() {}

    private static final Set<Block> TARGETS = Set.of(
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
     * Same test as {@link #matches}, as a predicate, for palette-level checks such as
     * {@code LevelChunkSection.maybeHas}. Hoisted to a constant so the hot path allocates nothing.
     */
    static final Predicate<BlockState> PREDICATE = ObfuscatedBlocks::matches;

    static boolean matches(BlockState state) {
        return TARGETS.contains(state.getBlock());
    }

    /**
     * The state a hidden block is shown as: the plain base material for its depth. Break and
     * proximity reveal then flick stone to the real block, which reads as natural (unlike
     * fake-ore to stone, which looks like a glitch) and leaks no ore info to xrayers.
     */
    static BlockState maskFor(int worldY, boolean nether) {
        if (nether) {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        return worldY < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }
}
