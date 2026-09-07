package mc.smpessentials.mixin;

import mc.smpessentials.skills.SilkTouchedSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Lets a Philosopher's Touch spawner keep its mob when a survival player places it.
@Mixin(BlockItem.class)
public abstract class BlockItemSpawnerMixin {

    // Injected at RETURN, and only acting on false, so vanilla always gets first refusal: a creative
    // gamemaster placement loads the data itself and returns true, and this never runs. Only when
    // vanilla has declined does the marked stack get a second chance.
    @Inject(method = "updateCustomBlockEntityTag(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("RETURN"), cancellable = true)
    private static void onUpdateCustomBlockEntityTag(Level level, Player player, BlockPos pos, ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && SilkTouchedSpawner.restore(level, pos, stack)) {
            cir.setReturnValue(true);
        }
    }
}
