package mc.smpessentials.kits;

import mc.smpessentials.config.ConfigData;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.tier.TierService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class KitService {
    private KitService() {}

    public static List<ConfigData.KitDef> getAvailableKits(UUID uuid, MinecraftServer server) {
        int playerTier = TierService.getTier(uuid, server);
        List<ConfigData.KitDef> result = new ArrayList<>();
        for (ConfigData.KitDef kit : SmpConfig.KIT_DEFINITIONS) {
            if (playerTier >= kit.minTier) {
                result.add(kit);
            }
        }
        return result;
    }

    public static ConfigData.KitDef findKit(String name, UUID uuid, MinecraftServer server) {
        for (ConfigData.KitDef kit : getAvailableKits(uuid, server)) {
            if (kit.name.equalsIgnoreCase(name)) return kit;
        }
        return null;
    }

    // Returns remaining cooldown in seconds, or 0 if ready.
    public static long getRemainingCooldown(UUID uuid, MinecraftServer server) {
        long lastRedeem = KitData.get(server).getLastRedeem(uuid);
        if (lastRedeem == 0) return 0;
        long elapsedMs = System.currentTimeMillis() - lastRedeem;
        long cooldownMs = SmpConfig.KIT_COOLDOWN_SECONDS * 1000L;
        long remainingMs = cooldownMs - elapsedMs;
        return remainingMs > 0 ? (remainingMs + 999) / 1000 : 0;
    }

    public static void giveKit(ServerPlayer player, ConfigData.KitDef kit) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        KitData.get(server).setRedeemed(player.getUUID(), System.currentTimeMillis());

        giveArmorPiece(player, kit.armor.head, EquipmentSlot.HEAD);
        giveArmorPiece(player, kit.armor.chest, EquipmentSlot.CHEST);
        giveArmorPiece(player, kit.armor.legs, EquipmentSlot.LEGS);
        giveArmorPiece(player, kit.armor.feet, EquipmentSlot.FEET);

        for (ConfigData.KitItem ki : kit.items) {
            Item item = resolveItem(ki.item);
            if (item == null || item == Items.AIR) continue;
            ItemStack stack = new ItemStack(item, ki.count);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    private static void giveArmorPiece(ServerPlayer player, String itemId, EquipmentSlot slot) {
        if (itemId == null || itemId.isEmpty()) return;
        Item item = resolveItem(itemId);
        if (item == null || item == Items.AIR) return;
        ItemStack stack = new ItemStack(item, 1);

        if (player.getItemBySlot(slot).isEmpty()) {
            player.setItemSlot(slot, stack);
        } else if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static Item resolveItem(String id) {
        try {
            return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        } catch (Exception e) {
            return null;
        }
    }

    public static String formatDuration(long seconds) {
        if (seconds <= 0) return "now";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.isEmpty()) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
