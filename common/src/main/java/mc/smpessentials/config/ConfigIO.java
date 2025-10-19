package mc.smpessentials.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared config file helpers for all packages. */
public final class ConfigIO {
    private static final String FILE_NAME = "quackedsmp.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ConfigIO() {}

    public static Path path() {
        return Platform.getConfigFolder().resolve(FILE_NAME);
    }

    /** Ensures a file exists and returns its parsed JSON; writes a minimal default if missing or empty. */
    public static JsonObject readOrCreate() {
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            if (!Files.exists(p) || Files.size(p) == 0) {
                JsonObject root = defaultJson();
                Files.writeString(p, GSON.toJson(root), StandardCharsets.UTF_8);
                return root;
            }
            String s = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject obj = GSON.fromJson(s, JsonObject.class);
            if (obj == null) obj = defaultJson();
            // Backfill missing sections for forward-compat
            if (!obj.has("chatfilter") || !obj.get("chatfilter").isJsonObject()) {
                obj.add("chatfilter", defaultJson().getAsJsonObject("chatfilter"));
                Files.writeString(p, GSON.toJson(obj), StandardCharsets.UTF_8);
            }
            return obj;
        } catch (IOException e) {
            // Fall back to default in-memory config when IO fails
            return defaultJson();
        }
    }

    private static JsonObject defaultJson() {
        JsonObject root = new JsonObject();
        JsonObject cf = new JsonObject();
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        arr.add("word1");
        arr.add("word2");
        cf.add("contents", arr);
        root.add("chatfilter", cf);
        return root;
    }

}
