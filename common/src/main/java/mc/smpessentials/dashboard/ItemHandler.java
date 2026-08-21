package mc.smpessentials.dashboard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.StringReader;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
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

    // ItemStack.CODEC clamps count to this range, so the importer clamps to the same.
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 99;

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
     * POST /api/admin/items/import. Takes a pasted command or bare item id and hands the stack
     * back as ItemStack.CODEC JSON.
     *
     * Vanilla's own parser does the work, so an item lifted from a command block arrives with its
     * components intact and no SNBT parser is needed in the browser.
     */
    public static String handleImport(String method, Map<String, String> headers, String body,
                                      MinecraftServer server) {
        String denied = deny(method, "POST", headers, server);
        if (denied != null) return denied;

        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String give = req.has("give") ? req.get("give").getAsString() : "";
            if (give.isBlank()) return err(400, "Nothing to import");

            ItemStack stack = parseItemSpec(give, server);
            var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            JsonElement encoded = ItemStack.CODEC.encodeStart(ops, stack).result()
                    .orElseThrow(() -> new IllegalArgumentException("that item cannot be stored"));

            JsonObject out = new JsonObject();
            out.addProperty("ok", true);
            out.add("stack", encoded);
            return out.toString();
        } catch (Exception e) {
            return err(400, "Could not read that item: " + e.getMessage());
        }
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

    /**
     * Pulls an item out of whatever was pasted. Nothing is executed: this only parses item syntax,
     * which is the same in every command that names one, so a line lifted from /give, /item or a
     * command block works, as does a bare item id.
     *
     * The item is found by trying to parse from each token in turn and keeping the first that
     * succeeds, which avoids hard-coding the argument layout of any one command.
     */
    private static ItemStack parseItemSpec(String raw, MinecraftServer server) throws Exception {
        String text = raw.trim();
        if (text.startsWith("/")) text = text.substring(1);

        ItemParser parser = new ItemParser(server.registryAccess());

        for (int start = 0; start < text.length(); start = nextToken(text, start)) {
            StringReader reader = new StringReader(text.substring(start));
            ItemInput input;
            try {
                input = parser.parse(reader);
            } catch (Exception notAnItemHere) {
                continue;
            }

            int requested = MIN_COUNT;
            reader.skipWhitespace();
            if (reader.canRead()) {
                try {
                    requested = reader.readInt();
                } catch (Exception ignored) {
                    // Trailing words that are not a count, e.g. the tail of an /item command.
                }
            }
            return input.createItemStack(clampCount(input, requested));
        }

        throw new IllegalArgumentException("no item found in that text");
    }

    /**
     * One stored entry is one stack, so a count above what the item stacks to is clamped rather
     * than refused: /give can hand a player three beds, but three beds are not a single stack.
     */
    private static int clampCount(ItemInput input, int requested) throws Exception {
        int limit = Math.min(MAX_COUNT, input.createItemStack(MIN_COUNT).getMaxStackSize());
        return Math.max(MIN_COUNT, Math.min(limit, requested));
    }

    /** Start of the token after the one at {@code from}, or the length when there is none. */
    private static int nextToken(String text, int from) {
        int space = text.indexOf(' ', from);
        if (space < 0) return text.length();
        while (space < text.length() && text.charAt(space) == ' ') space++;
        return space;
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
