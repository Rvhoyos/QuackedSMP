package mc.smpessentials.keepinv;

import net.minecraft.resources.Identifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

// Persists UUIDs of players who opted out of keep-inventory. Default is keep; only opt-outs are stored.
// While the feature is on the mod owns the keep_inventory gamerule and holds it ON, and opted-out
// players get their inventory dropped manually on death.
public final class KeepInvSavedData extends SavedData {

    private final Set<UUID> optedOut = new HashSet<>();

    public KeepInvSavedData() {
    }

    private static KeepInvSavedData fromList(List<UUID> list) {
        KeepInvSavedData d = new KeepInvSavedData();
        d.optedOut.addAll(list);
        return d;
    }

    public static final Codec<KeepInvSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.listOf().optionalFieldOf("opted_out", List.of())
                    .forGetter(d -> List.copyOf(d.optedOut)))
            .apply(i, KeepInvSavedData::fromList));

    public static final SavedDataType<KeepInvSavedData> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace("quackedsmp_keepinv"),
            KeepInvSavedData::new,
            CODEC,
            DataFixTypes.LEVEL);

    public static KeepInvSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // The last flag value this class wrote to the gamerule, null if it never has. The mod owns
    // keep_inventory only while the feature is on. Turning the feature off is the one time we write
    // false; while it is off and we never wrote, the rule is the owner's and we leave it alone.
    private static Boolean appliedFlag = null;

    // Ownership starts fresh with each server instance.
    public static void enforceGamerule(MinecraftServer server) {
        appliedFlag = null;
        syncGamerule(server);
    }

    // Call after anything changes KEEP_INV_ENABLED. Writes the gamerule only when the flag moved.
    public static void syncGamerule(MinecraftServer server) {
        boolean enabled = mc.smpessentials.config.SmpConfig.KEEP_INV_ENABLED;
        boolean unchanged = appliedFlag != null && appliedFlag == enabled;
        boolean neverOwned = appliedFlag == null && !enabled;
        appliedFlag = enabled;
        if (unchanged || neverOwned) return;

        GameRules rules = server.getGameRules();
        if (rules.get(GameRules.KEEP_INVENTORY) == enabled) return;
        rules.set(GameRules.KEEP_INVENTORY, enabled, server);
        mc.smpessentials.SmpUtilsMod.LOGGER.info(
                "[QuackedSMP] keep_inventory gamerule set {} by the KeepInv feature.",
                enabled ? "ON" : "OFF");
    }

    public static KeepInvSavedData get(ServerLevel level) {
        return get(level.getServer());
    }

    public boolean isKeeping(UUID uuid) {
        return !optedOut.contains(uuid);
    }

    // Returns true if the preference changed. keep=true removes from opted-out; keep=false adds.
    public boolean setKeeping(UUID uuid, boolean keep) {
        boolean changed = keep ? optedOut.remove(uuid) : optedOut.add(uuid);
        if (changed) setDirty();
        return changed;
    }

    // Reproduces a vanilla death for a player the forced gamerule would otherwise protect: drops
    // their items and the vanilla share of their XP, then clears both before respawn. Applies to a
    // player who opted out, and to every hardcore session member.
    public static void onPlayerDeath(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        MinecraftServer server = level.getServer();

        // The manual drop exists only to undo a forced keep_inventory. With the rule off vanilla
        // drops items and awards XP itself, so anything added here duplicates it, hardcore included.
        if (!server.getGameRules().get(GameRules.KEEP_INVENTORY)) return;

        KeepInvSavedData data = get(server);
        // Hardcore always drops, whatever the flag and whatever the player's own preference:
        // this is the only code that drops a hardcore player's in-session gear.
        boolean inHardcore = mc.smpessentials.hardcore.HardcoreSavedData
                .get(server)
                .holdsSessionGear(player.getUUID());
        if (!inHardcore) {
            // Feature off: the gamerule is the owner's own, we add nothing.
            if (!mc.smpessentials.config.SmpConfig.KEEP_INV_ENABLED) return;
            // Player never opted out, and the rule was read above, so their items are kept.
            if (data.isKeeping(player.getUUID())) return;
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        // Slots 36 and up are the armour, offhand, body and saddle slots, so one pass covers
        // everything the player is holding and wearing. See Inventory.EQUIPMENT_SLOT_MAPPING.
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            // Curse of Vanishing destroys the item rather than dropping it, the same rule
            // Player.destroyVanishingCursedItems applies on a normal death.
            if (!EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                level.addFreshEntity(new ItemEntity(level, x, y, z, stack.copy()));
            }
            player.getInventory().setItem(i, ItemStack.EMPTY);
        }

        // Vanilla drops this much and deletes the rest, so a level 30 death yields 100 and not the
        // 1395 the player earned. Player.getBaseExperienceReward is the source of the formula, and
        // totalExperience must not be used: enchanting spends levels without lowering it.
        int xp = Math.min(player.experienceLevel * 7, 100);
        if (xp > 0) {
            ExperienceOrb.award(level, player.position(), xp);
        }
        player.totalExperience = 0;
        player.experienceLevel = 0;
        player.experienceProgress = 0.0f;
    }
}
