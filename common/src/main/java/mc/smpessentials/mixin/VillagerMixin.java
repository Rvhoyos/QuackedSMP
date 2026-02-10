package mc.smpessentials.mixin;

import mc.smpessentials.skills.SkillData;
import mc.smpessentials.skills.SkillEvents;
import mc.smpessentials.skills.SkillType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link Villager#rewardTradeXp(MerchantOffer)} to award Trading
 * skill XP when a player completes a trade with a villager.
 *
 * <p>
 * {@code rewardTradeXp()} is called whenever a trade succeeds. It sets
 * {@code this.lastTradedPlayer = this.getTradingPlayer()}, so we can safely
 * read the trading player at {@code HEAD} before the villager processes the
 * reward. XP is scaled by the offer's own XP value (bigger trades = more XP).
 */
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
