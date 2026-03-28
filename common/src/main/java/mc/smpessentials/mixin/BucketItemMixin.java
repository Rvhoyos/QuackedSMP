package mc.smpessentials.mixin;

import mc.smpessentials.claims.ClaimProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.material.Fluid;

// Enforces the lava-in-wilderness restriction for bucket placement, which bypasses BlockItem.place entirely.
@Mixin(BucketItem.class)
public abstract class BucketItemMixin {

    @Shadow
    public abstract Fluid getContent();

    @Inject(method = "emptyContents", at = @At("HEAD"), cancellable = true)
    private void onEmptyContents(LivingEntity entity, Level level, BlockPos pos,
                                  BlockHitResult hitResult,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide()) return;
        if (entity == null) return;

        // Build the block state the fluid would place so ClaimProtection can
        // inspect its fluid state (same check used for non-bucket lava placement).
        BlockState fluidBlock = getContent().defaultFluidState().createLegacyBlock();
        if (!ClaimProtection.onBlockPlace(level, pos, fluidBlock, entity)) {
            cir.setReturnValue(false);
        }
    }
}
