package mc.smpessentials.mixin;

import mc.smpessentials.skills.SkillData;
import mc.smpessentials.skills.SkillEvents;
import mc.smpessentials.skills.SkillType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Awards Enchanting XP when an enchantment is successfully applied.
 * XP scales with {@code costs[id]} (the level cost of the chosen slot).
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {

    /** The three enchantment option level costs displayed in the GUI. */
    @Shadow
    @Final
    public int[] costs;

    @Inject(method = "clickMenuButton", at = @At("RETURN"))
    private void quackedsmp$onEnchant(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue())
            return; // enchantment was not applied
        if (!(player instanceof ServerPlayer sp))
            return;

        ServerLevel sl = (ServerLevel) sp.level();
        SkillData data = SkillData.get(sl);

        // XP scales with the enchantment's level cost, 20 + cost*2 XP for every slot
        // e.g. a level-30 enchant awards 80 XP
        double xp = 20 + (this.costs[id] * 2.0);
        SkillEvents.awardXp(sp, data, SkillType.ENCHANTING, xp);
    }
}
