package mc.smpessentials.skills;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.*;
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

    private SkillEvents() {
    }

    public static void init() {
        registerBlockBreak();
        registerCombat();
        registerPlayerTick();
        registerFishing();
        registerPlayerJoin();

        SmpUtilsMod.LOGGER.info("QuackedSMP Skills system initialized");
    }

    // ========== BLOCK BREAK (Mining, Excavation, Woodcutting, Farming) ==========

    private static void registerBlockBreak() {
        BlockEvent.BREAK.register((level, pos, state, player, exp) -> {
            if (!(player instanceof ServerPlayer sp))
                return EventResult.pass();
            if (!(level instanceof ServerLevel sl))
                return EventResult.pass();

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

            return EventResult.pass();
        });
    }

    // ========== COMBAT (Melee, Archery, Defense) ==========

    private static void registerCombat() {
        // XP on kill
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity.level().isClientSide())
                return EventResult.pass();
            if (!(entity instanceof Monster mob))
                return EventResult.pass();

            Entity attacker = source.getEntity();
            if (!(attacker instanceof ServerPlayer sp))
                return EventResult.pass();

            ServerLevel sl = (ServerLevel) sp.level();
            SkillData data = SkillData.get(sl);

            double baseXp = mobXp(mob);

            // Determine melee vs archery from damage source
            if (!source.isDirect()) {
                // Projectile = Archery
                double dist = sp.distanceTo(mob);
                double bonus = dist > 30 ? 2.0 : 1.0; // distance bonus
                awardXp(sp, data, SkillType.ARCHERY, baseXp * bonus);
            } else {
                awardXp(sp, data, SkillType.MELEE, baseXp);
            }

            return EventResult.pass();
        });

        // Defense XP on damage taken + Bleed passive on attack
        EntityEvent.LIVING_HURT.register((entity, source, amount) -> {
            if (entity.level().isClientSide())
                return EventResult.pass();

            // Defense: player takes damage
            if (entity instanceof ServerPlayer victim) {
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
                    }

                    // Combat parent damage buff applied via attribute would be better,
                    // but for simplicity we don't modify the event amount here.
                    // Parent buff is handled in ActiveAbilities or a separate tick.
                }
            }

            return EventResult.pass();
        });
    }

    // ========== AGILITY (Movement) ==========

    private static void registerPlayerTick() {
        TickEvent.PLAYER_POST.register(player -> {
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

                        // Fall damage reduction passive
                        int agiLevel = data.getLevel(uuid, SkillType.AGILITY);
                        if (agiLevel > 0 && sp.fallDistance > 3) {
                            double reduction = agiLevel * 0.005; // 0.5% per level
                            // We can't easily modify fall damage here,
                            // so we apply Slow Falling briefly on high falls
                            if (sp.fallDistance > 5 && sp.getRandom().nextDouble() < reduction) {
                                sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20, 0, false, false));
                            }
                        }
                    }
                    distanceAccum.put(uuid, accum);
                }
            }
            lastPositions.put(uuid, current);

            // Agility Dash Trigger: Sprint + Sneak (Shift)
            // Ideally we'd use Jump, but detecting jump start server-side is tricky without
            // mixins.
            // Sprint + Sneak is a unique combo (normally stops sprint).
            if (sp.isSprinting() && sp.isShiftKeyDown() && !sp.onGround()) {
                // The player is seemingly "rocket jumping" or dash-jumping
                ServerLevel sl = (ServerLevel) sp.level();
                mc.smpessentials.skills.ActiveAbilities.tryActivateDash(sp, SkillData.get(sl), uuid);
            }
        });

        // Clean up on logout
        PlayerEvent.PLAYER_QUIT.register(player -> {
            lastPositions.remove(player.getUUID());
            distanceAccum.remove(player.getUUID());
        });
    }

    // ========== FISHING ==========

    private static void registerFishing() {
        // Fishing XP is handled by FishingHookMixin — no listener needed here.
        // The mixin injects into FishingHook.retrieve() and awards 15 XP on catch.
    }

    /**
     * Register listener for player join to apply parent buffs on login.
     * Also applies Knowledge XP multiplier context for future awards.
     */
    private static void registerPlayerJoin() {
        PlayerEvent.PLAYER_JOIN.register(player -> {
            if (player instanceof ServerPlayer sp) {
                ServerLevel sl = (ServerLevel) sp.level();
                SkillData data = SkillData.get(sl);
                updateParentBuffs(sp, data);
            }
        });
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
            player.playNotifySound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);

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
     * <li><b>Nature</b> → Max Health (flat HP, up to config cap × 20 HP)</li>
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

        // Nature → Health buff (e.g. 20 extra HP = 10 hearts at max)
        int natureLevel = SkillManager.parentLevel(SkillType.Category.NATURE, xpMap);
        double healthBonus = SkillManager.perkScale(natureLevel, SmpConfig.CAP_NATURE_HEALTH) * 20;
        applyModifier(player, Attributes.MAX_HEALTH,
                "quackedsmp", "nature_health", healthBonus,
                AttributeModifier.Operation.ADD_VALUE);

        // Combat → Damage buff (e.g. 0.15 = 15% damage at max)
        int combatLevel = SkillManager.parentLevel(SkillType.Category.COMBAT, xpMap);
        double damageBonus = SkillManager.perkScale(combatLevel, SmpConfig.CAP_COMBAT_DAMAGE);
        applyModifier(player, Attributes.ATTACK_DAMAGE,
                "quackedsmp", "combat_damage", damageBonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
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

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        attr.removeModifier(id);
        if (value > 0) {
            attr.addPermanentModifier(new AttributeModifier(id, value, operation));
        }
    }

    // ========== PASSIVE HELPERS ==========

    /** Double drop chance based on parent category level. */
    private static void applyDoubleDrop(ServerPlayer sp, SkillData data, BlockState state, BlockPos pos,
            ServerLevel level) {
        int parentLevel = SkillManager.parentLevel(SkillType.Category.INDUSTRIAL, data.getTypedXpMap(sp.getUUID()));
        double chance = SkillManager.perkScale(parentLevel, SmpConfig.CAP_INDUSTRIAL_SPEED);
        if (sp.getRandom().nextDouble() < chance) {
            // Drop an extra copy of what the block drops
            Block.dropResources(state, level, pos, null, sp, sp.getMainHandItem());
        }
    }

    /** Excavation: chance to find treasure items in dirt/sand. */
    private static void applyTreasureFind(ServerPlayer sp, SkillData data, BlockPos pos, ServerLevel level) {
        int excLevel = data.getLevel(sp.getUUID(), SkillType.EXCAVATION);
        double chance = excLevel * 0.003; // 0.3% per level, 30% at 100
        if (sp.getRandom().nextDouble() < chance) {
            // Random treasure
            Item[] treasures = { Items.GOLD_NUGGET, Items.IRON_NUGGET, Items.GUNPOWDER, Items.BONE, Items.FLINT };
            Item treasure = treasures[sp.getRandom().nextInt(treasures.length)];
            Block.popResource(level, pos, new ItemStack(treasure, 1));
        }
    }

    /** Woodcutting: leaves decay instantly near chopped log. */
    private static void applyLeafBlower(ServerPlayer sp, SkillData data, BlockPos pos, ServerLevel level) {
        int wcLevel = data.getLevel(sp.getUUID(), SkillType.WOODCUTTING);
        if (wcLevel < 20)
            return; // unlocks at level 20

        // Scan 3-block radius for leaves
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos leafPos = pos.offset(dx, dy, dz);
                    BlockState leafState = level.getBlockState(leafPos);
                    if (leafState.is(BlockTags.LEAVES)) {
                        level.destroyBlock(leafPos, true, sp);
                    }
                }
            }
        }
    }

    /** Farming: auto-replant crops on harvest. */
    private static void applyAutoReplant(ServerPlayer sp, SkillData data, BlockState state, BlockPos pos,
            ServerLevel level) {
        int farmLevel = data.getLevel(sp.getUUID(), SkillType.FARMING);
        double chance = farmLevel * 0.01; // 1% per level, 100% at 100
        if (sp.getRandom().nextDouble() < chance) {
            // Schedule replant on next tick (block is being broken this tick)
            level.getServer().execute(() -> {
                if (level.getBlockState(pos).isAir()) {
                    // Re-place the crop at age 0
                    level.setBlockAndUpdate(pos, state.getBlock().defaultBlockState());
                }
            });
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
