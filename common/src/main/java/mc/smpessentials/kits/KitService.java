package mc.smpessentials.kits;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import mc.smpessentials.config.ConfigData;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.tier.TierService;
import mc.smpessentials.welcomebook.WelcomeBookService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class KitService {
    private static final Logger LOGGER = LogManager.getLogger("KitService");

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
            decode(player, ki == null ? null : ki.stack).ifPresent(stack -> {
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            });
        }

        if (kit.giveWelcomeBook) {
            WelcomeBookService.give(player);
        }
    }

    private static void giveArmorPiece(ServerPlayer player, JsonElement stored, EquipmentSlot slot) {
        decode(player, stored).ifPresent(stack -> {
            if (player.getItemBySlot(slot).isEmpty()) {
                player.setItemSlot(slot, stack);
            } else if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        });
    }

    /**
     * Rebuilds a stack from its ItemStack.CODEC JSON. Components ride along, which is how a kit
     * can hand out a written book or an enchanted helmet rather than a plain item id.
     */
    private static Optional<ItemStack> decode(ServerPlayer player, JsonElement stored) {
        if (stored == null || stored.isJsonNull()) return Optional.empty();
        var ops = player.level().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        return ItemStack.CODEC.parse(ops, stored)
                .resultOrPartial(error -> LOGGER.warn("[Kits] Bad item in config: {}", error))
                .filter(stack -> !stack.isEmpty());
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
