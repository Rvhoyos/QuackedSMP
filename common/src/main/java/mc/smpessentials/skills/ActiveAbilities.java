package mc.smpessentials.skills;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Handles active ability activation via <b>sneak + drop key (Q)</b> with the
 * matching tool held. Abilities require a minimum skill level (10) and have
 * configurable cooldowns.
 *
 * <p>
 * Activation: hold the corresponding tool, sneak (shift), and press Q.
 * The item drop is cancelled and the ability activates instead. If the ability
 * is on cooldown, the drop is still cancelled and a cooldown message is shown.
 *
 * <p>
 * Exception: <b>Agility Dash</b> uses sprint + right-click with an empty hand
 * (no vanilla conflict since empty-hand right-click does nothing).
 */
public final class ActiveAbilities {

    private static final int MIN_LEVEL_FOR_ABILITY = 10;

    // Track active ability durations (player UUID -> expiry time)
    private static final Map<UUID, Long> superBreakerActive = new HashMap<>();
    private static final Map<UUID, Long> treeFellerActive = new HashMap<>();

    private ActiveAbilities() {
    }

    public static void init() {

        // ── Sneak + Q (drop key) activates tool abilities ──────────────
        // When the player sneaks and presses Q, the DROP_ITEM event fires
        // AFTER the item is removed from inventory. We cancel the event
        // (preventing the ItemEntity from spawning) and put the item back.
        PlayerEvent.DROP_ITEM.register((player, entity) -> {
            if (!(player instanceof ServerPlayer sp))
                return EventResult.pass();
            if (!sp.isShiftKeyDown())
                return EventResult.pass();

            ItemStack dropped = entity.getItem();
            ServerLevel sl = (ServerLevel) sp.level();
            SkillData data = SkillData.get(sl);
            UUID uuid = sp.getUUID();

            boolean handled = false;

            if (dropped.is(ItemTags.PICKAXES)) {
                handled = tryActivate(sp, data, SkillType.MINING, "Super Breaker", uuid);
            } else if (dropped.is(ItemTags.SHOVELS)) {
                handled = tryActivate(sp, data, SkillType.EXCAVATION, "Giga Drill", uuid);
            } else if (dropped.is(ItemTags.AXES)) {
                handled = tryActivateTreeFeller(sp, data, uuid);
            } else if (dropped.is(ItemTags.HOES)) {
                handled = tryActivateGreenTerra(sp, data, uuid, sl);
            } else if (dropped.getItem() instanceof FishingRodItem) {
                handled = tryActivateMasterAngler(sp, data, uuid);
            } else if (dropped.is(ItemTags.SWORDS)) {
                handled = tryActivateBerzerk(sp, data, uuid);
            } else if (dropped.getItem() instanceof BowItem || dropped.getItem() instanceof CrossbowItem) {
                handled = tryActivateSniper(sp, data, uuid);
            } else if (dropped.getItem() instanceof ShieldItem) {
                handled = tryActivateJuggernaut(sp, data, uuid);
            } else if (dropped.isDamageableItem() && dropped.isDamaged()) {
                // Non-tool damaged item → Arcane Infusion (repair 10%)
                handled = tryArcaneInfusion(sp, data, uuid, dropped);
            }

            if (handled) {
                // Cancel the drop and return the item to the player
                sp.getInventory().add(entity.getItem());
                return EventResult.interruptFalse();
            }

            return EventResult.pass();
        });

        // ── Sprint + right-click empty hand → Agility Dash ────────────
        // Empty-hand right-click has no vanilla action, so zero conflicts.
        InteractionEvent.RIGHT_CLICK_ITEM.register((player, hand) -> {
            if (!(player instanceof ServerPlayer sp))
                return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND)
                return InteractionResult.PASS;
            if (!sp.getMainHandItem().isEmpty())
                return InteractionResult.PASS;
            if (!sp.isSprinting())
                return InteractionResult.PASS;

            ServerLevel sl = (ServerLevel) sp.level();
            SkillData data = SkillData.get(sl);
            tryActivateDash(sp, data, sp.getUUID());
            return InteractionResult.PASS;
        });
    }

    // ========== ABILITY IMPLEMENTATIONS ==========

    /**
     * Generic ability handler for Mining/Excavation (Haste V).
     *
     * @return true if handled (activated or on cooldown), false if level too low
     */
    private static boolean tryActivate(ServerPlayer sp, SkillData data, SkillType skill, String name, UUID uuid) {
        int level = data.getLevel(uuid, skill);
        if (level < MIN_LEVEL_FOR_ABILITY)
            return false;

        if (!data.isAbilityReady(uuid, skill)) {
            long remaining = data.getCooldownRemaining(uuid, skill);
            sp.displayClientMessage(Component.literal(
                    "\u00a7c" + name + " on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return true; // handled: cancel the drop, show cooldown
        }

        data.setCooldown(uuid, skill);
        // Duration scales with level: 10s base + 0.2s per level
        int durationTicks = (int) ((10 + level * 0.2) * 20);

        switch (skill) {
            case MINING -> {
                sp.addEffect(new MobEffectInstance(MobEffects.HASTE, durationTicks, 4, false, false));
                superBreakerActive.put(uuid, System.currentTimeMillis() + (durationTicks * 50L));
                announce(sp, name, durationTicks / 20);
                resyncHand(sp);
            }
            case EXCAVATION -> {
                sp.addEffect(new MobEffectInstance(MobEffects.HASTE, durationTicks, 4, false, false));
                announce(sp, name, durationTicks / 20);
                resyncHand(sp);
            }
            default -> {
            }
        }
        return true;
    }

    /** @return true if handled */
    private static boolean tryActivateTreeFeller(ServerPlayer sp, SkillData data, UUID uuid) {
        int level = data.getLevel(uuid, SkillType.WOODCUTTING);
        if (level < MIN_LEVEL_FOR_ABILITY)
            return false;

        if (!data.isAbilityReady(uuid, SkillType.WOODCUTTING)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.WOODCUTTING);
            sp.displayClientMessage(Component.literal(
                    "\u00a7cTree Feller on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return true;
        }

        data.setCooldown(uuid, SkillType.WOODCUTTING);
        // Duration scales with level: 10s base + 0.2s per level
        int durationTicks = (int) ((10 + level * 0.2) * 20);
        treeFellerActive.put(uuid, System.currentTimeMillis() + (durationTicks * 50L));
        announce(sp, "Tree Feller", durationTicks / 20);
        resyncHand(sp);
        return true;
    }

    /** Check if tree feller is active and chain-break logs. */
    public static void onLogBreak(ServerPlayer sp, BlockPos pos, ServerLevel level) {
        UUID uuid = sp.getUUID();
        Long expiry = treeFellerActive.get(uuid);
        if (expiry == null || System.currentTimeMillis() > expiry) {
            treeFellerActive.remove(uuid);
            return;
        }
        chainBreakLogs(level, pos, sp, 64);
    }

    private static void chainBreakLogs(ServerLevel level, BlockPos start, ServerPlayer sp, int maxBlocks) {
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start.above());
        int broken = 0;

        while (!queue.isEmpty() && broken < maxBlocks) {
            BlockPos pos = queue.poll();
            if (visited.contains(pos))
                continue;
            visited.add(pos);

            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.LOGS))
                continue;

            level.destroyBlock(pos, true, sp);
            broken++;

            queue.add(pos.above());
            queue.add(pos.north());
            queue.add(pos.south());
            queue.add(pos.east());
            queue.add(pos.west());
        }
    }

    /** @return true if handled */
    private static boolean tryActivateGreenTerra(ServerPlayer sp, SkillData data, UUID uuid, ServerLevel level) {
        int farmLevel = data.getLevel(uuid, SkillType.FARMING);
        if (farmLevel < MIN_LEVEL_FOR_ABILITY)
            return false;

        if (!data.isAbilityReady(uuid, SkillType.FARMING)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.FARMING);
            sp.displayClientMessage(Component.literal(
                    "\u00a7cGreen Terra on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return true;
        }

        data.setCooldown(uuid, SkillType.FARMING);
        BlockPos center = sp.blockPosition();

        int bonemealed = 0;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(net.minecraft.tags.BlockTags.CROPS)) {
                        if (net.minecraft.world.item.BoneMealItem.growCrop(
                                new ItemStack(Items.BONE_MEAL), level, pos)) {
                            bonemealed++;
                        }
                    }
                }
            }
        }
        announce(sp, "Green Terra", bonemealed + " crops boosted");
        resyncHand(sp);
        return true;
    }

    /** @return true if handled */
    private static boolean tryActivateMasterAngler(ServerPlayer sp, SkillData data, UUID uuid) {
        int fishLevel = data.getLevel(uuid, SkillType.FISHING);
        if (fishLevel < MIN_LEVEL_FOR_ABILITY)
            return false;

        if (!data.isAbilityReady(uuid, SkillType.FISHING)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.FISHING);
            sp.displayClientMessage(Component.literal(
                    "\u00a7cMaster Angler on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return true;
        }

        data.setCooldown(uuid, SkillType.FISHING);
        int durationTicks = (int) ((10 + fishLevel * 0.2) * 20);
        sp.addEffect(new MobEffectInstance(MobEffects.LUCK, durationTicks, 4, false, false));
        announce(sp, "Master Angler", durationTicks / 20);
        resyncHand(sp);
        return true;
    }

    /** @return true if handled */
    private static boolean tryActivateBerzerk(ServerPlayer sp, SkillData data, UUID uuid) {
        int meleeLevel = data.getLevel(uuid, SkillType.MELEE);
        if (meleeLevel < MIN_LEVEL_FOR_ABILITY)
            return false;

        if (!data.isAbilityReady(uuid, SkillType.MELEE)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.MELEE);
            sp.displayClientMessage(Component.literal(
                    "\u00a7cBerzerk on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return true;
        }

        data.setCooldown(uuid, SkillType.MELEE);
        int durationTicks = (int) ((10 + meleeLevel * 0.2) * 20);
        sp.addEffect(new MobEffectInstance(MobEffects.STRENGTH, durationTicks, 1, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.SPEED, durationTicks, 1, false, false));
        announce(sp, "Berzerk", durationTicks / 20);
        resyncHand(sp);
        return true;
    }

    /** @return true if handled */
    private static boolean tryActivateSniper(ServerPlayer sp, SkillData data, UUID uuid) {
        int archLevel = data.getLevel(uuid, SkillType.ARCHERY);
        if (archLevel < MIN_LEVEL_FOR_ABILITY)
            return false;

        if (!data.isAbilityReady(uuid, SkillType.ARCHERY)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.ARCHERY);
            sp.displayClientMessage(Component.literal(
                    "\u00a7cSniper on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return true;
        }

        data.setCooldown(uuid, SkillType.ARCHERY);
        int durationTicks = (int) ((10 + archLevel * 0.2) * 20);
        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, durationTicks, 0, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, durationTicks, 0, false, false));
        announce(sp, "Sniper", durationTicks / 20);
        resyncHand(sp);
        return true;
    }

    /** @return true if handled */
    private static boolean tryActivateJuggernaut(ServerPlayer sp, SkillData data, UUID uuid) {
        int defLevel = data.getLevel(uuid, SkillType.DEFENSE);
        if (defLevel < MIN_LEVEL_FOR_ABILITY)
            return false;

        if (!data.isAbilityReady(uuid, SkillType.DEFENSE)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.DEFENSE);
            sp.displayClientMessage(Component.literal(
                    "\u00a7cJuggernaut on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return true;
        }

        data.setCooldown(uuid, SkillType.DEFENSE);
        int durationTicks = (int) ((10 + defLevel * 0.2) * 20);
        sp.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, durationTicks, 3, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, durationTicks, 3, false, false));
        announce(sp, "Juggernaut", durationTicks / 20);
        resyncHand(sp);
        return true;
    }

    /**
     * Agility Dash: boost velocity in facing direction.
     * Triggered by sprint + right-click with empty hand.
     */
    public static void tryActivateDash(ServerPlayer sp, SkillData data, UUID uuid) {
        int agiLevel = data.getLevel(uuid, SkillType.AGILITY);
        if (agiLevel < MIN_LEVEL_FOR_ABILITY)
            return;

        if (!data.isAbilityReady(uuid, SkillType.AGILITY)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.AGILITY);
            sp.displayClientMessage(Component.literal(
                    "\u00a7cDash on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return;
        }

        data.setCooldown(uuid, SkillType.AGILITY);
        Vec3 look = sp.getLookAngle();
        double power = 1.5 + (agiLevel * 0.01);
        sp.setDeltaMovement(look.x * power, Math.max(look.y * power, 0.4), look.z * power);
        sp.hurtMarked = true;
        announce(sp, "Dash", 0);
    }

    // ========== KNOWLEDGE ABILITIES ==========

    /**
     * Arcane Infusion: repairs the dropped item by 10% durability.
     * Triggered by sneak + Q with a damaged non-tool item (tridents, shears, etc.).
     * The item is repaired in-place before being returned to the player's
     * inventory.
     *
     * @return true if handled
     */
    private static boolean tryArcaneInfusion(ServerPlayer sp, SkillData data, UUID uuid, ItemStack droppedItem) {
        int level = data.getLevel(uuid, SkillType.ENCHANTING);
        if (level < MIN_LEVEL_FOR_ABILITY)
            return false;

        if (!data.isAbilityReady(uuid, SkillType.ENCHANTING)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.ENCHANTING);
            sp.displayClientMessage(Component.literal(
                    "\u00a7cArcane Infusion on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return true;
        }

        data.setCooldown(uuid, SkillType.ENCHANTING);
        int maxDmg = droppedItem.getMaxDamage();
        int repairAmount = Math.max(1, (int) (maxDmg * 0.10));
        droppedItem.setDamageValue(Math.max(0, droppedItem.getDamageValue() - repairAmount));
        announce(sp, "Arcane Infusion", "Repaired 10%");
        resyncHand(sp);
        return true;
    }

    // ========== HELPERS ==========

    private static void announce(ServerPlayer sp, String abilityName, int seconds) {
        String msg = "\u00a76\u00a7l\u2605 " + abilityName + " Activated! \u00a7r\u00a77(" + seconds + "s)";
        sp.displayClientMessage(Component.literal(msg), false);
        sp.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.5f);
    }

    private static void announce(ServerPlayer sp, String abilityName, String detail) {
        String msg = "\u00a76\u00a7l\u2605 " + abilityName + "! \u00a7r\u00a77" + detail;
        sp.displayClientMessage(Component.literal(msg), false);
        sp.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.5f);
    }

    private static String formatTime(long seconds) {
        if (seconds >= 3600)
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        if (seconds >= 60)
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    private static void resyncHand(ServerPlayer sp) {
        // Force the client to refresh the inventory, fixing the "ghost item" visual
        // glitch
        sp.inventoryMenu.sendAllDataToRemote();
    }
}
