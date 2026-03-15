package mc.smpessentials.skills;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registers all event listeners for XP gain and passive perk application.
 * Call init() once from SmpUtilsMod.
 */
public final class SkillEvents {

    // Track distance for Agility
    private static final Map<UUID, BlockPos> lastPositions = new HashMap<>();
    // Track distance accumulator for Agility (fractional blocks)
    private static final Map<UUID, Double> distanceAccum = new HashMap<>();
    // Ticks since last age verify prompt per player (for periodic reprompt)
    private static final Map<UUID, Integer> agePromptTicks = new HashMap<>();
    private static final int AGE_REPROMPT_INTERVAL = 6000; // 5 minutes

    private SkillEvents() {
    }

    public static void init() {
        SmpUtilsMod.LOGGER.info("QuackedSMP Skills system initialized");
    }

    // ========== BLOCK BREAK (Mining, Excavation, Woodcutting, Farming) ==========

    /**
     * Called when a player breaks a block. Awards XP for Mining, Excavation,
     * Woodcutting, and Farming based on block type. Also cancels Scout Zoom if
     * active, and forwards log breaks to {@link ActiveAbilities#onLogBreak} for
     * Tree Feller chain-breaking.
     */
    public static void onBlockBreak(Level level, BlockPos pos, BlockState state, Player player) {
        if (!(player instanceof ServerPlayer sp))
            return;
        if (!(level instanceof ServerLevel sl))
            return;

        // Scout Zoom: cancel on block break
        if (ActiveAbilities.isZoomActive(sp.getUUID())) {
            ActiveAbilities.deactivateZoom(sp.getUUID(), sp);
        }

        try {
            SkillData data = SkillData.get(sl);

            // ---- Mining (Pickaxe blocks) ----
            if (isOre(state)) {
                double xp = oreXp(state);
                awardXp(sp, data, SkillType.MINING, xp);
                applyDoubleDrop(sp, data, state, pos, sl);
            } else if (isStone(state)) {
                awardXp(sp, data, SkillType.MINING, 1);
            }

            // ---- Excavation (Shovel blocks) ----
            else if (isShovelBlock(state)) {
                awardXp(sp, data, SkillType.EXCAVATION, 1);
                applyTreasureFind(sp, data, pos, sl);
            }

            // ---- Woodcutting (Logs) ----
            else if (state.is(BlockTags.LOGS)) {
                awardXp(sp, data, SkillType.WOODCUTTING, 5);
                applyDoubleDrop(sp, data, state, pos, sl);
                applyLeafBlower(sp, data, pos, sl);
                // Tree Feller: chain-break connected logs if ability is active
                ActiveAbilities.onLogBreak(sp, pos, sl);
            }

            // ---- Farming (Crops) ----
            else if (state.is(BlockTags.CROPS) && isMatureCrop(state)) {
                awardXp(sp, data, SkillType.FARMING, 5);
                applyAutoReplant(sp, data, state, pos, sl);
            }
        } catch (Exception e) {
            SmpUtilsMod.LOGGER.error("Error in SkillEvents block break handler", e);
        }
    }

    // ========== COMBAT (Melee, Archery, Defense) ==========

    /**
     * Called when a living entity dies. Awards Melee or Archery XP to the killing
     * player depending on whether the hit was direct (melee) or indirect (projectile).
     * The Arrow Recovery passive may trigger on projectile kills. Also cancels Scout
     * Zoom if the dying entity is a player.
     */
    public static void onLivingDeath(LivingEntity entity, DamageSource source) {
        if (entity.level().isClientSide())
            return;

        // Scout Zoom: cancel on player death
        if (entity instanceof ServerPlayer sp && ActiveAbilities.isZoomActive(sp.getUUID())) {
            ActiveAbilities.deactivateZoom(sp.getUUID(), sp);
        }

        if (!(entity instanceof Monster mob))
            return;

        Entity attacker = source.getEntity();
        if (!(attacker instanceof ServerPlayer sp))
            return;

        ServerLevel sl = (ServerLevel) sp.level();
        SkillData data = SkillData.get(sl);

        double baseXp = mobXp(mob);

        // Determine melee vs archery from damage source
        if (!source.isDirect()) {
            // Projectile = Archery
            double dist = sp.distanceTo(mob);
            double bonus = dist > 30 ? 2.0 : 1.0; // distance bonus
            awardXp(sp, data, SkillType.ARCHERY, baseXp * bonus);

            // Arrow Recovery passive: chance to recover an arrow
            int archLevel = data.getLevel(sp.getUUID(), SkillType.ARCHERY);
            double arrowChance = archLevel * 0.005; // 0.5% per level, 50% at Lv.100
            if (sp.getRandom().nextDouble() < arrowChance) {
                Block.popResource(sl, mob.blockPosition(), new ItemStack(Items.ARROW, 1));
                sp.displayClientMessage(Component.literal("\u00a76\u25c6 Arrow Recovered!"), true);
            }
        } else {
            awardXp(sp, data, SkillType.MELEE, baseXp);
        }
    }

    /**
     * Called when a living entity takes damage. Awards Defense XP to players hit
     * by a living attacker (excludes environmental sources such as cactus, fire,
     * and drowning). Triggers the Bleed passive for melee attacks by players.
     * Also cancels Scout Zoom if the hurt entity is a player with zoom active.
     */
    public static void onLivingHurt(LivingEntity entity, DamageSource source, float amount) {
        if (entity.level().isClientSide())
            return;

        // Scout Zoom: cancel on taking damage
        if (entity instanceof ServerPlayer victim && ActiveAbilities.isZoomActive(victim.getUUID())) {
            ActiveAbilities.deactivateZoom(victim.getUUID(), victim);
        }

        // Defense: player takes damage from a living attacker (mob or player).
        // Environmental sources (cactus, fire, drowning, fall, lava) are excluded
        // to prevent passive XP farming.
        if (entity instanceof ServerPlayer victim && source.getEntity() instanceof LivingEntity) {
            ServerLevel sl = (ServerLevel) victim.level();
            SkillData data = SkillData.get(sl);
            double xp = Math.max(1, Math.floor(amount)); // 1 XP per half-heart
            awardXp(victim, data, SkillType.DEFENSE, xp);
        }

        // Bleed: player attacks with melee
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer sp && entity instanceof LivingEntity target) {
            if (source.isDirect()) { // melee only
                ServerLevel sl = (ServerLevel) sp.level();
                SkillData data = SkillData.get(sl);
                int meleeLevel = data.getLevel(sp.getUUID(), SkillType.MELEE);
                double bleedChance = meleeLevel * 0.005; // 0.5% per level, 50% at 100
                if (sp.getRandom().nextDouble() < bleedChance) {
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0)); // 3s
                    sp.displayClientMessage(Component.literal("\u00a7c\u2694 Bleed!"), true);
                }

                // Combat parent damage buff is applied as a persistent attribute modifier
                // in updateParentBuffs() — no need to modify the event amount here.
            }
        }
    }

    // ========== SAFE LANDING ==========

    /**
     * Guards against re-entrant fall damage processing when we re-apply reduced damage.
     * Set to true while the reduced-damage re-call is in flight.
     */
    public static final ThreadLocal<Boolean> SAFE_LANDING_GUARD = ThreadLocal.withInitial(() -> false);

    /**
     * Intercepts fall damage and absorbs a fraction proportional to Agility level.
     * At Agility 100, absorbs up to {@code CAP_SAFE_LANDING} (default 1.0 = full negation).
     * Scales linearly: at level 50 with default cap, absorbs 50%.
     *
     * <p>When partially absorbed, re-applies the reduced amount via {@code entity.hurt()}
     * guarded by {@link #SAFE_LANDING_GUARD} to prevent infinite recursion.
     *
     * @return true if the original fall damage event should be cancelled (we handled it)
     */
    public static boolean onFallDamage(LivingEntity entity, DamageSource source, float amount) {
        if (SAFE_LANDING_GUARD.get()) return false;
        if (!(entity instanceof ServerPlayer sp)) return false;
        if (!source.is(DamageTypeTags.IS_FALL)) return false;

        ServerLevel sl = (ServerLevel) sp.level();
        SkillData data = SkillData.get(sl);
        int agiLevel = data.getLevel(sp.getUUID(), SkillType.AGILITY);
        if (agiLevel <= 0) return false;

        double reduction = SkillManager.perkScale(agiLevel, SmpConfig.CAP_SAFE_LANDING);
        if (reduction <= 0) return false;

        float reducedAmount = Math.max(0, (float) (amount * (1.0 - reduction)));
        int pct = Math.min(100, (int) (reduction * 100));

        if (reducedAmount > 0) {
            SAFE_LANDING_GUARD.set(true);
            try {
                sp.hurtServer((ServerLevel) sp.level(), source, reducedAmount);
            } finally {
                SAFE_LANDING_GUARD.set(false);
            }
        }
        // Show AFTER re-call so Defense XP action bar message doesn't overwrite it
        sp.displayClientMessage(Component.literal("\u00a7b\u2734 Safe Landing! -" + pct + "%"), true);
        return true; // cancel original event
    }

    // ========== AGILITY (Movement) ==========

    /**
     * Called every server tick for each player. Handles Agility XP accumulation
     * from horizontal movement (1 XP per 100 blocks walked), Dash trigger detection
     * (sprint + sneak while airborne), and Scout Zoom per-tick updates (glow cone
     * application and expiry check).
     */
    public static void onPlayerTick(Player player) {
        if (!(player instanceof ServerPlayer sp))
            return;
        if (sp.level().isClientSide())
            return;

        UUID uuid = sp.getUUID();
        BlockPos current = sp.blockPosition();
        BlockPos last = lastPositions.get(uuid);

        if (last != null && !sp.isPassenger() && !sp.isFallFlying()) {
            double dx = current.getX() - last.getX();
            double dz = current.getZ() - last.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist > 0 && dist < 10) { // ignore teleports
                double accum = distanceAccum.getOrDefault(uuid, 0.0) + dist;
                ServerLevel sl = (ServerLevel) sp.level();

                if (accum >= 100) { // 100 blocks walked = 1 XP
                    SkillData data = SkillData.get(sl);
                    int chunks = (int) (accum / 100);
                    awardXp(sp, data, SkillType.AGILITY, chunks);
                    accum -= chunks * 100;
                }
                distanceAccum.put(uuid, accum);
            }
        }
        lastPositions.put(uuid, current);

        // Agility Dash Trigger: Sprint + Sneak while in air (not on ground).
        // Sprint + Sneak is a unique combo (normally stops sprint).
        if (sp.isSprinting() && sp.isShiftKeyDown() && !sp.onGround()) {
            ServerLevel sl = (ServerLevel) sp.level();
            mc.smpessentials.skills.ActiveAbilities.tryActivateDash(sp, SkillData.get(sl), uuid);
        }

        // Scout Zoom tick: update glow and check expiry
        if (mc.smpessentials.skills.ActiveAbilities.isZoomActive(uuid)) {
            ServerLevel sl = (ServerLevel) sp.level();
            mc.smpessentials.skills.ActiveAbilities.onZoomTick(sp, SkillData.get(sl));
        }

        // Age verify periodic reprompt
        tickAgeVerify(sp, uuid);
    }

    /**
     * Called every tick per online player. Re-sends the age verification prompt
     * every {@link #AGE_REPROMPT_INTERVAL} ticks to players who have never responded.
     * Players who explicitly denied are not re-prompted — they can type
     * {@code /verify confirm} if they change their mind.
     */
    private static void tickAgeVerify(ServerPlayer sp, UUID uuid) {
        if (!mc.smpessentials.voicechat.VoicechatIntegration.isAvailable())
            return;
        mc.smpessentials.ageverify.AgeVerifyData data = mc.smpessentials.ageverify.AgeVerifyData
                .get(sp.level().getServer());
        // Verified or denied — no periodic action needed
        if (data.hasAnswered(uuid)) {
            agePromptTicks.remove(uuid);
            return;
        }
        int ticks = agePromptTicks.getOrDefault(uuid, 0) + 1;
        if (ticks >= AGE_REPROMPT_INTERVAL) {
            sendAgeVerifyPrompt(sp);
            agePromptTicks.put(uuid, 0);
        } else {
            agePromptTicks.put(uuid, ticks);
        }
    }

    /**
     * Called by {@link mc.smpessentials.mixin.PlayerActionMixin} when the player presses
     * sneak + F (swap-offhand key) with both hands empty, delegating to
     * {@link ActiveAbilities#tryActivateZoom}.
     */
    public static void onZoomActivate(net.minecraft.server.level.ServerPlayer sp) {
        ServerLevel sl = (ServerLevel) sp.level();
        ActiveAbilities.tryActivateZoom(sp, SkillData.get(sl));
    }

    /**
     * Called when a player disconnects. Cleans up per-player state: movement
     * accumulators, Tree Feller timer, and Scout Zoom (restoring the offhand
     * to prevent the injected spyglass from being persisted).
     */
    public static void onPlayerLoggedOut(Player player) {
        UUID uuid = player.getUUID();
        lastPositions.remove(uuid);
        distanceAccum.remove(uuid);
        agePromptTicks.remove(uuid);
        mc.smpessentials.skills.ActiveAbilities.clearTreeFeller(uuid);
        // Restore offhand and discard injected spyglass on disconnect so the
        // temporary item is not persisted to the player's save file.
        if (player instanceof ServerPlayer sp)
            mc.smpessentials.skills.ActiveAbilities.deactivateZoom(uuid, sp);
    }

    // ========== FISHING ==========

    // Fishing XP is handled by FishingHookMixin — no listener needed here.
    // The mixin injects into FishingHook.retrieve() and awards 15 XP on catch.

    /**
     * Called when a player joins the server. Updates the cached display name,
     * applies parent buff attribute modifiers based on current skill levels,
     * and executes any queued punishments.
     */
    public static void onPlayerJoin(Player player) {
        if (player instanceof ServerPlayer sp) {
            ServerLevel sl = (ServerLevel) sp.level();
            SkillData data = SkillData.get(sl);
            data.updateName(sp.getUUID(), sp.getName().getString());
            updateParentBuffs(sp, data);

            // Apply queued punishments if any
            mc.smpessentials.punish.PunishManager pm = mc.smpessentials.punish.PunishManager.get(sp.level().getServer());
            if (pm.isPending(sp.getUUID())) {
                pm.punish(sp);
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7cYour inventory and claims were cleared while you were away due to a recently applied punishment."));
            }

            // Age verification prompt for voice chat
            if (mc.smpessentials.voicechat.VoicechatIntegration.isAvailable()) {
                mc.smpessentials.ageverify.AgeVerifyData verifyData = mc.smpessentials.ageverify.AgeVerifyData
                        .get(sl.getServer());
                if (!verifyData.isVerified(sp.getUUID())) {
                    if (!verifyData.hasAnswered(sp.getUUID())) {
                        // Never responded — show full prompt and start reprompt timer
                        agePromptTicks.put(sp.getUUID(), 0);
                        sendAgeVerifyPrompt(sp);
                    } else {
                        // Previously denied — one-time reminder per login
                        sp.sendSystemMessage(Component.literal(
                                "\u00a77[Voice Chat] Voice chat is currently disabled for you. Type \u00a7a/verify confirm \u00a77if you are 18+ and wish to enable it."));
                    }
                }
            }
        }
    }

    private static void sendAgeVerifyPrompt(ServerPlayer sp) {
        sp.sendSystemMessage(Component.literal(
                "\u00a76\u00a7l[QuackedSMP]\u00a7r\u00a7f This server uses Simple Voice Chat, which is restricted to players 18+. \u00a7cLying about your age will result in a permanent ban."));
        net.minecraft.network.chat.MutableComponent confirm = Component.literal("\u00a7a\u00a7l[ I am 18 or older ]")
                .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/verify confirm")));
        net.minecraft.network.chat.MutableComponent deny = Component.literal("  \u00a7c\u00a7l[ I am under 18 ]")
                .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/verify deny")));
        sp.sendSystemMessage(confirm.append(deny));
    }

    // ========== XP AWARD + ACTION BAR ==========

    /**
     * Award XP for a skill, applying the Knowledge parent buff XP multiplier
     * for non-Knowledge skills. Sends action bar notification and level-up
     * announcement. Updates parent buff attribute modifiers on level change.
     *
     * <p>
     * Public so that mixin classes (different package) can call it.
     */
    public static void awardXp(ServerPlayer player, SkillData data, SkillType skill, double amount) {
        // Knowledge parent buff: XP multiplier for non-Knowledge skills
        if (skill.category() != SkillType.Category.KNOWLEDGE) {
            int knowledgeLevel = SkillManager.parentLevel(
                    SkillType.Category.KNOWLEDGE, data.getTypedXpMap(player.getUUID()));
            double multiplier = 1.0 + SkillManager.perkScale(knowledgeLevel, SmpConfig.CAP_KNOWLEDGE_XP);
            amount *= multiplier;
        }

        int oldLevel = data.getLevel(player.getUUID(), skill);
        double newTotal = data.addXp(player.getUUID(), skill, amount);
        int newLevel = SkillManager.levelFromXp(newTotal);

        // Action bar notification
        String bar = SkillManager.progressBar(SkillManager.progressFraction(newTotal), 10);
        String msg = skill.category().color() + "+" + (int) amount + " " + capitalize(skill.name())
                + " XP " + bar + " \u00a7f(Lv." + newLevel + ")";
        player.displayClientMessage(Component.literal(msg), true);

        // Level up announcement + parent buff update
        if (newLevel > oldLevel) {
            player.displayClientMessage(Component.literal(
                    "\u00a76\u00a7l\u2605 LEVEL UP! \u00a7r" + skill.category().color()
                            + capitalize(skill.name()) + " \u00a7fis now level \u00a7e" + newLevel + "\u00a7f!"),
                    false);
            player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                    1.0f, 1.0f);

            // Recalculate parent buff attribute modifiers on level change
            updateParentBuffs(player, data);
        }
    }

    // ========== PARENT BUFF ATTRIBUTES ==========

    /**
     * Apply persistent attribute modifiers based on parent category levels.
     * Called on level-up and player join.
     *
     * <ul>
     * <li><b>Industrial</b> → Movement Speed (multiplier)</li>
     * <li><b>Nature</b> → Max Health (flat HP; CAP_NATURE_HEALTH hearts max)</li>
     * <li><b>Combat</b> → Attack Damage (multiplier)</li>
     * <li><b>Knowledge</b> → XP multiplier (applied in awardXp, not here)</li>
     * </ul>
     */
    private static void updateParentBuffs(ServerPlayer player, SkillData data) {
        Map<SkillType, Double> xpMap = data.getTypedXpMap(player.getUUID());

        // Industrial → Speed buff (e.g. 0.20 = 20% speed at max)
        int industrialLevel = SkillManager.parentLevel(SkillType.Category.INDUSTRIAL, xpMap);
        double speedBonus = SkillManager.perkScale(industrialLevel, SmpConfig.CAP_INDUSTRIAL_SPEED);
        applyModifier(player, Attributes.MOVEMENT_SPEED,
                "quackedsmp", "industrial_speed", speedBonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        // Nature → Health buff: CAP_NATURE_HEALTH is in hearts; * 2 converts to HP units
        int natureLevel = SkillManager.parentLevel(SkillType.Category.NATURE, xpMap);
        double healthBonus = SkillManager.perkScale(natureLevel, SmpConfig.CAP_NATURE_HEALTH) * 2;
        applyModifier(player, Attributes.MAX_HEALTH,
                "quackedsmp", "nature_health", healthBonus,
                AttributeModifier.Operation.ADD_VALUE);

        // Combat → Damage buff (e.g. 0.15 = 15% damage at max)
        int combatLevel = SkillManager.parentLevel(SkillType.Category.COMBAT, xpMap);
        double damageBonus = SkillManager.perkScale(combatLevel, SmpConfig.CAP_COMBAT_DAMAGE);
        applyModifier(player, Attributes.ATTACK_DAMAGE,
                "quackedsmp", "combat_damage", damageBonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        // Defense sub-skill → Armor bonus (flat armor points)
        int defenseLevel = data.getLevel(player.getUUID(), SkillType.DEFENSE);
        double armorBonus = SkillManager.perkScale(defenseLevel, SmpConfig.CAP_DEFENSE_ARMOR);
        applyModifier(player, Attributes.ARMOR,
                "quackedsmp", "defense_armor", armorBonus,
                AttributeModifier.Operation.ADD_VALUE);
    }

    /**
     * Apply or update a single attribute modifier on the player.
     * Removes the old modifier (by ResourceLocation key) before adding the new one.
     */
    private static void applyModifier(ServerPlayer player,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            String namespace, String path, double value,
            AttributeModifier.Operation operation) {
        AttributeInstance attr = player.getAttribute(attribute);
        if (attr == null)
            return;

        Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
        attr.removeModifier(id);
        if (value > 0) {
            attr.addPermanentModifier(new AttributeModifier(id, value, operation));
        }
    }

    // ========== PASSIVE HELPERS ==========

    /** Double drop chance based on Industrial parent level, scaled to CAP_DOUBLE_DROP. */
    private static void applyDoubleDrop(ServerPlayer sp, SkillData data, BlockState state, BlockPos pos,
            ServerLevel level) {
        int parentLevel = SkillManager.parentLevel(SkillType.Category.INDUSTRIAL, data.getTypedXpMap(sp.getUUID()));
        double chance = SkillManager.perkScale(parentLevel, SmpConfig.CAP_DOUBLE_DROP);
        if (sp.getRandom().nextDouble() < chance) {
            Block.dropResources(state, level, pos, null, sp, sp.getMainHandItem());
            sp.displayClientMessage(Component.literal("\u00a7a\u26a1 Double Drop!"), true);
        }
    }

    /** Excavation: chance to find treasure items in dirt/sand. */
    private static void applyTreasureFind(ServerPlayer sp, SkillData data, BlockPos pos, ServerLevel level) {
        int excLevel = data.getLevel(sp.getUUID(), SkillType.EXCAVATION);
        double chance = excLevel * 0.003; // 0.3% per level, 30% at 100
        if (sp.getRandom().nextDouble() < chance) {
            Item[] treasures = { Items.GOLD_NUGGET, Items.IRON_NUGGET, Items.GUNPOWDER, Items.BONE, Items.FLINT };
            Item treasure = treasures[sp.getRandom().nextInt(treasures.length)];
            Block.popResource(level, pos, new ItemStack(treasure, 1));
            sp.displayClientMessage(Component.literal("\u00a76\u2726 Treasure!"), true);
        }
    }

    /** Woodcutting: leaves decay instantly near chopped log. */
    private static void applyLeafBlower(ServerPlayer sp, SkillData data, BlockPos pos, ServerLevel level) {
        int wcLevel = data.getLevel(sp.getUUID(), SkillType.WOODCUTTING);
        if (wcLevel < 20)
            return;

        int cleared = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos leafPos = pos.offset(dx, dy, dz);
                    BlockState leafState = level.getBlockState(leafPos);
                    if (leafState.is(BlockTags.LEAVES)) {
                        level.destroyBlock(leafPos, true, sp);
                        cleared++;
                    }
                }
            }
        }
        if (cleared > 0)
            sp.displayClientMessage(Component.literal("\u00a72\u2702 Leaves Cleared!"), true);
    }

    /** Farming: auto-replant crops on harvest. */
    private static void applyAutoReplant(ServerPlayer sp, SkillData data, BlockState state, BlockPos pos,
            ServerLevel level) {
        int farmLevel = data.getLevel(sp.getUUID(), SkillType.FARMING);
        double chance = farmLevel * 0.01; // 1% per level, 100% at 100
        if (sp.getRandom().nextDouble() < chance) {
            level.getServer().execute(() -> {
                if (level.getBlockState(pos).isAir()) {
                    level.setBlockAndUpdate(pos, state.getBlock().defaultBlockState());
                }
            });
            sp.displayClientMessage(Component.literal("\u00a72\u21ba Replanted!"), true);
        }
    }

    // ========== BLOCK CLASSIFICATION ==========

    private static boolean isOre(BlockState state) {
        return state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES) || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.COPPER_ORES);
    }

    private static double oreXp(BlockState state) {
        if (state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES))
            return 50;
        if (state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.LAPIS_ORES))
            return 20;
        if (state.is(BlockTags.IRON_ORES) || state.is(BlockTags.REDSTONE_ORES))
            return 10;
        if (state.is(BlockTags.COPPER_ORES))
            return 8;
        if (state.is(BlockTags.COAL_ORES))
            return 5;
        return 5;
    }

    private static boolean isStone(BlockState state) {
        Block b = state.getBlock();
        return b == Blocks.STONE || b == Blocks.DEEPSLATE || b == Blocks.ANDESITE
                || b == Blocks.DIORITE || b == Blocks.GRANITE || b == Blocks.TUFF
                || b == Blocks.CALCITE || b == Blocks.NETHERRACK || b == Blocks.BASALT
                || b == Blocks.BLACKSTONE || b == Blocks.END_STONE || b == Blocks.OBSIDIAN;
    }

    private static boolean isShovelBlock(BlockState state) {
        Block b = state.getBlock();
        return b == Blocks.DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.SAND
                || b == Blocks.RED_SAND || b == Blocks.GRAVEL || b == Blocks.CLAY
                || b == Blocks.SOUL_SAND || b == Blocks.SOUL_SOIL
                || b == Blocks.MYCELIUM || b == Blocks.PODZOL
                || b == Blocks.SNOW_BLOCK || b == Blocks.SNOW;
    }

    private static boolean isMatureCrop(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        // Melons and Pumpkins are always "mature" when present
        Block b = state.getBlock();
        return b == Blocks.MELON || b == Blocks.PUMPKIN;
    }

    private static double mobXp(LivingEntity mob) {
        // Boss mobs
        if (mob.getType() == net.minecraft.world.entity.EntityType.ENDER_DRAGON)
            return 500;
        if (mob.getType() == net.minecraft.world.entity.EntityType.WITHER)
            return 500;
        if (mob.getType() == net.minecraft.world.entity.EntityType.ELDER_GUARDIAN)
            return 100;
        if (mob.getType() == net.minecraft.world.entity.EntityType.WARDEN)
            return 200;
        // Standard hostiles
        if (mob.getType() == net.minecraft.world.entity.EntityType.CREEPER)
            return 15;
        if (mob.getType() == net.minecraft.world.entity.EntityType.ENDERMAN)
            return 20;
        if (mob.getType() == net.minecraft.world.entity.EntityType.BLAZE)
            return 20;
        if (mob.getType() == net.minecraft.world.entity.EntityType.GHAST)
            return 25;
        if (mob.getType() == net.minecraft.world.entity.EntityType.WITCH)
            return 20;
        return 10; // Default for zombie, skeleton, spider, etc.
    }

    private static String capitalize(String name) {
        if (name == null || name.isEmpty())
            return name;
        return name.charAt(0) + name.substring(1).toLowerCase();
    }
}
