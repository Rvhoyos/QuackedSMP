package mc.smpessentials.mixin;

import mc.smpessentials.claims.SpawnProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Blocks end crystal placement inside the spawn protection area for non-OPs.
@Mixin(EndCrystalItem.class)
public abstract class EndCrystalItemMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void blockCrystalInSpawn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level level = context.getLevel();
        BlockPos placementPos = context.getClickedPos().above();
        if (level instanceof ServerLevel sl && SpawnProtection.isBlockInSpawnProtection(sl, placementPos)) {
            if (context.getPlayer() instanceof ServerPlayer sp
                    && sl.getServer().getPlayerList().isOp(sp.nameAndId())) {
                return;
            }
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
