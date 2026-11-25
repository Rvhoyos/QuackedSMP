package mc.smpessentials.mixin;

import mc.smpessentials.skills.SkillData;
import mc.smpessentials.skills.SkillEvents;
import mc.smpessentials.skills.SkillType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Awards Alchemy XP on brew completion. No player context in doBrew(), so XP goes to nearest player within 8 blocks.
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandMixin {

    @Inject(method = "doBrew", at = @At("TAIL"))
    private static void quackedsmp$onBrewComplete(ServerLevel level, BlockPos pos,
            BrewingStandBlockEntity stand, CallbackInfo ci) {
        // Find the nearest player within 8 blocks of the brewing stand
        Player nearest = level.getNearestPlayer(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                8.0, false);

        if (nearest instanceof ServerPlayer sp) {
            SkillData data = SkillData.get(level);
            // 15 XP per successful brew (typically brews 1-3 potions at once)
            SkillEvents.awardXp(sp, data, SkillType.ALCHEMY, 15);
        }
    }
}
