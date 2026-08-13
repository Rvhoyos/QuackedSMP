package mc.smpessentials.dashboard;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mc.smpessentials.hardcore.HardcoreLeaderboard;
import mc.smpessentials.hardcore.HardcoreSavedData;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// Public (no-auth) dashboard endpoint serving the hardcore run-time leaderboard as JSON.
// Mirrors the skills leaderboard: derives everything from HardcoreLeaderboard on the server
// thread and sends raw millis for the client to format.
public final class HardcoreHandler {

    private HardcoreHandler() {}

    public static String handleLeaderboard(String method, Map<String, String> headers, String body,
                                           MinecraftServer server) {
        if (server == null) return AdminHandler.err(503, "Server not ready");

        CompletableFuture<String> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                HardcoreSavedData data = HardcoreSavedData.get(server);
                HardcoreLeaderboard board = HardcoreLeaderboard.of(data, System.currentTimeMillis());

                JsonObject root = new JsonObject();
                root.add("fastestWins", runArray(board.fastestWins()));
                root.add("longestRuns", runArray(board.longestRuns()));
                root.add("active", activeArray(board.activeRuns()));

                // Shared fun records + counters (no server-wide win-rate aggregate).
                JsonObject records = new JsonObject();
                records.add("bloodiestRun", runObj(board.bloodiestRun()));
                records.add("closestCall", runObj(board.closestCall()));
                records.add("biggestParty", runObj(board.biggestParty()));
                records.add("loneWolf", runObj(board.loneWolf()));
                records.add("veteran", playerObj(board.veteran(), server));
                records.add("champion", playerObj(board.champion(), server));
                records.addProperty("dragonsSlain", board.dragonsSlain());
                records.addProperty("bodyCount", board.bodyCount());
                records.addProperty("totalRuns", board.totalRuns());
                root.add("records", records);

                root.add("topPlayers", playersArray(board.topPlayers(), server));
                future.complete(root.toString());
            } catch (Exception ex) {
                future.complete(AdminHandler.err(500, AdminHandler.jsonEscape(String.valueOf(ex.getMessage()))));
            }
        });
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return AdminHandler.err(500, "Timeout");
        }
    }

    private static JsonArray runArray(List<HardcoreLeaderboard.RunEntry> entries) {
        JsonArray arr = new JsonArray();
        int limit = Math.min(10, entries.size());
        for (int i = 0; i < limit; i++) arr.add(runObj(entries.get(i)));
        return arr;
    }

    private static JsonObject runObj(HardcoreLeaderboard.RunEntry e) {
        if (e == null) return null; // JsonObject.add stores JsonNull for null elements
        JsonObject o = new JsonObject();
        o.addProperty("name", e.name());
        o.addProperty("creator", e.creatorName());
        o.addProperty("outcome", e.outcome().name());
        o.addProperty("durationMs", e.durationMillis());
        o.addProperty("peakPlayers", e.peakPlayers());
        o.addProperty("deaths", e.deaths());
        o.addProperty("participants", e.participantCount());
        o.addProperty("endedAt", e.endedAt());
        return o;
    }

    private static JsonArray activeArray(List<HardcoreLeaderboard.ActiveEntry> entries) {
        JsonArray arr = new JsonArray();
        for (HardcoreLeaderboard.ActiveEntry e : entries) {
            JsonObject o = new JsonObject();
            o.addProperty("name", e.name());
            o.addProperty("runMs", e.runMillis());
            o.addProperty("alive", e.aliveCount());
            o.addProperty("dead", e.deadCount());
            o.addProperty("deaths", e.deaths());
            o.addProperty("threshold", e.threshold());
            arr.add(o);
        }
        return arr;
    }

    private static JsonArray playersArray(List<HardcoreLeaderboard.PlayerStat> players, MinecraftServer server) {
        JsonArray arr = new JsonArray();
        int limit = Math.min(10, players.size());
        for (int i = 0; i < limit; i++) {
            JsonObject o = playerObj(players.get(i), server);
            if (o != null) arr.add(o);
        }
        return arr;
    }

    private static JsonObject playerObj(HardcoreLeaderboard.PlayerStat p, MinecraftServer server) {
        if (p == null) return null;
        JsonObject o = new JsonObject();
        o.addProperty("name", HardcoreSavedData.resolveName(server, p.player()));
        o.addProperty("runs", p.runs());
        o.addProperty("wins", p.wins());
        o.addProperty("losses", p.losses());
        o.addProperty("winRatePct", Math.round(p.winRate() * 100));
        o.addProperty("totalRunMs", p.totalRunMillis());
        o.addProperty("fastestWinMs", p.fastestWinMillis());
        o.addProperty("longestRunMs", p.longestRunMillis());
        return o;
    }
}
