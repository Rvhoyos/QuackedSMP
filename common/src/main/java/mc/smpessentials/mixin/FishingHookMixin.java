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

/**
 * Mixin into {@link FishingHook#retrieve(ItemStack)} to award Fishing skill XP
 * when a player successfully catches an item from water.
 *
 * <p>
 * The {@code retrieve()} method returns:
 * <ul>
 * <li>{@code 0} — nothing happened / client-side</li>
 * <li>{@code 1} — player caught loot from bobbing (the fish-catch path)</li>
 * <li>{@code 2} — hook was on ground</li>
 * <li>{@code 3} — hooked an ItemEntity</li>
 * <li>{@code 5} — hooked a non-item entity</li>
 * </ul>
 * We only award XP when return value is 1 (actual fishing catch).
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {

    /** Access the hook's owning player. */
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
            // 15 base XP per catch — scales well with the exponential curve
            SkillEvents.awardXp(sp, data, SkillType.FISHING, 15);
        }
    }
}
