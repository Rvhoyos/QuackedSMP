package mc.smpessentials.mixin;

import mc.smpessentials.skills.SkillEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Awards Farming XP for sweet berries and glow berries, which are picked by right-clicking and so
// never reach the block break event. All three blocks return SUCCESS from useWithoutItem only on
// the path that actually drops fruit, so the return value is the pick.
@Mixin({ SweetBerryBushBlock.class, CaveVinesBlock.class, CaveVinesPlantBlock.class })
public abstract class BerryPickMixin {

    @Inject(method = "useWithoutItem", at = @At("RETURN"))
    private void quackedsmp$onBerriesPicked(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue() != InteractionResult.SUCCESS)
            return;
        if (player instanceof ServerPlayer sp)
            SkillEvents.onBerriesPicked(sp);
    }
}
