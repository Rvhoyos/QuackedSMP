package mc.smpessentials.welcomebook;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

/**
 * Builds and hands out the welcome book. Every delivery path goes through here (the /guide
 * command, /smp help, kit rewards, rtp arrivals) so there is one book and one place that knows
 * how to turn the stored content into an item.
 */
public final class WelcomeBookService {
    private static final Logger LOGGER = LogManager.getLogger("WelcomeBook");
    private static final String BOOK_ITEM = "minecraft:written_book";

    private WelcomeBookService() {}

    public static boolean isEnabled() {
        return SmpConfig.WELCOME_BOOK_ENABLED
                && SmpConfig.WELCOME_BOOK_CONTENT != null
                && !SmpConfig.WELCOME_BOOK_CONTENT.isJsonNull();
    }

    /**
     * The book as an item, or empty when the feature is off or the stored content is unreadable.
     * Built per call rather than cached so an edit in the panel takes effect immediately.
     */
    public static Optional<ItemStack> build(ServerPlayer player) {
        if (!isEnabled()) return Optional.empty();

        com.google.gson.JsonObject stack = new com.google.gson.JsonObject();
        stack.addProperty("id", BOOK_ITEM);
        stack.addProperty("count", 1);
        com.google.gson.JsonObject components = new com.google.gson.JsonObject();
        components.add("minecraft:written_book_content", SmpConfig.WELCOME_BOOK_CONTENT);
        stack.add("components", components);

        return decode(player, stack);
    }

    /** Adds the book to the player's inventory, dropping it when there is no room. */
    public static void give(ServerPlayer player) {
        build(player).ifPresent(book -> {
            if (!player.getInventory().add(book)) {
                player.drop(book, false);
            }
        });
    }

    /**
     * Hands over the book and says so, or explains why not. Used by the commands, which owe the
     * player an answer either way.
     */
    public static void giveWithFeedback(ServerPlayer player) {
        if (!isEnabled()) {
            player.sendSystemMessage(Component.literal("§7The server guide is not available."));
            return;
        }
        Optional<ItemStack> book = build(player);
        if (book.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cThe server guide is misconfigured."));
            return;
        }
        if (!player.getInventory().add(book.get())) {
            player.drop(book.get(), false);
        }
        player.sendSystemMessage(Component.literal("§aHere is the server guide."));
    }

    private static Optional<ItemStack> decode(ServerPlayer player, JsonElement stack) {
        var ops = player.level().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        return ItemStack.CODEC.parse(ops, stack)
                .resultOrPartial(error -> LOGGER.warn("[WelcomeBook] Unreadable book: {}", error))
                .filter(item -> !item.isEmpty());
    }
}
