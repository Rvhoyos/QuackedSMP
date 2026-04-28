package mc.smpessentials.mixin;

import mc.smpessentials.claims.SpawnProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Prevents farmland trampling by mobs and players in claims and spawn protection. Gated by PROTECT_FARMLAND.
@Mixin(FarmlandBlock.class)
public class FarmBlockMixin {

    @Redirect(
            method = "fallOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/FarmlandBlock;turnToDirt(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private static void cancelTrampleInProtectedArea(Entity entity, BlockState state, Level level, BlockPos pos) {
        if (mc.smpessentials.config.SmpConfig.PROTECT_FARMLAND
                && level instanceof ServerLevel sl && SpawnProtection.isProtectedArea(sl, pos)) {
            return;
        }
        FarmlandBlock.turnToDirt(entity, state, level, pos);
    }
}
