package mc.smpessentials.mixin;

import mc.smpessentials.claims.ClaimProtection;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void onPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!ClaimProtection.onBlockPlace(context.getLevel(), context.getClickedPos(),
                ((BlockItem) (Object) this).getBlock().defaultBlockState(), context.getPlayer())) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
