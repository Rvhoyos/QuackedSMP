package mc.smpessentials.sidebar;

import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.hardcore.HardcoreSavedData;
import mc.smpessentials.util.TextUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// A transient second-MOTD board shown when a player enters normal survival: on join (unless they
// are in a hardcore session) and when they return to survival from a hardcore session. Content is
// configurable (title + lines) and self-suppresses for hardcore members so it never fights the
// hardcore board over the shared slot.
public final class WelcomeSidebarProvider implements SidebarProvider {

    // Server tick at which each player's show window ends.
    private final Map<UUID, Long> showUntilTick = new HashMap<>();

    private long now;

    @Override
    public void tick(MinecraftServer server) {
        now = server.getTickCount();
        if (!SmpConfig.WELCOME_SIDEBAR_ENABLED) showUntilTick.clear();
    }

    // Opens a transient window for the player. No-op if the feature is off.
    public void openFor(ServerPlayer player) {
        if (!SmpConfig.WELCOME_SIDEBAR_ENABLED) return;
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        long end = server.getTickCount() + Math.max(1, SmpConfig.WELCOME_SIDEBAR_SHOW_SECONDS) * 20L;
        showUntilTick.put(player.getUUID(), end);
    }

    @Override
    public Optional<SidebarContent> contentFor(ServerPlayer player) {
        if (!SmpConfig.WELCOME_SIDEBAR_ENABLED) return Optional.empty();
        UUID id = player.getUUID();

        Long until = showUntilTick.get(id);
        if (until == null || now >= until) {
            showUntilTick.remove(id);
            return Optional.empty();
        }
        // Never overlap the hardcore board.
        if (HardcoreSavedData.get(((ServerLevel) player.level()).getServer()).getPlayerSessionName(id) != null) {
            return Optional.empty();
        }
        return Optional.of(new SidebarContent(
                TextUtil.motd(SmpConfig.WELCOME_SIDEBAR_TITLE, player), buildLines(player)));
    }

    @Override
    public void onDisconnect(ServerPlayer player) {
        showUntilTick.remove(player.getUUID());
    }

    private static List<Component> buildLines(ServerPlayer player) {
        List<Component> lines = new ArrayList<>();
        for (String line : SmpConfig.WELCOME_SIDEBAR_LINES) {
            lines.add(TextUtil.motd(line, player));
        }
        return lines;
    }
}
