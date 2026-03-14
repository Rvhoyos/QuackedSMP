package mc.smpessentials.mixin;

import mc.smpessentials.skills.SkillEvents;
import mc.smpessentials.claims.ClaimProtection;
import mc.smpessentials.teleport.TeleportScheduler;
import mc.smpessentials.skills.ActiveAbilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("RETURN"), cancellable = true)
    private void onDropReturn(ItemStack stack, boolean randomizeMotion, boolean includeThrower,
            CallbackInfoReturnable<ItemEntity> cir) {
        ItemEntity itemEntity = cir.getReturnValue();
        if (itemEntity != null) {
            LivingEntity entity = (LivingEntity) (Object) this;
            if (entity instanceof Player player) {
                if (ActiveAbilities.onPlayerDropItem(player, itemEntity)) {
                    cir.setReturnValue(null);
                }
            }
        }
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void onHurtServer(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;

        // Safe Landing: intercept fall damage before anything else.
        // If handled, original event is cancelled; reduced damage is re-applied internally.
        if (SkillEvents.onFallDamage(entity, source, amount)) {
            cir.setReturnValue(false);
            return;
        }

        if (!ClaimProtection.onLivingHurt(entity, source, amount)) {
            cir.setReturnValue(false);
            return;
        }

        SkillEvents.onLivingHurt(entity, source, amount);

        if (entity instanceof ServerPlayer sp) {
            TeleportScheduler.onPlayerHurt(sp);
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void onDie(DamageSource source, CallbackInfo ci) {
        SkillEvents.onLivingDeath((LivingEntity) (Object) this, source);
    }
}
