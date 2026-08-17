package mc.smpessentials.sidebar;

import net.minecraft.network.chat.Component;

import java.util.List;

// One rendered sidebar: a title plus its lines, top line first. Value object so SidebarManager
// can compare against the last-rendered content and only re-send packets when it changes.
public record SidebarContent(Component title, List<Component> lines) {}
