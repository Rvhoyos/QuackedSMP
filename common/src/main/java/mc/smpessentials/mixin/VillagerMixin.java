package mc.smpessentials.mixin;

import mc.smpessentials.skills.SkillData;
import mc.smpessentials.skills.SkillEvents;
import mc.smpessentials.skills.SkillType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Awards Trading XP when a villager trade succeeds. XP scales with the offer's own XP value.
@Mixin(Villager.class)
public abstract class VillagerMixin {

    @Inject(method = "rewardTradeXp", at = @At("HEAD"))
    private void quackedsmp$onTrade(MerchantOffer offer, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        Player player = self.getTradingPlayer();

        if (player instanceof ServerPlayer sp) {
            ServerLevel sl = (ServerLevel) sp.level();
            SkillData data = SkillData.get(sl);
            // 10 base + the offer's intrinsic XP value (typically 2-10)
            double xp = 10 + offer.getXp();
            SkillEvents.awardXp(sp, data, SkillType.TRADING, xp);
        }
    }
}
