package mc.smpessentials.mixin;

import mc.smpessentials.skills.SkillData;
import mc.smpessentials.skills.SkillEvents;
import mc.smpessentials.skills.SkillType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into
 * {@link BrewingStandBlockEntity#doBrew(Level, BlockPos, NonNullList)}
 * to award Alchemy skill XP when a brewing operation completes.
 *
 * <p>
 * {@code doBrew()} is a private static method called from {@code serverTick()}
 * when {@code brewTime} reaches 0 and the recipe is still valid. It has no
 * player
 * context, so we award XP to the <b>nearest player within 8 blocks</b> of the
 * brewing stand. This mirrors how mcMMO handles brewing attribution.
 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandMixin {

    @Inject(method = "doBrew", at = @At("TAIL"))
    private static void quackedsmp$onBrewComplete(Level level, BlockPos pos,
            NonNullList<ItemStack> items, CallbackInfo ci) {
        if (!(level instanceof ServerLevel sl))
            return;

        // Find the nearest player within 8 blocks of the brewing stand
        Player nearest = level.getNearestPlayer(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                8.0, false);

        if (nearest instanceof ServerPlayer sp) {
            SkillData data = SkillData.get(sl);
            // 15 XP per successful brew (typically brews 1-3 potions at once)
            SkillEvents.awardXp(sp, data, SkillType.ALCHEMY, 15);
        }
    }
}
