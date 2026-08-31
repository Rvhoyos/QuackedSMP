package mc.smpessentials.keepinv;

import net.minecraft.resources.Identifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

// Persists UUIDs of players who opted out of keep-inventory. Default is keep; only opt-outs are stored.
// keep_inventory gamerule must stay ON; opted-out players get their inventory dropped manually on death.
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

    // Forces keep_inventory=true on startup while the feature is on; drops are then done by hand.
    public static void enforceGamerule(MinecraftServer server) {
        // Off means the mod stops touching death entirely, so the server's own gamerule stands.
        if (!mc.smpessentials.config.SmpConfig.KEEP_INV_ENABLED) return;
        GameRules rules = server.overworld().getGameRules();
        if (!((Boolean) rules.get(GameRules.KEEP_INVENTORY))) {
            rules.set(GameRules.KEEP_INVENTORY, true, server);
            mc.smpessentials.SmpUtilsMod.LOGGER.info("[QuackedSMP] keep_inventory gamerule forced ON by KeepInv system.");
        }
    }

    public static KeepInvSavedData get(net.minecraft.server.level.ServerLevel level) {
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

    // Drops all items and XP at the death position and clears them before respawn. Applies to a
    // player who opted out, and to every hardcore session member.
    public static void onPlayerDeath(ServerPlayer player) {
        KeepInvSavedData data = get((net.minecraft.server.level.ServerLevel) player.level());
        // Hardcore always drops, whatever the flag and whatever the player's own preference:
        // this is the only code that drops a hardcore player's in-session gear.
        boolean inHardcore = mc.smpessentials.hardcore.HardcoreSavedData
                .get(((net.minecraft.server.level.ServerLevel) player.level()).getServer())
                .getPlayerSessionName(player.getUUID()) != null;
        if (!inHardcore) {
            // Feature off: the vanilla gamerule governs this death, we add nothing.
            if (!mc.smpessentials.config.SmpConfig.KEEP_INV_ENABLED) return;
            // Player never opted out, so the forced gamerule already keeps their items.
            if (data.isKeeping(player.getUUID())) return;
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        var level = player.level();

        // Main inventory (hotbar slots 0-8 + main slots 9-35)
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                level.addFreshEntity(new ItemEntity(level, x, y, z, stack.copy()));
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }

        // Armor + offhand (MAINHAND is already covered by the inventory loop above)
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET,
                net.minecraft.world.entity.EquipmentSlot.OFFHAND
        }) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                level.addFreshEntity(new ItemEntity(level, x, y, z, stack.copy()));
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
        }

        // Drop XP orbs and zero out XP so the respawn transfer restores nothing
        int xp = player.totalExperience;
        if (xp > 0) {
            ExperienceOrb.award((net.minecraft.server.level.ServerLevel) level, player.position(), xp);
        }
        player.totalExperience = 0;
        player.experienceLevel = 0;
        player.experienceProgress = 0.0f;
    }
}
