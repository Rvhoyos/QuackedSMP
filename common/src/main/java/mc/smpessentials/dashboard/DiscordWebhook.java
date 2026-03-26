package mc.smpessentials.dashboard;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

/**
 * Fires-and-forgets Discord webhook payloads for player events and chat.
 * Disabled when {@code discord.webhook_url} is blank in config.
 */
public final class DiscordWebhook {
    private DiscordWebhook() {}

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    // Catppuccin-adjacent Discord colours
    private static final int COLOR_GREEN = 0x57F287; // join
    private static final int COLOR_RED   = 0xED4245; // leave
    private static final int COLOR_BLUE  = 0x5865F2; // chat

    public static void sendJoin(String player) {
        if (!SmpConfig.DISCORD_JOIN_LEAVE) return;
        post(simpleEmbed("**" + player + "** joined the server", COLOR_GREEN));
    }

    public static void sendLeave(String player) {
        if (!SmpConfig.DISCORD_JOIN_LEAVE) return;
        post(simpleEmbed("**" + player + "** left the server", COLOR_RED));
    }

    public static void sendChat(String player, String message) {
        if (!SmpConfig.DISCORD_CHAT) return;
        post(chatEmbed(player, message));
    }

    // ── Payload builders ──────────────────────────────────────────────────────

    private static String simpleEmbed(String description, int color) {
        return "{\"embeds\":[{"
                + "\"description\":\"" + esc(description) + "\","
                + "\"color\":"  + color + ","
                + "\"timestamp\":\"" + Instant.now() + "\""
                + "}]}";
    }

    private static String chatEmbed(String player, String message) {
        return "{\"embeds\":[{"
                + "\"author\":{\"name\":\"" + esc(player) + "\"},"
                + "\"description\":\"" + esc(message) + "\","
                + "\"color\":" + COLOR_BLUE + ","
                + "\"timestamp\":\"" + Instant.now() + "\""
                + "}]}";
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private static void post(String json) {
        String url = SmpConfig.DISCORD_WEBHOOK_URL;
        if (url == null || url.isBlank()) return;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            CLIENT.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(e -> {
                        SmpUtilsMod.LOGGER.warn("[Dashboard] Discord webhook failed: {}", e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            SmpUtilsMod.LOGGER.warn("[Dashboard] Discord webhook error: {}", e.getMessage());
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        // Strip Minecraft § colour codes before sending to Discord
        String clean = s.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
        return clean.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
