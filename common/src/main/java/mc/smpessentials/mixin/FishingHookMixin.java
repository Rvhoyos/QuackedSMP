package mc.smpessentials.mixin;

import mc.smpessentials.skills.SkillData;
import mc.smpessentials.skills.SkillEvents;
import mc.smpessentials.skills.SkillType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Awards Fishing XP when retrieve() returns 1 (loot from water); other return values are non-catch paths.
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {

    @Shadow
    public abstract Player getPlayerOwner();

    @Inject(method = "retrieve", at = @At("RETURN"))
    private void quackedsmp$onFishCatch(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() != 1)
            return; // Only the loot-from-water path

        Player player = this.getPlayerOwner();
        if (player instanceof ServerPlayer sp) {
            ServerLevel sl = (ServerLevel) sp.level();
            SkillData data = SkillData.get(sl);
            // 15 base XP per catch, scales well with the exponential curve
            SkillEvents.awardXp(sp, data, SkillType.FISHING, 15);
        }
    }
}
