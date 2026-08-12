package mc.smpessentials.hardcore;

import mc.smpessentials.config.SmpConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

// Owns the scoreboard team used to mark hardcore session members (nametag/tab identity).
// The team is seeded once with sensible defaults; admins then edit its color/prefix in the
// dashboard teams editor. Only the team name is config-driven.
public final class HardcoreTeam {

    private HardcoreTeam() {}

    // Returns the hardcore team, creating it with default color + prefix if it does not exist.
    public static PlayerTeam getOrCreate(Scoreboard scoreboard) {
        String name = SmpConfig.HARDCORE_TEAM_NAME;
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team != null) return team;

        team = scoreboard.addPlayerTeam(name);
        team.setColor(ChatFormatting.RED);
        team.setPlayerPrefix(Component.literal("☠ "));
        return team;
    }

    // Seeds the team at startup so it is editable in the dashboard before anyone joins hardcore.
    public static void seed(MinecraftServer server) {
        getOrCreate(server.getScoreboard());
    }
}
