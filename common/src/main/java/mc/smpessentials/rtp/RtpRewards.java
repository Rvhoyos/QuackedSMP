package mc.smpessentials.rtp;

import com.mojang.serialization.JsonOps;
import mc.smpessentials.config.ConfigData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

/**
 * Hands a profile's arrival package to a player: potion effects, then items. Knows nothing about
 * teleporting or about when a reward is due, only how to apply one.
 */
final class RtpRewards {
    private static final Logger LOGGER = LogManager.getLogger("RtpRewards");
    private static final int TICKS_PER_SECOND = 20;

    private final ConfigData.RtpProfile profile;

    RtpRewards(ConfigData.RtpProfile profile) {
        this.profile = profile;
    }

    void applyTo(ServerPlayer player) {
        for (ConfigData.RtpEffect effect : this.profile.effects) {
            applyEffect(player, effect);
        }
        for (ConfigData.RtpItem item : this.profile.items) {
            giveItem(player, item);
        }
        if (this.profile.giveWelcomeBook) {
            mc.smpessentials.welcomebook.WelcomeBookService.give(player);
        }
    }

    private static void applyEffect(ServerPlayer player, ConfigData.RtpEffect effect) {
        Identifier id = parseId(effect.effect);
        if (id == null) return;

        BuiltInRegistries.MOB_EFFECT.get(id).ifPresentOrElse(
                holder -> player.addEffect(new MobEffectInstance(holder,
                        Math.max(1, effect.seconds) * TICKS_PER_SECOND,
                        Math.max(0, effect.amplifier),
                        false,
                        effect.showParticles)),
                () -> LOGGER.warn("[RTP] Unknown effect in config: {}", effect.effect));
    }

    private static void giveItem(ServerPlayer player, ConfigData.RtpItem item) {
        decode(player, item).ifPresent(stack -> {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        });
    }

    /**
     * Rebuilds a stack from its ItemStack.CODEC JSON. Components ride along, which is how the
     * arrival package can include a written book rather than just a plain item id.
     */
    private static Optional<ItemStack> decode(ServerPlayer player, ConfigData.RtpItem item) {
        if (item.stack == null || item.stack.isJsonNull()) return Optional.empty();
        var ops = player.level().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        return ItemStack.CODEC.parse(ops, item.stack)
                .resultOrPartial(error -> LOGGER.warn("[RTP] Bad item in config: {}", error))
                .filter(stack -> !stack.isEmpty());
    }

    private static Identifier parseId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Identifier.parse(raw);
        } catch (RuntimeException e) {
            LOGGER.warn("[RTP] Bad id in config: {}", raw);
            return null;
        }
    }
}
