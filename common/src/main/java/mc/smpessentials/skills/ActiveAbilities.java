package mc.smpessentials.skills;

import mc.smpessentials.config.SmpConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.nbt.CompoundTag;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.minecraft.world.entity.player.Inventory;

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
 * Exception: <b>Agility Dash</b> uses sprint + sneak while in air.
 * <b>Scout Zoom</b> uses sneak + F (swap offhand) with both hands empty.
 */
public final class ActiveAbilities {

    // Track active ability durations (player UUID -> expiry time ms)
    private static final Map<UUID, Long> treeFellerActive = new HashMap<>();

    /**
     * Immutable snapshot of the player's state at Scout Zoom activation.
     *
     * @param savedOffhand        the offhand item that was replaced by the spyglass
     *                            (always {@link ItemStack#EMPTY} since activation requires both
     *                            hands to be empty, but stored defensively)
     * @param expiry              wall-clock ms at which zoom expires automatically
     * @param priorSpyglassCount  number of spyglasses in the player's main inventory
     *                            (slots 0–35) at activation; used to detect and remove any
     *                            injected spyglass the player dragged into their inventory
     */
    private record ZoomState(ItemStack savedOffhand, long expiry, int priorSpyglassCount) {}
    private static final Map<UUID, ZoomState> zoomActive = new HashMap<>();

    private ActiveAbilities() {
    }

    /**
     * Called when a player drops an item.
     * 
     * @return true if the event is handled and should be cancelled, false
     *         otherwise.
     */
    public static boolean onPlayerDropItem(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.entity.item.ItemEntity entity) {
        if (!(player instanceof ServerPlayer sp))
            return false;
        if (sp.isCreative())
            return false;
        if (!sp.isShiftKeyDown())
            return false;

        ItemStack dropped = entity.getItem();
        ServerLevel sl = (ServerLevel) sp.level();
        SkillData data = SkillData.get(sl);
        UUID uuid = sp.getUUID();

        boolean handled = false;

        if (dropped.is(ItemTags.PICKAXES)) {
            if (tryActivate(sp, data, SkillType.MINING, "Super Breaker", uuid))
                handled = true;
        } else if (dropped.is(ItemTags.SHOVELS)) {
            if (tryActivate(sp, data, SkillType.EXCAVATION, "Giga Drill", uuid))
                handled = true;
        } else if (dropped.is(ItemTags.AXES)) {
            if (tryActivateTreeFeller(sp, data, uuid))
                handled = true;
        } else if (dropped.is(ItemTags.HOES)) {
            if (tryActivateGreenTerra(sp, data, uuid, sl))
                handled = true;
        } else if (dropped.getItem() instanceof FishingRodItem) {
            if (tryActivateMasterAngler(sp, data, uuid))
                handled = true;
        } else if (dropped.is(ItemTags.SWORDS)) {
            if (tryActivateBerzerk(sp, data, uuid))
                handled = true;
        } else if (dropped.getItem() instanceof BowItem || dropped.getItem() instanceof CrossbowItem) {
            if (tryActivateSniper(sp, data, uuid))
                handled = true;
        } else if (dropped.getItem() instanceof ShieldItem) {
            if (tryActivateJuggernaut(sp, data, uuid))
                handled = true;
        } else if (dropped.getItem() == Items.BOOK || dropped.getItem() == Items.ENCHANTED_BOOK) {
            // Alchemy: Book + Sneak + Q -> Silk Touch Spawner
            if (tryActivateAlchemy(sp, data, uuid))
                handled = true;
        } else if (dropped.getItem() == Items.EMERALD) {
            // Trading: Emerald + Sneak + Q -> Tycoon's Charm (Hero of Village)
            if (tryActivateTycoon(sp, data, uuid))
                handled = true;
        }

        // Independent check: Arcane Infusion (Repair)
        // Triggers alongside above abilities if the item is damageable and damaged
        if (dropped.isDamageableItem() && dropped.isDamaged()) {
            if (tryArcaneInfusion(sp, data, uuid, dropped))
                handled = true;
        }

        if (handled) {
            // Cancel the drop and return the item to the player
            sp.getInventory().add(entity.getItem());
            return true;
        }

        return false;
    }

    // ========== ABILITY IMPLEMENTATIONS ==========

    /**
     * Generic ability handler for Mining/Excavation (Haste V).
     *
     * @return true if handled (activated or on cooldown), false if level too low
     */
    private static boolean tryActivate(ServerPlayer sp, SkillData data, SkillType skill, String name, UUID uuid) {
        int level = data.getLevel(uuid, skill);
        if (level < SmpConfig.getAbilityUnlockLevel(skill))
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
                announce(sp, name, durationTicks / 20, SoundEvents.ANVIL_LAND);
                resyncHand(sp);
            }
            case EXCAVATION -> {
                sp.addEffect(new MobEffectInstance(MobEffects.HASTE, durationTicks, 4, false, false));
                announce(sp, name, durationTicks / 20, SoundEvents.GRASS_BREAK);
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
        if (level < SmpConfig.getAbilityUnlockLevel(SkillType.WOODCUTTING))
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
        announce(sp, "Tree Feller", durationTicks / 20, SoundEvents.UI_STONECUTTER_TAKE_RESULT);
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
        if (farmLevel < SmpConfig.getAbilityUnlockLevel(SkillType.FARMING))
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
                    if (state.is(net.minecraft.tags.BlockTags.CROPS)
                            && state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock bonemealable
                            && bonemealable.isValidBonemealTarget(level, pos, state)) {
                        if (bonemealable.isBonemealSuccess(level, level.random, pos, state)) {
                            bonemealable.performBonemeal(level, level.random, pos, state);
                        }
                        bonemealed++;
                    }
                }
            }
        }
        announce(sp, "Green Terra", bonemealed + " crops boosted", SoundEvents.BONE_MEAL_USE);
        resyncHand(sp);
        return true;
    }

    /** @return true if handled */
    private static boolean tryActivateMasterAngler(ServerPlayer sp, SkillData data, UUID uuid) {
        int fishLevel = data.getLevel(uuid, SkillType.FISHING);
        if (fishLevel < SmpConfig.getAbilityUnlockLevel(SkillType.FISHING))
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
        announce(sp, "Master Angler", durationTicks / 20, SoundEvents.EXPERIENCE_ORB_PICKUP);
        resyncHand(sp);
        return true;
    }

    /** @return true if handled */
    private static boolean tryActivateBerzerk(ServerPlayer sp, SkillData data, UUID uuid) {
        int meleeLevel = data.getLevel(uuid, SkillType.MELEE);
        if (meleeLevel < SmpConfig.getAbilityUnlockLevel(SkillType.MELEE))
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
        announce(sp, "Berzerk", durationTicks / 20, SoundEvents.ENDER_DRAGON_GROWL);
        resyncHand(sp);
        return true;
    }

    /** @return true if handled */
    private static boolean tryActivateSniper(ServerPlayer sp, SkillData data, UUID uuid) {
        int archLevel = data.getLevel(uuid, SkillType.ARCHERY);
        if (archLevel < SmpConfig.getAbilityUnlockLevel(SkillType.ARCHERY))
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
        announce(sp, "Sniper", durationTicks / 20, SoundEvents.ARROW_HIT_PLAYER);
        resyncHand(sp);
        return true;
    }

    /** @return true if handled */
    private static boolean tryActivateJuggernaut(ServerPlayer sp, SkillData data, UUID uuid) {
        int defLevel = data.getLevel(uuid, SkillType.DEFENSE);
        if (defLevel < SmpConfig.getAbilityUnlockLevel(SkillType.DEFENSE))
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
        announce(sp, "Juggernaut", durationTicks / 20, SoundEvents.SHIELD_BLOCK);
        resyncHand(sp);
        return true;
    }

    /**
     * Agility Dash: boost velocity in facing direction.
     * Triggered by sprint + sneak while in the air (not on ground).
     */
    public static void tryActivateDash(ServerPlayer sp, SkillData data, UUID uuid) {
        if (sp.isCreative())
            return;
        int agiLevel = data.getLevel(uuid, SkillType.AGILITY);
        if (agiLevel < SmpConfig.getAbilityUnlockLevel(SkillType.AGILITY))
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
        announce(sp, "Dash", 0, SoundEvents.FIREWORK_ROCKET_LAUNCH);
    }

    // ========== SCOUT ZOOM ==========

    private static final String ZOOM_KEY = "archery_zoom";

    /**
     * Attempts to activate Scout Zoom for the given player.
     *
     * <p>Activation requires both hands to be empty (enforced upstream by
     * {@link mc.smpessentials.mixin.PlayerActionMixin}) so {@code savedOffhand} in the
     * resulting {@link ZoomState} will always be {@link net.minecraft.world.item.ItemStack#EMPTY}.
     * The field is retained for defensive correctness in case the precondition changes.
     *
     * <p>Effects scale with Archery level:
     * <ul>
     *   <li>Duration: 10 s base + 0.25 s/level (up to 35 s at level 100)</li>
     *   <li>Night Vision: amplifier 0 below level 67, amplifier 1 at 67+</li>
     *   <li>Glow cone range: 30 blocks (levels 1–33), 60 (34–66), 100 (67–100)</li>
     * </ul>
     *
     * <p>No-ops silently if the player has not reached the unlock level.
     * Displays a cooldown message (actionbar) if the ability is on cooldown.
     */
    public static void tryActivateZoom(ServerPlayer sp, SkillData data) {
        if (sp.isCreative())
            return;
        UUID uuid = sp.getUUID();
        int archLevel = data.getLevel(uuid, SkillType.ARCHERY);
        if (archLevel < SmpConfig.getAbilityUnlockLevel(ZOOM_KEY))
            return;

        if (!data.isAbilityReady(uuid, ZOOM_KEY, SkillType.ARCHERY)) {
            long remaining = data.getCooldownRemaining(uuid, ZOOM_KEY, SkillType.ARCHERY);
            sp.displayClientMessage(Component.literal(
                    "\u00a7cScout Zoom on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return;
        }

        // Duration: 10s base, +0.25s per level → 35s at level 100
        int durationTicks = (int) ((10 + archLevel * 0.25) * 20);
        long expiryMs = System.currentTimeMillis() + (durationTicks * 50L);

        // Snapshot how many spyglasses are in the main inventory RIGHT NOW, before we
        // inject one into the offhand. Any count above this after activation means the
        // player dragged the injected spyglass into their inventory.
        int priorSpyglassCount = countSpyglassesInMainInventory(sp);

        // Save current offhand and inject spyglass
        ItemStack savedOffhand = sp.getOffhandItem().copy();
        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SPYGLASS));
        sp.inventoryMenu.sendAllDataToRemote();

        // Night Vision: level I below 67, level II at 67+
        int nvAmp = archLevel >= 67 ? 1 : 0;
        sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, durationTicks, nvAmp, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, durationTicks, 0, false, false));

        zoomActive.put(uuid, new ZoomState(savedOffhand, expiryMs, priorSpyglassCount));
        data.setCooldown(uuid, ZOOM_KEY);

        announce(sp, "Scout Zoom", durationTicks / 20, SoundEvents.SPYGLASS_USE);
    }

    /**
     * Deactivates Scout Zoom for the given player and restores their offhand.
     *
     * <p>Safe to call even when zoom is not active — no-ops if the player has no
     * active zoom state. Also safe to call during player logout before inventory
     * is saved, ensuring the temporary spyglass is never persisted.
     *
     * <p>The offhand is only cleared if it still holds the injected spyglass
     * (item == {@link Items#SPYGLASS}, count == 1). This is the normal path —
     * {@link mc.smpessentials.mixin.PlayerActionMixin} blocks all GUI interactions
     * with the offhand slot while zoom is active, so the spyglass cannot be moved
     * via normal inventory UI. The {@code else} branch handles admin commands or
     * creative-mode manipulation that bypass the packet-level lock.
     */
    public static void deactivateZoom(UUID uuid, ServerPlayer sp) {
        ZoomState state = zoomActive.remove(uuid);
        if (state == null) return;

        ItemStack current = sp.getOffhandItem();
        if (current.getItem() == Items.SPYGLASS && current.getCount() == 1) {
            // Normal case: spyglass still in offhand — clear it and restore saved item.
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, state.savedOffhand());
            sp.inventoryMenu.sendAllDataToRemote();
        } else {
            // Spyglass was removed by an admin command or creative-mode slot edit
            // that bypasses the packet-level GUI lock. Remove any excess spyglasses
            // that appeared since activation as a last-resort cleanup.
            removeExcessSpyglasses(sp, state.priorSpyglassCount());
        }
    }

    /**
     * Called every server tick for players with active Scout Zoom.
     *
     * <p>Responsibilities:
     * <ol>
     *   <li>Expire zoom when the duration timer elapses.</li>
     *   <li>Deactivate if the player switches to a hotbar slot that has an item
     *       (main hand becomes non-empty), since they can no longer hold the spyglass.</li>
     *   <li>Apply the {@link net.minecraft.world.effect.MobEffects#GLOWING Glowing} effect
     *       (40 ticks) to living entities within the look cone in front of the player.
     *       Range and cone angle scale with Archery level.</li>
     * </ol>
     */
    public static void onZoomTick(ServerPlayer sp, SkillData data) {
        UUID uuid = sp.getUUID();
        ZoomState state = zoomActive.get(uuid);
        if (state == null) return;

        // Expire on timer
        if (System.currentTimeMillis() > state.expiry()) {
            deactivateZoom(uuid, sp);
            sp.displayClientMessage(Component.literal("\u00a77Scout Zoom ended."), true);
            return;
        }

        // Deactivate if player switched to a hotbar slot with an item
        if (!sp.getMainHandItem().isEmpty()) {
            deactivateZoom(uuid, sp);
            sp.displayClientMessage(Component.literal("\u00a77Scout Zoom ended."), true);
            return;
        }

        // Deactivate if spyglass was dragged out of offhand (to inventory or dropped).
        // deactivateZoom() handles inventory cleanup via removeExcessSpyglasses().
        ItemStack offhand = sp.getOffhandItem();
        if (!(offhand.getItem() == Items.SPYGLASS && offhand.getCount() == 1)) {
            deactivateZoom(uuid, sp);
            sp.displayClientMessage(Component.literal("\u00a77Scout Zoom ended."), true);
            return;
        }

        // Apply Glowing to mobs in look cone — range scales with Archery level
        int archLevel = data.getLevel(uuid, SkillType.ARCHERY);
        double range = archLevel <= 33 ? 30 : archLevel <= 66 ? 60 : 100;

        ServerLevel sl = (ServerLevel) sp.level();
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle();

        sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                sp.getBoundingBox().inflate(range),
                e -> e != sp && !(e instanceof net.minecraft.world.entity.player.Player))
            .stream()
            .filter(e -> {
                Vec3 toEntity = e.position().subtract(eye).normalize();
                return toEntity.dot(look) > 0.85; // ~32° cone
            })
            .forEach(e -> e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false)));
    }

    /**
     * Called on player logout to discard any active Tree Feller state.
     * Prevents stale UUID entries from accumulating in the map.
     */
    public static void clearTreeFeller(UUID uuid) {
        treeFellerActive.remove(uuid);
    }

    /**
     * Returns {@code true} if the given player currently has Scout Zoom active.
     * Used by the player tick and event handlers to skip the zoom pipeline for
     * players who have not activated it.
     */
    public static boolean isZoomActive(UUID uuid) {
        return zoomActive.containsKey(uuid);
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
        if (level < SmpConfig.getAbilityUnlockLevel(SkillType.ENCHANTING))
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
        announce(sp, "Arcane Infusion", "Repaired 10%", SoundEvents.ENCHANTMENT_TABLE_USE);
        resyncHand(sp);
        return true;
    }

    // ========== SCOUT ZOOM HELPERS ==========

    /**
     * Counts spyglasses across main inventory slots 0–{@link Inventory#INVENTORY_SIZE}-1
     * (does NOT include the offhand slot). Used to detect duplication when the player
     * drags the injected spyglass out of the offhand.
     */
    private static int countSpyglassesInMainInventory(ServerPlayer sp) {
        Inventory inv = sp.getInventory();
        int count = 0;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (inv.getItem(i).getItem() == Items.SPYGLASS) {
                count += inv.getItem(i).getCount();
            }
        }
        return count;
    }

    /**
     * Removes spyglasses from the player's main inventory until the count is back to
     * {@code priorCount}. Slots are scanned front-to-back (hotbar first).
     *
     * <p>No-ops if the current count is already {@code <= priorCount} (e.g. the player
     * dropped the spyglass to the world instead of moving it to inventory).
     */
    private static void removeExcessSpyglasses(ServerPlayer sp, int priorCount) {
        int toRemove = countSpyglassesInMainInventory(sp) - priorCount;
        if (toRemove <= 0) return;

        Inventory inv = sp.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE && toRemove > 0; i++) {
            if (inv.getItem(i).getItem() == Items.SPYGLASS) {
                inv.setItem(i, ItemStack.EMPTY);
                toRemove--;
            }
        }
        sp.inventoryMenu.sendAllDataToRemote();
    }

    // ========== HELPERS ==========

    private static void announce(ServerPlayer sp, String abilityName, int seconds,
            net.minecraft.sounds.SoundEvent sound) {
        String msg;
        if (seconds > 0) {
            msg = "\u00a76\u00a7l\u2605 " + abilityName + " Activated! \u00a7r\u00a77(" + seconds + "s)";
        } else {
            msg = "\u00a76\u00a7l\u2605 " + abilityName + " Activated! \u00a7r";
        }
        sp.displayClientMessage(Component.literal(msg), false);
        sp.playSound(sound, 1.0f, 1.5f);
    }

    private static void announce(ServerPlayer sp, String abilityName, int seconds,
            net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound) {
        announce(sp, abilityName, seconds, sound.value());
    }

    private static void announce(ServerPlayer sp, String abilityName, String detail,
            net.minecraft.sounds.SoundEvent sound) {
        String msg = "\u00a76\u00a7l\u2605 " + abilityName + "! \u00a7r\u00a77" + detail;
        sp.displayClientMessage(Component.literal(msg), false);
        sp.playSound(sound, 1.0f, 1.5f);
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

    /** @return true if handled */
    private static boolean tryActivateAlchemy(ServerPlayer sp, SkillData data, UUID uuid) {
        int alchLevel = data.getLevel(uuid, SkillType.ALCHEMY);
        if (alchLevel < SmpConfig.getAbilityUnlockLevel(SkillType.ALCHEMY))
            return false;

        // Perform raycast to see if looking at a spawner (5 blocks range)
        Vec3 start = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1.0F);
        Vec3 end = start.add(look.x * 5, look.y * 5, look.z * 5);
        BlockHitResult hit = sp.level()
                .clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, sp));

        if (hit.getType() != HitResult.Type.BLOCK)
            return false;

        BlockPos pos = hit.getBlockPos();
        BlockState state = sp.level().getBlockState(pos);

        if (!state.is(Blocks.SPAWNER))
            return false;

        // Check cooldown only after verifying a valid target
        if (!data.isAbilityReady(uuid, SkillType.ALCHEMY)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.ALCHEMY);
            sp.displayClientMessage(Component.literal(
                    "\u00a7cAlchemy on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return true;
        }

        // Get spawner data
        BlockEntity be = sp.level().getBlockEntity(pos);
        if (be == null)
            return false;

        // Create spawner item with NBT
        ItemStack spawnerItem = new ItemStack(Blocks.SPAWNER);
        CompoundTag tag = be.saveWithFullMetadata(sp.registryAccess());

        // Use DataComponents for 1.21+
        spawnerItem.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(BlockEntityType.MOB_SPAWNER, tag));

        // Drop item
        net.minecraft.world.entity.item.ItemEntity it = new net.minecraft.world.entity.item.ItemEntity(
                sp.level(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, spawnerItem);
        it.setDefaultPickUpDelay();
        sp.level().addFreshEntity(it);

        // Destroy block (no drops, since we dropped custom)
        sp.level().destroyBlock(pos, false, sp);

        // Cooldown + Effects
        data.setCooldown(uuid, SkillType.ALCHEMY);
        announce(sp, "Philosopher's Touch", "Spawner Silk Touched!", SoundEvents.ZOMBIE_VILLAGER_CONVERTED);
        resyncHand(sp);

        // Particles/Sound
        sp.level().playSound(sp, pos, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS, 1.0f, 1.0f);

        return true;
    }

    /** @return true if handled */
    private static boolean tryActivateTycoon(ServerPlayer sp, SkillData data, UUID uuid) {
        int tradeLevel = data.getLevel(uuid, SkillType.TRADING);
        if (tradeLevel < SmpConfig.getAbilityUnlockLevel(SkillType.TRADING))
            return false;

        if (!data.isAbilityReady(uuid, SkillType.TRADING)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.TRADING);
            sp.displayClientMessage(Component.literal(
                    "\u00a7cTycoon's Charm on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return true;
        }

        data.setCooldown(uuid, SkillType.TRADING);

        // Duration: 30s base + 0.5s per level
        int durationTicks = (int) ((30 + tradeLevel * 0.5) * 20);

        // Amplifier calculation
        int amp = 0;
        if (tradeLevel >= 80)
            amp = 2; // Hero III
        else if (tradeLevel >= 50)
            amp = 1; // Hero II

        sp.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, durationTicks, amp, false, false));
        announce(sp, "Tycoon's Charm", durationTicks / 20, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
        resyncHand(sp);
        return true;
    }
}
