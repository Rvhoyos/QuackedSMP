package mc.smpessentials.skills;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import mc.smpessentials.claims.ClaimAccessCache;
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

import net.minecraft.world.entity.player.Inventory;

import java.util.*;

// Active ability handler. Activation: hold matching tool + sneak + Q.
// Agility Dash: sprint + sneak in air. Scout Zoom: sneak + F with both hands empty.
public final class ActiveAbilities {

    // Track active ability durations (player UUID -> expiry time ms)
    private static final Map<UUID, Long> treeFellerActive = new HashMap<>();

    // Captures zoom activation state: saved offhand item, expiry time, and spyglass count in main inventory before activation.
    private record ZoomState(ItemStack savedOffhand, long expiry, int priorSpyglassCount) {}
    private static final Map<UUID, ZoomState> zoomActive = new HashMap<>();

    private ActiveAbilities() {
    }

    // Returns true if the drop event should be cancelled, which is only when an ability actually
    // fired. A gesture that hits a cooldown still says so and then drops the item normally, so
    // sneak + Q never silently swallows an item the player meant to throw away.
    public static boolean onPlayerDropItem(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.entity.item.ItemEntity entity) {
        if (!mc.smpessentials.config.SmpConfig.SKILLS_ENABLED) return false;
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

        SkillType triggered = abilityFor(dropped);
        if (triggered != null) {
            handled = switch (triggered) {
                case MINING      -> tryActivate(sp, data, SkillType.MINING, "Super Breaker", uuid);
                case EXCAVATION  -> tryActivate(sp, data, SkillType.EXCAVATION, "Giga Drill", uuid);
                case WOODCUTTING -> tryActivateTreeFeller(sp, data, uuid);
                case FARMING     -> tryActivateGreenTerra(sp, data, uuid, sl);
                case FISHING     -> tryActivateMasterAngler(sp, data, uuid);
                case MELEE       -> tryActivateBerzerk(sp, data, uuid);
                case ARCHERY     -> tryActivateSniper(sp, data, uuid);
                case DEFENSE     -> tryActivateJuggernaut(sp, data, uuid);
                case ALCHEMY     -> tryActivateAlchemy(sp, data, uuid);
                case TRADING     -> tryActivateTycoon(sp, data, uuid);
                case AGILITY, ENCHANTING -> false;
            };
        }

        // Independent check: Arcane Infusion (Repair)
        // Triggers alongside above abilities if the item is damageable and damaged
        if (dropped.isDamageableItem() && dropped.isDamaged()) {
            if (tryArcaneInfusion(sp, data, uuid, dropped))
                handled = true;
        }

        if (handled) {
            // Cancel the drop and return the item to the player.
            // If the inventory is full, drop it to the world rather than losing it silently.
            if (!sp.getInventory().add(entity.getItem())) {
                sp.drop(entity.getItem(), false);
            }
            return true;
        }

        return false;
    }

    /**
     * The skill whose sneak + Q ability {@code stack} triggers, or null for an item that triggers
     * none. Agility and Enchanting are absent on purpose: Dash has no trigger item, and Arcane
     * Infusion overlays any damaged item rather than owning one, so both are handled separately.
     */
    public static SkillType abilityFor(ItemStack stack) {
        if (stack.is(ItemTags.PICKAXES)) return SkillType.MINING;
        if (stack.is(ItemTags.SHOVELS))  return SkillType.EXCAVATION;
        if (stack.is(ItemTags.AXES))     return SkillType.WOODCUTTING;
        if (stack.is(ItemTags.HOES))     return SkillType.FARMING;
        if (stack.is(ItemTags.SWORDS))   return SkillType.MELEE;
        Item item = stack.getItem();
        if (item instanceof FishingRodItem)                    return SkillType.FISHING;
        if (item instanceof BowItem || item instanceof CrossbowItem) return SkillType.ARCHERY;
        if (item instanceof ShieldItem)                        return SkillType.DEFENSE;
        if (item == Items.BOOK || item == Items.ENCHANTED_BOOK) return SkillType.ALCHEMY;
        if (item == Items.EMERALD)                             return SkillType.TRADING;
        return null;
    }

    // ========== ABILITY IMPLEMENTATIONS ==========

    // Shared handler for Super Breaker (Mining) and Giga Drill (Excavation). Applies Haste V; duration: 10s + 0.2s/level.
    private static boolean tryActivate(ServerPlayer sp, SkillData data, SkillType skill, String name, UUID uuid) {
        int level = data.getLevel(uuid, skill);
        if (level < SmpConfig.getAbilityUnlockLevel(skill))
            return false;

        if (!data.isAbilityReady(uuid, skill)) {
            long remaining = data.getCooldownRemaining(uuid, skill);
            sp.sendSystemMessage(Component.literal(
                    "\u00a7c" + name + " on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return false; // not activated, so the drop goes through as a normal drop
        }

        data.setCooldown(uuid, skill);
        // Duration scales with level: 10s base + 0.2s per level
        int durationTicks = (int) ((10 + level * 0.2) * 20);

        switch (skill) {
            case MINING -> {
                sp.addEffect(new MobEffectInstance(MobEffects.HASTE, durationTicks, 4, false, false, true));
                announce(sp, name, durationTicks / 20, SoundEvents.ANVIL_LAND);
                resyncHand(sp);
            }
            case EXCAVATION -> {
                sp.addEffect(new MobEffectInstance(MobEffects.HASTE, durationTicks, 4, false, false, true));
                announce(sp, name, durationTicks / 20, SoundEvents.GRASS_BREAK);
                resyncHand(sp);
            }
            default -> {
            }
        }
        return true;
    }

    // Marks the player's UUID in treeFellerActive. onLogBreak chain-breaks logs while the timer hasn't expired.
    private static boolean tryActivateTreeFeller(ServerPlayer sp, SkillData data, UUID uuid) {
        int level = data.getLevel(uuid, SkillType.WOODCUTTING);
        if (level < SmpConfig.getAbilityUnlockLevel(SkillType.WOODCUTTING))
            return false;

        if (!data.isAbilityReady(uuid, SkillType.WOODCUTTING)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.WOODCUTTING);
            sp.sendSystemMessage(Component.literal(
                    "\u00a7cTree Feller on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return false; // not activated, so the drop goes through as a normal drop
        }

        data.setCooldown(uuid, SkillType.WOODCUTTING);
        // Duration scales with level: 10s base + 0.2s per level
        int durationTicks = (int) ((10 + level * 0.2) * 20);
        treeFellerActive.put(uuid, System.currentTimeMillis() + (durationTicks * 50L));
        announce(sp, "Tree Feller", durationTicks / 20, SoundEvents.UI_STONECUTTER_TAKE_RESULT);
        resyncHand(sp);
        return true;
    }

    // Measured between the two furthest-apart logs of a tree, not from its trunk, because the leash
    // is anchored wherever the player cut and that can be a branch tip. MegaJungleTrunkPlacer offsets
    // branch logs by (int)(1.5 + cos(angle) * i) for i up to 4, so 1.5 +/- 4 truncates to the range
    // -2 to 5 on each axis: opposite tips are 7 apart, and 7 reaches every log of every vanilla tree
    // from any cut. There is deliberately no vertical bound, the radius keeps a fell to one trunk's
    // column and FELL_MAX_LOGS caps the cost, so a third limit would only risk cutting a tall tree
    // short.
    private static final int FELL_RADIUS = 7;

    // The largest tree vanilla can grow is a mega jungle at 159 logs (4 trunk columns of up to 31,
    // plus at most 7 branches of 5). This is the same single-tick destroyBlock budget that
    // SkillEvents.applyLeafBlower already spends on this very event, so it stops a fell running into
    // a log build without ever being the thing that leaves a real tree standing.
    private static final int FELL_MAX_LOGS = 343;

    // Called when a log is broken. If Tree Feller is active, fells the rest of the tree.
    public static void onLogBreak(ServerPlayer sp, BlockPos pos, ServerLevel level) {
        UUID uuid = sp.getUUID();
        Long expiry = treeFellerActive.get(uuid);
        if (expiry == null || System.currentTimeMillis() > expiry) {
            treeFellerActive.remove(uuid);
            return;
        }
        chainBreakLogs(level, pos, sp);
    }

    /**
     * Breaks every log connected to {@code start}, within {@link #FELL_RADIUS} of it.
     *
     * All 26 neighbours are walked, not just the 6 faces. Mega jungle branches step diagonally in
     * all three axes at once, and a 2x2 trunk needs the sideways step at the broken log's own level
     * to reach its other three columns.
     */
    private static void chainBreakLogs(ServerLevel level, BlockPos start, ServerPlayer sp) {
        // On Fabric this runs before the claim check on the player's own block, hence testing the
        // origin up front.
        ClaimAccessCache claims = new ClaimAccessCache(level, sp);
        if (!claims.canModify(start))
            return;

        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // The player's own break already drops this log, so it is marked seen rather than enqueued.
        // Breaking it again here would run the drops a second time.
        visited.add(start.asLong());
        enqueueNeighbors(queue, visited, start, start);

        int broken = 0;
        while (!queue.isEmpty() && broken < FELL_MAX_LOGS) {
            cursor.set(queue.dequeueLong());
            if (!level.getBlockState(cursor).is(BlockTags.LOGS))
                continue;
            if (!claims.canModify(cursor))
                continue;

            BlockPos log = cursor.immutable();
            level.destroyBlock(log, true, sp);
            broken++;
            enqueueNeighbors(queue, visited, log, start);
        }
    }

    /**
     * Queues the 26 neighbours of {@code from} that are unseen and still within {@link #FELL_RADIUS}
     * of {@code origin}. Marking them seen here rather than on dequeue is what keeps each position
     * to a single block lookup: neighbouring logs share most of their neighbours, so a face-only
     * check on dequeue would look at the same position over and over.
     */
    private static void enqueueNeighbors(LongArrayFIFOQueue queue, LongOpenHashSet visited,
            BlockPos from, BlockPos origin) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0)
                        continue;
                    int x = from.getX() + dx;
                    int y = from.getY() + dy;
                    int z = from.getZ() + dz;
                    if (Math.abs(x - origin.getX()) > FELL_RADIUS
                            || Math.abs(z - origin.getZ()) > FELL_RADIUS)
                        continue;
                    long packed = BlockPos.asLong(x, y, z);
                    if (visited.add(packed))
                        queue.enqueue(packed);
                }
            }
        }
    }

    // Green Terra: applies bonemeal to all crops in an 11x11x5 area around the player.
    private static boolean tryActivateGreenTerra(ServerPlayer sp, SkillData data, UUID uuid, ServerLevel level) {
        int farmLevel = data.getLevel(uuid, SkillType.FARMING);
        if (farmLevel < SmpConfig.getAbilityUnlockLevel(SkillType.FARMING))
            return false;

        if (!data.isAbilityReady(uuid, SkillType.FARMING)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.FARMING);
            sp.sendSystemMessage(Component.literal(
                    "\u00a7cGreen Terra on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return false; // not activated, so the drop goes through as a normal drop
        }

        data.setCooldown(uuid, SkillType.FARMING);
        BlockPos center = sp.blockPosition();

        // The area is centred on the player, so standing near a border reaches into a neighbouring
        // claim. performBonemeal changes blocks without ever going near a break or place event, so
        // nothing else would check this.
        ClaimAccessCache claims = new ClaimAccessCache(level, sp);

        int bonemealed = 0;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(net.minecraft.tags.BlockTags.CROPS)
                            && state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock bonemealable
                            && bonemealable.isValidBonemealTarget(level, pos, state)
                            && claims.canModify(pos)
                            && bonemealable.isBonemealSuccess(level, level.getRandom(), pos, state)) {
                        bonemealable.performBonemeal(level, level.getRandom(), pos, state);
                        bonemealed++;
                    }
                }
            }
        }
        announce(sp, "Green Terra", bonemealed + " crops boosted", SoundEvents.BONE_MEAL_USE);
        resyncHand(sp);
        return true;
    }

    private static boolean tryActivateMasterAngler(ServerPlayer sp, SkillData data, UUID uuid) {
        int fishLevel = data.getLevel(uuid, SkillType.FISHING);
        if (fishLevel < SmpConfig.getAbilityUnlockLevel(SkillType.FISHING))
            return false;

        if (!data.isAbilityReady(uuid, SkillType.FISHING)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.FISHING);
            sp.sendSystemMessage(Component.literal(
                    "\u00a7cMaster Angler on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return false; // not activated, so the drop goes through as a normal drop
        }

        data.setCooldown(uuid, SkillType.FISHING);
        // Luck is read at FishingHook.retrieve, not at cast, so the buff has to outlive the whole
        // wait or it does nothing at all. Vanilla's worst case is timeUntilLured 600 plus
        // timeUntilHooked 80 plus a 40 tick nibble, so 36s is the shortest base that always covers
        // it, and 34 + 10 * 0.2 is exactly 36s at the level 10 unlock.
        int durationTicks = (int) ((34 + fishLevel * 0.2) * 20);
        sp.addEffect(new MobEffectInstance(MobEffects.LUCK, durationTicks, 4, false, false, true));
        announce(sp, "Master Angler", durationTicks / 20, SoundEvents.EXPERIENCE_ORB_PICKUP);
        resyncHand(sp);
        return true;
    }

    private static boolean tryActivateBerzerk(ServerPlayer sp, SkillData data, UUID uuid) {
        int meleeLevel = data.getLevel(uuid, SkillType.MELEE);
        if (meleeLevel < SmpConfig.getAbilityUnlockLevel(SkillType.MELEE))
            return false;

        if (!data.isAbilityReady(uuid, SkillType.MELEE)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.MELEE);
            sp.sendSystemMessage(Component.literal(
                    "\u00a7cBerzerk on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return false; // not activated, so the drop goes through as a normal drop
        }

        data.setCooldown(uuid, SkillType.MELEE);
        int durationTicks = (int) ((10 + meleeLevel * 0.2) * 20);
        sp.addEffect(new MobEffectInstance(MobEffects.STRENGTH, durationTicks, 1, false, false, true));
        sp.addEffect(new MobEffectInstance(MobEffects.SPEED, durationTicks, 1, false, false, true));
        announce(sp, "Berzerk", durationTicks / 20, SoundEvents.ENDER_DRAGON_GROWL);
        resyncHand(sp);
        return true;
    }

    private static boolean tryActivateSniper(ServerPlayer sp, SkillData data, UUID uuid) {
        int archLevel = data.getLevel(uuid, SkillType.ARCHERY);
        if (archLevel < SmpConfig.getAbilityUnlockLevel(SkillType.ARCHERY))
            return false;

        if (!data.isAbilityReady(uuid, SkillType.ARCHERY)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.ARCHERY);
            sp.sendSystemMessage(Component.literal(
                    "\u00a7cSniper on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return false; // not activated, so the drop goes through as a normal drop
        }

        data.setCooldown(uuid, SkillType.ARCHERY);
        int durationTicks = (int) ((10 + archLevel * 0.2) * 20);
        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, durationTicks, 0, false, false, true));
        sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, durationTicks, 0, false, false, true));
        announce(sp, "Sniper", durationTicks / 20, SoundEvents.ARROW_HIT_PLAYER);
        resyncHand(sp);
        return true;
    }

    private static boolean tryActivateJuggernaut(ServerPlayer sp, SkillData data, UUID uuid) {
        int defLevel = data.getLevel(uuid, SkillType.DEFENSE);
        if (defLevel < SmpConfig.getAbilityUnlockLevel(SkillType.DEFENSE))
            return false;

        if (!data.isAbilityReady(uuid, SkillType.DEFENSE)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.DEFENSE);
            sp.sendSystemMessage(Component.literal(
                    "\u00a7cJuggernaut on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return false; // not activated, so the drop goes through as a normal drop
        }

        data.setCooldown(uuid, SkillType.DEFENSE);
        int durationTicks = (int) ((10 + defLevel * 0.2) * 20);
        sp.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, durationTicks, 3, false, false, true));
        sp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, durationTicks, 3, false, false, true));
        announce(sp, "Juggernaut", durationTicks / 20, SoundEvents.SHIELD_BLOCK);
        resyncHand(sp);
        return true;
    }

    // Agility Dash: launches player in look direction. Power scales with Agility level. Only called when sprinting in air.
    public static void tryActivateDash(ServerPlayer sp, SkillData data, UUID uuid) {
        if (sp.isCreative())
            return;
        int agiLevel = data.getLevel(uuid, SkillType.AGILITY);
        if (agiLevel < SmpConfig.getAbilityUnlockLevel(SkillType.AGILITY))
            return;

        if (!data.isAbilityReady(uuid, SkillType.AGILITY)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.AGILITY);
            sp.sendSystemMessage(Component.literal(
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

    // Scout Zoom: injects spyglass into offhand, applies Night Vision + Slow Falling.
    // Duration: 10s base + 0.25s/level. Night Vision II at Archery level 67+. Glowing cone range: 30/60/100 by tier.
    public static void tryActivateZoom(ServerPlayer sp, SkillData data) {
        if (sp.isCreative())
            return;
        UUID uuid = sp.getUUID();
        int archLevel = data.getLevel(uuid, SkillType.ARCHERY);
        if (archLevel < SmpConfig.getAbilityUnlockLevel(ZOOM_KEY))
            return;

        if (!data.isAbilityReady(uuid, ZOOM_KEY, SkillType.ARCHERY)) {
            long remaining = data.getCooldownRemaining(uuid, ZOOM_KEY, SkillType.ARCHERY);
            sp.sendSystemMessage(Component.literal(
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
        sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, durationTicks, nvAmp, false, false, true));
        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, durationTicks, 0, false, false, true));

        zoomActive.put(uuid, new ZoomState(savedOffhand, expiryMs, priorSpyglassCount));
        data.setCooldown(uuid, ZOOM_KEY);

        announce(sp, "Scout Zoom", durationTicks / 20, SoundEvents.SPYGLASS_USE);
    }

    // Removes the spyglass from offhand and clears zoom state. Safe to call when not active.
    public static void deactivateZoom(UUID uuid, ServerPlayer sp) {
        ZoomState state = zoomActive.remove(uuid);
        if (state == null) return;

        ItemStack current = sp.getOffhandItem();
        if (current.getItem() == Items.SPYGLASS && current.getCount() == 1) {
            // Normal case: spyglass still in offhand, clear it and restore saved item.
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, state.savedOffhand());
            sp.inventoryMenu.sendAllDataToRemote();
        } else {
            // Spyglass was removed by an admin command or creative-mode slot edit
            // that bypasses the packet-level GUI lock. Remove any excess spyglasses
            // that appeared since activation as a last-resort cleanup.
            removeExcessSpyglasses(sp, state.priorSpyglassCount());
        }
    }

    // Per-tick: expires zoom on timer, deactivates if main hand is occupied, and applies Glowing to mobs in the look cone.
    public static void onZoomTick(ServerPlayer sp, SkillData data) {
        UUID uuid = sp.getUUID();
        ZoomState state = zoomActive.get(uuid);
        if (state == null) return;

        // Expire on timer
        if (System.currentTimeMillis() > state.expiry()) {
            deactivateZoom(uuid, sp);
            sp.sendSystemMessage(Component.literal("\u00a77Scout Zoom ended."), true);
            return;
        }

        // Deactivate if player switched to a hotbar slot with an item
        if (!sp.getMainHandItem().isEmpty()) {
            deactivateZoom(uuid, sp);
            sp.sendSystemMessage(Component.literal("\u00a77Scout Zoom ended."), true);
            return;
        }

        // Deactivate if spyglass was dragged out of offhand (to inventory or dropped).
        // deactivateZoom() handles inventory cleanup via removeExcessSpyglasses().
        ItemStack offhand = sp.getOffhandItem();
        if (!(offhand.getItem() == Items.SPYGLASS && offhand.getCount() == 1)) {
            deactivateZoom(uuid, sp);
            sp.sendSystemMessage(Component.literal("\u00a77Scout Zoom ended."), true);
            return;
        }

        // Apply Glowing to mobs in look cone, range scales with Archery level
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

    public static void clearTreeFeller(UUID uuid) {
        treeFellerActive.remove(uuid);
    }

    public static boolean isZoomActive(UUID uuid) {
        return zoomActive.containsKey(uuid);
    }

    // ========== KNOWLEDGE ABILITIES ==========

    // Arcane Infusion: repairs the dropped item by 10% of its max durability.
    private static boolean tryArcaneInfusion(ServerPlayer sp, SkillData data, UUID uuid, ItemStack droppedItem) {
        int level = data.getLevel(uuid, SkillType.ENCHANTING);
        if (level < SmpConfig.getAbilityUnlockLevel(SkillType.ENCHANTING))
            return false;

        if (!data.isAbilityReady(uuid, SkillType.ENCHANTING)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.ENCHANTING);
            sp.sendSystemMessage(Component.literal(
                    "\u00a7cArcane Infusion on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return false; // not activated, so the drop goes through as a normal drop
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

    // Removes spyglasses added to main inventory since zoom activation (e.g. player dragged the injected offhand spyglass in).
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
        sp.sendSystemMessage(Component.literal(msg), false);
        sp.playSound(sound, 1.0f, 1.5f);
    }

    private static void announce(ServerPlayer sp, String abilityName, int seconds,
            net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound) {
        announce(sp, abilityName, seconds, sound.value());
    }

    private static void announce(ServerPlayer sp, String abilityName, String detail,
            net.minecraft.sounds.SoundEvent sound) {
        String msg = "\u00a76\u00a7l\u2605 " + abilityName + "! \u00a7r\u00a77" + detail;
        sp.sendSystemMessage(Component.literal(msg), false);
        sp.playSound(sound, 1.0f, 1.5f);
    }

    static String formatTime(long seconds) {
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

    // Philosopher's Touch: raycasts for a spawner within 5 blocks and silk-touches it, preserving the mob type in NBT.
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

        // Checked before the cooldown is spent, so a refused attempt does not cost the charge.
        // Not handled, so the book drops as it already does when nothing valid is in view.
        if (!new ClaimAccessCache((ServerLevel) sp.level(), sp).canModify(pos)) {
            sp.sendSystemMessage(
                    Component.literal("\u00a7cThat spawner is in a protected area."), true);
            return false;
        }

        // Check cooldown only after verifying a valid target
        if (!data.isAbilityReady(uuid, SkillType.ALCHEMY)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.ALCHEMY);
            sp.sendSystemMessage(Component.literal(
                    "\u00a7cAlchemy on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return false; // not activated, so the drop goes through as a normal drop
        }

        // Get spawner data
        BlockEntity be = sp.level().getBlockEntity(pos);
        if (be == null)
            return false;

        ItemStack spawnerItem = SilkTouchedSpawner.create(be, sp.registryAccess());

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

    // Tycoon's Charm: grants Hero of the Village. Amplifier scales with Trading level (I/II/III at 1/50/80).
    private static boolean tryActivateTycoon(ServerPlayer sp, SkillData data, UUID uuid) {
        int tradeLevel = data.getLevel(uuid, SkillType.TRADING);
        if (tradeLevel < SmpConfig.getAbilityUnlockLevel(SkillType.TRADING))
            return false;

        if (!data.isAbilityReady(uuid, SkillType.TRADING)) {
            long remaining = data.getCooldownRemaining(uuid, SkillType.TRADING);
            sp.sendSystemMessage(Component.literal(
                    "\u00a7cTycoon's Charm on cooldown! \u00a77(" + formatTime(remaining) + ")"), true);
            return false; // not activated, so the drop goes through as a normal drop
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

        sp.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, durationTicks, amp, false, false, true));
        announce(sp, "Tycoon's Charm", durationTicks / 20, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
        resyncHand(sp);
        return true;
    }
}
