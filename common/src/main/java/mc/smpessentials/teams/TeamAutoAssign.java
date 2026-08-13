package mc.smpessentials.teams;

import mc.smpessentials.config.SmpConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Assigns players to scoreboard teams based on which dimension they are in.
// Config: SmpConfig.TEAM_AUTO_ASSIGN (team name -> list of dimension resource IDs).
public final class TeamAutoAssign {
    private TeamAutoAssign() {}

    // dim resource ID (e.g. "minecraft:overworld") -> team name
    private static volatile Map<String, String> reverseMap = Map.of();

    // Rebuilds the dim->team reverse lookup from config. Call on load/reload.
    public static void buildReverseMap() {
        Map<String, String> map = new HashMap<>();
        for (var entry : SmpConfig.TEAM_AUTO_ASSIGN.entrySet()) {
            String team = entry.getKey();
            List<String> dims = entry.getValue();
            for (String dim : dims) {
                map.put(dim, team);
            }
        }
        reverseMap = Map.copyOf(map);
    }

    // Called from platform dimension-change events.
    public static void onDimensionChange(ServerPlayer player, ResourceKey<Level> newDim) {
        apply(player, newDim.identifier().toString());
    }

    // Called every tick from the existing per-player loop.
    // Cost: one HashMap.get; short-circuits if the dim has no mapping.
    public static void tick(ServerPlayer player) {
        apply(player, player.level().dimension().identifier().toString());
    }

    private static void apply(ServerPlayer player, String dimId) {
        var scoreboard = player.level().getScoreboard();
        var current = scoreboard.getPlayersTeam(player.getScoreboardName());

        // Hardcore membership takes priority over the dimension team and fully owns the hardcore
        // team, so this stays self-correcting each tick.
        if (mc.smpessentials.config.SmpConfig.HARDCORE_TEAM_VISIBILITY
                && mc.smpessentials.hardcore.HardcoreSavedData.get(
                        ((net.minecraft.server.level.ServerLevel) player.level()).getServer())
                        .getPlayerSessionName(player.getUUID()) != null) {
            var hcTeam = mc.smpessentials.hardcore.HardcoreTeam.getOrCreate(scoreboard);
            if (current != hcTeam) scoreboard.addPlayerToTeam(player.getScoreboardName(), hcTeam);
            return;
        }
        // Not (or no longer) in a session: never leave a player stuck on the hardcore team, even
        // when the current dimension has no mapping.
        if (current != null && current.getName().equals(mc.smpessentials.config.SmpConfig.HARDCORE_TEAM_NAME)) {
            scoreboard.removePlayerFromTeam(player.getScoreboardName());
            current = null;
        }

        String targetTeam = reverseMap.get(dimId);
        if (targetTeam == null) return;
        if (current != null && current.getName().equals(targetTeam)) return;

        var team = scoreboard.getPlayerTeam(targetTeam);
        if (team == null) return;
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
    }
}
