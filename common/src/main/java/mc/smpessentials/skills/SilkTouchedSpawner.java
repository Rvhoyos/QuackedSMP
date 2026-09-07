package mc.smpessentials.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

// A spawner item produced by Philosopher's Touch, and the only thing that knows how it is marked.
//
// Vanilla refuses to load BLOCK_ENTITY_DATA into a spawner unless the placer is in creative with
// gamemaster permission (BlockItem.updateCustomBlockEntityTag, MOB_SPAWNER is in
// BlockEntityTypes.OP_ONLY_CUSTOM_DATA). The marker is what lets BlockItemSpawnerMixin reopen that
// gate for these stacks only, so a configured spawner from a kit or a /give still obeys vanilla.
public final class SilkTouchedSpawner {

    private static final CompoundTag MARKER = new CompoundTag();

    static {
        MARKER.putBoolean("quacksmp_silk_spawner", true);
    }

    private SilkTouchedSpawner() {
    }

    // Saves the spawner verbatim onto a marked item. Uses saveCustomOnly rather than
    // saveWithFullMetadata so no source coordinates ride along, matching what vanilla's own
    // pick-block path writes; coordinates would make two otherwise identical spawners not stack.
    public static ItemStack create(BlockEntity spawner, HolderLookup.Provider registries) {
        ItemStack stack = new ItemStack(Blocks.SPAWNER);
        CompoundTag tag = spawner.saveCustomOnly(registries);
        stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(BlockEntityTypes.MOB_SPAWNER, tag));
        CustomData.set(DataComponents.CUSTOM_DATA, stack, MARKER);
        return stack;
    }

    public static boolean isMarked(ItemStack stack) {
        CustomData marker = stack.get(DataComponents.CUSTOM_DATA);
        return marker != null && marker.matchedBy(MARKER);
    }

    // Applies the saved spawner onto the block just placed at pos. Returns whether anything loaded.
    public static boolean restore(Level level, BlockPos pos, ItemStack stack) {
        if (level.isClientSide() || !isMarked(stack)) {
            return false;
        }

        TypedEntityData<?> saved = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (saved == null || saved.type() != BlockEntityTypes.MOB_SPAWNER) {
            return false;
        }

        return level.getBlockEntity(pos) instanceof SpawnerBlockEntity placed
                && saved.loadInto(placed, level.registryAccess());
    }
}
