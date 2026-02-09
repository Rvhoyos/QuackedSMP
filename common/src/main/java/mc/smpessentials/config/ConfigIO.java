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

    private ConfigIO() {
    }

    public static Path path() {
        return Platform.getConfigFolder().resolve(FILE_NAME);
    }

    /**
     * Ensures a file exists and returns its parsed JSON; writes a minimal default
     * if missing or empty.
     */
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
            if (obj == null)
                obj = defaultJson();
            boolean dirty = false;
            // Backfill missing sections for forward-compat
            if (!obj.has("chatfilter") || !obj.get("chatfilter").isJsonObject()) {
                obj.add("chatfilter", defaultJson().getAsJsonObject("chatfilter"));
                dirty = true;
            }
            if (!obj.has("messages") || !obj.get("messages").isJsonObject()) {
                obj.add("messages", defaultJson().getAsJsonObject("messages"));
                dirty = true;
            }
            if (!obj.has("max_claims")) {
                obj.addProperty("max_claims", 50);
                dirty = true;
            }
            if (!obj.has("tp_warmup")) {
                obj.addProperty("tp_warmup", 5);
                dirty = true;
            }
            if (!obj.has("welcome_message")) {
                obj.addProperty("welcome_message", "Welcome to QuackedSMP, {player}!");
                dirty = true;
            }
            if (!obj.has("rules")) {
                com.google.gson.JsonArray r = new com.google.gson.JsonArray();
                r.add("&e1. Be respectful.");
                r.add("&e2. No griefing inside claims.");
                r.add("&e3. Wilderness is dangerous (PvP enabled).");
                r.add("&e4. No cheating.");
                obj.add("rules", r);
                dirty = true;
            }
            if (!obj.has("periodic_messages")) {
                com.google.gson.JsonArray pm = new com.google.gson.JsonArray();
                pm.add("&b[Tip] &fUse &a/claim &fto protect your land!");
                pm.add("&b[Tip] &fDon't forget to set your &a/home&f!");
                obj.add("periodic_messages", pm);
                dirty = true;
            }
            if (!obj.has("message_interval")) {
                obj.addProperty("message_interval", 300);
                dirty = true;
            }
            if (!obj.has("vip_bonus_claims")) {
                obj.addProperty("vip_bonus_claims", 20);
                dirty = true;
            }
            if (!obj.has("allow_lava_wilderness")) {
                obj.addProperty("allow_lava_wilderness", false);
                dirty = true;
            }
            if (!obj.has("vips")) {
                obj.add("vips", new com.google.gson.JsonArray());
                dirty = true;
            }

            if (dirty) {
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
        root.addProperty("max_claims", 50);
        root.addProperty("tp_warmup", 5);
        root.addProperty("welcome_message", "&6Welcome to QuackedSMP, {player}!");
        root.addProperty("message_interval", 300);
        root.addProperty("vip_bonus_claims", 20);
        root.addProperty("allow_lava_wilderness", false);
        root.add("vips", new com.google.gson.JsonArray());

        com.google.gson.JsonArray rules = new com.google.gson.JsonArray();
        rules.add("&e1. Be respectful.");
        rules.add("&e2. No griefing inside claims.");
        rules.add("&e3. Wilderness is dangerous (PvP enabled).");
        rules.add("&e4. No cheating.");
        root.add("rules", rules);

        com.google.gson.JsonArray pm = new com.google.gson.JsonArray();
        pm.add("&b[Tip] &fUse &a/claim &fto protect your land!");
        pm.add("&b[Tip] &fDon't forget to set your &a/home&f!");
        root.add("periodic_messages", pm);

        JsonObject cf = new JsonObject();
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        arr.add("word1");
        arr.add("word2");
        cf.add("contents", arr);
        root.add("chatfilter", cf);

        JsonObject msgs = new JsonObject();
        // Claiming
        msgs.addProperty("claim.success", "Chunk claimed.");
        msgs.addProperty("claim.already_claimed", "This claim is already protected.");
        msgs.addProperty("claim.limit_reached", "You reached the claim limit ({count}).");
        msgs.addProperty("claim.spawn_protected", "You can’t claim inside spawn protection.");
        msgs.addProperty("unclaim.success", "Chunk unclaimed.");
        msgs.addProperty("unclaim.fail_ownership", "You don’t control this claim.");
        msgs.addProperty("claim.info.owned", "You own {count} chunk(s) in this dimension.");
        msgs.addProperty("claim.info.protected_by_you", "This chunk is protected by you.");
        msgs.addProperty("claim.info.protected", "This chunk is protected.");
        msgs.addProperty("claim.info.unclaimed", "Current chunk is unclaimed.");

        // Teleportation
        msgs.addProperty("tpr.sent", "Teleport request sent to {player}");
        msgs.addProperty("tpr.received", "{player} requested to teleport to you. Use /tpa accept or /tpa deny.");
        msgs.addProperty("tpr.self", "Cannot request teleport to self.");
        msgs.addProperty("tpr.cooldown", "A request was sent recently. Please wait.");
        msgs.addProperty("tpr.already_pending", "A request is already pending.");
        msgs.addProperty("tpr.queue_full", "That player has too many pending requests.");
        msgs.addProperty("tpa.no_pending", "No pending teleport requests.");
        msgs.addProperty("tpa.requester_offline", "Requester is no longer online.");
        msgs.addProperty("tpa.requester_busy", "Requester cannot be teleported right now.");
        msgs.addProperty("tpa.no_location", "No valid teleport location.");
        msgs.addProperty("tpa.teleporting_requester", "Teleporting {player} in 5 seconds...");
        msgs.addProperty("tpa.teleporting_to", "Teleported to {player}");
        msgs.addProperty("tpa.denied", "Teleport request denied by {player}");
        msgs.addProperty("tpa.denied_confirm", "Denied the oldest pending request.");

        root.add("messages", msgs);
        return root;
    }

}
