package mc.smpessentials.dashboard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.Map;

import static mc.smpessentials.dashboard.AdminHandler.err;

/**
 * Shared item and effect routes for the panel. Nothing here is specific to one feature: any editor
 * that stores an ItemStack or a potion effect (random teleport today, kits and votifier rewards
 * later) uses these rather than growing its own copy.
 *
 * Stacks travel as ItemStack.CODEC JSON, which is plain JSON the panel can walk, components and
 * all, so a written book's title, author and pages are directly editable in the browser.
 */
public final class ItemHandler {

    private ItemHandler() {}

    /**
     * GET /api/admin/registry. The effect and item ids this server actually has, so editors offer
     * real choices instead of asking an operator to type an id from memory. Read from the live
     * registries, so modded content appears too.
     */
    public static String handleRegistry(String method, Map<String, String> headers, String body,
                                        MinecraftServer server) {
        String denied = deny(method, "GET", headers, server);
        if (denied != null) return denied;

        JsonObject out = new JsonObject();
        out.add("effects", effectRows());
        out.add("items", itemIds());
        return out.toString();
    }

    /**
     * Decodes a stored stack the way the game will, returning the failure message when it cannot.
     * Editors call this before saving so a bad stack is caught at save time rather than silently
     * failing later when a player is meant to receive it.
     */
    public static String describeIfUnreadable(JsonElement stack, MinecraftServer server) {
        if (stack == null || stack.isJsonNull()) return null;
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var parsed = ItemStack.CODEC.parse(ops, stack);
        return parsed.isError() ? parsed.error().map(e -> e.message()).orElse("unreadable") : null;
    }

    private static com.google.gson.JsonArray effectRows() {
        com.google.gson.JsonArray effects = new com.google.gson.JsonArray();
        // Sorted so a grouped dropdown reads alphabetically inside each category rather than in
        // whatever order the registry happens to hold.
        BuiltInRegistries.MOB_EFFECT.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().identifier().toString()))
                .forEach(entry -> {
                    MobEffect effect = entry.getValue();
                    JsonObject row = new JsonObject();
                    row.addProperty("id", entry.getKey().identifier().toString());
                    row.addProperty("category", effect.getCategory().name());
                    // Instant effects apply once and ignore duration; editors say so.
                    row.addProperty("instant", effect.isInstantaneous());
                    row.addProperty("color", String.format("#%06X", effect.getColor() & 0xFFFFFF));
                    effects.add(row);
                });
        return effects;
    }

    private static com.google.gson.JsonArray itemIds() {
        com.google.gson.JsonArray items = new com.google.gson.JsonArray();
        BuiltInRegistries.ITEM.keySet().stream().map(Object::toString).sorted().forEach(items::add);
        return items;
    }

    /** Shared guard for the routes here. Returns an error body, or null to proceed. */
    static String deny(String method, String expected, Map<String, String> headers,
                       MinecraftServer server) {
        if (!expected.equals(method))          return err(405, "Method not allowed");
        if (!mc.smpessentials.config.SmpConfig.ADMIN_ENABLED) return err(403, "Admin panel disabled");
        if (!AdminAuth.isAuthorized(headers))  return err(403, "Unauthorized");
        if (server == null)                    return err(503, "Server not ready");
        return null;
    }
}
