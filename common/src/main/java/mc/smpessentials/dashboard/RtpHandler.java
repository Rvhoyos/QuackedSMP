package mc.smpessentials.dashboard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.smpessentials.config.ConfigData;
import mc.smpessentials.config.ConfigIO;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Map;

import static mc.smpessentials.dashboard.AdminHandler.err;

/**
 * HTTP routes for the random teleport config tree. The panel edits it in the same shape
 * quackedsmp.json uses, so there is no second schema to keep in sync. Items and effects
 * themselves belong to {@link ItemHandler}, which every feature editor shares.
 */
public final class RtpHandler {

    private RtpHandler() {}

    /** GET /api/admin/rtp. The whole RTP config as the panel should show it. */
    public static String handleGet(String method, Map<String, String> headers, String body,
                                   MinecraftServer server) {
        String denied = ItemHandler.deny(method, "GET", headers, server);
        if (denied != null) return denied;

        JsonObject out = new JsonObject();
        out.addProperty("enabled", SmpConfig.RTP_ENABLED);
        out.add("config", ConfigIO.gson().toJsonTree(liveConfig()));
        return out.toString();
    }

    /** POST /api/admin/rtp/save. Replaces the config tree, then writes quackedsmp.json. */
    public static String handleSave(String method, Map<String, String> headers, String body,
                                    MinecraftServer server) {
        String denied = ItemHandler.deny(method, "POST", headers, server);
        if (denied != null) return denied;

        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            if (req.has("enabled")) {
                SmpConfig.RTP_ENABLED = req.get("enabled").getAsBoolean();
            }

            ConfigData.RtpConfig config =
                    ConfigIO.gson().fromJson(req.get("config"), ConfigData.RtpConfig.class);
            if (config == null) return err(400, "Missing config");

            String badItem = firstUnreadableItem(config, server);
            if (badItem != null) return err(400, "Item rejected by the game: " + badItem);

            SmpConfig.RTP_WARMUP_SECONDS = Math.max(0, config.warmupSeconds);
            SmpConfig.RTP_COOLDOWN_SECONDS = Math.max(0, config.cooldownSeconds);
            SmpConfig.RTP_PROFILES = config.profiles != null ? config.profiles : new ArrayList<>();

            ConfigIO.save();
            return "{\"ok\":true}";
        } catch (RuntimeException e) {
            return err(400, "Invalid config: " + e.getMessage());
        }
    }

    /**
     * Runs every arrival item back through ItemStack.CODEC and reports the first one the game
     * will not accept, or null when they all decode. This is what stops the book editor writing
     * something that only fails later, silently, when a player lands.
     */
    private static String firstUnreadableItem(ConfigData.RtpConfig config, MinecraftServer server) {
        if (config.profiles == null) return null;
        for (ConfigData.RtpProfile profile : config.profiles) {
            if (profile == null || profile.items == null) continue;
            for (ConfigData.RtpItem item : profile.items) {
                if (item == null) continue;
                String problem = ItemHandler.describeIfUnreadable(item.stack, server);
                if (problem != null) return profile.dimension + ": " + problem;
            }
        }
        return null;
    }

    /** Rebuilds the config tree from the loaded statics, which hold the live values. */
    private static ConfigData.RtpConfig liveConfig() {
        ConfigData.RtpConfig config = new ConfigData.RtpConfig();
        config.warmupSeconds = SmpConfig.RTP_WARMUP_SECONDS;
        config.cooldownSeconds = SmpConfig.RTP_COOLDOWN_SECONDS;
        config.profiles = SmpConfig.RTP_PROFILES;
        return config;
    }
}
