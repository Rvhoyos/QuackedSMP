package mc.smpessentials.chatfilter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mc.smpessentials.config.ConfigIO;
import net.minecraft.server.MinecraftServer;

/** Loads chat filter words from quackedsmp.json and merges into SavedData. */
public final class ChatFilterConfig {
    private ChatFilterConfig() {}

    public static Result mergeFromConfig(MinecraftServer server) {
        ChatFilterSavedData data = ChatFilter.getData(server);
        int before = data.snapshot().size();

        JsonObject root = ConfigIO.readOrCreate();
        if (root.has("chatfilter") && root.get("chatfilter").isJsonObject()) {
            JsonObject cf = root.getAsJsonObject("chatfilter");
            if (cf.has("contents") && cf.get("contents").isJsonArray()) {
                JsonArray arr = cf.getAsJsonArray("contents");
                for (JsonElement el : arr) {
                    if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                        data.add(el.getAsString());
                    }
                }
            }
        }

        int after = data.snapshot().size();
        return new Result(Math.max(after - before, 0), after);
    }

    public record Result(int added, int total) {}
}
