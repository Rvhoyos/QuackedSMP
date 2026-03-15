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

/**
 * Three responsibilities:
 *
 * <p><b>1. Ability activation via item drop</b> — intercepts {@code LivingEntity.drop()}
 * at RETURN. If the dropped item is a recognized tool and the player is sneaking,
 * {@link mc.smpessentials.skills.ActiveAbilities#onPlayerDropItem} handles activation
 * and returns the item to the player's inventory. The item entity's stack is consumed
 * by the inventory add, causing the entity to auto-discard on its next tick.
 *
 * <p><b>2. Fall damage / Safe Landing</b> — intercepts {@code hurtServer} at HEAD to
 * absorb a fraction of fall damage proportional to Agility level. Cancelled falls are
 * re-applied at reduced magnitude internally, guarded by
 * {@link mc.smpessentials.skills.SkillEvents#SAFE_LANDING_GUARD} to prevent recursion.
 *
 * <p><b>3. XP + claim events</b> — delegates to {@link SkillEvents#onLivingHurt} and
 * {@link SkillEvents#onLivingDeath} for XP gain, zoom cancellation, and bleed;
 * delegates to {@link mc.smpessentials.claims.ClaimProtection#onLivingHurt} for
 * PvP protection inside claims.
 */
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
