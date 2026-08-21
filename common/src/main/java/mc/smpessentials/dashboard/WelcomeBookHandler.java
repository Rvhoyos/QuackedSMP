package mc.smpessentials.dashboard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.smpessentials.config.ConfigIO;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;

import java.util.Map;

import static mc.smpessentials.dashboard.AdminHandler.err;

/**
 * HTTP routes for the welcome book. The book is stored once and handed out by several paths
 * (/guide, /smp help, kit rewards, rtp arrivals), so this is the one place it is edited.
 *
 * The content travels as minecraft:written_book_content JSON, the same shape the panel's book
 * editor already reads and writes for arrival and kit items.
 */
public final class WelcomeBookHandler {

    private static final String BOOK_ITEM = "minecraft:written_book";

    private WelcomeBookHandler() {}

    /** GET /api/admin/welcomebook. */
    public static String handleGet(String method, Map<String, String> headers, String body,
                                   MinecraftServer server) {
        String denied = ItemHandler.deny(method, "GET", headers, server);
        if (denied != null) return denied;

        JsonObject out = new JsonObject();
        out.addProperty("enabled", SmpConfig.WELCOME_BOOK_ENABLED);
        out.add("content", SmpConfig.WELCOME_BOOK_CONTENT);
        return out.toString();
    }

    /** POST /api/admin/welcomebook/save. */
    public static String handleSave(String method, Map<String, String> headers, String body,
                                    MinecraftServer server) {
        String denied = ItemHandler.deny(method, "POST", headers, server);
        if (denied != null) return denied;

        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            if (req.has("enabled")) {
                SmpConfig.WELCOME_BOOK_ENABLED = req.get("enabled").getAsBoolean();
            }

            if (req.has("content") && !req.get("content").isJsonNull()) {
                JsonObject content = req.getAsJsonObject("content");
                String problem = describeIfUnreadable(content, server);
                if (problem != null) return err(400, "Book rejected by the game: " + problem);
                SmpConfig.WELCOME_BOOK_CONTENT = content;
            }

            ConfigIO.save();
            return "{\"ok\":true}";
        } catch (RuntimeException e) {
            return err(400, "Invalid book: " + e.getMessage());
        }
    }

    /**
     * Decodes the book the way the game will, by building the stack it would become. Catching a
     * bad book here is what stops it failing silently later, when a player asks for it.
     */
    private static String describeIfUnreadable(JsonObject content, MinecraftServer server) {
        JsonObject stack = new JsonObject();
        stack.addProperty("id", BOOK_ITEM);
        stack.addProperty("count", 1);
        JsonObject components = new JsonObject();
        components.add("minecraft:written_book_content", content);
        stack.add("components", components);
        return ItemHandler.describeIfUnreadable(stack, server);
    }
}
