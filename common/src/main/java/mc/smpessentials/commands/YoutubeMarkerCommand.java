package mc.smpessentials.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import mc.smpessentials.bluemap.MarkerRefresh;
import mc.smpessentials.bluemap.YoutubeMarkerData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** /youtube, pins a video marker on the web map at the player's position. OP only. */
public final class YoutubeMarkerCommand {
    private YoutubeMarkerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("youtube")
                .requires(CommandRegistrar::isOp)
                .then(Commands.literal("add")
                        .then(Commands.argument("url", StringArgumentType.string())
                                .then(Commands.argument("label", StringArgumentType.greedyString())
                                        .executes(ctx -> add(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "url"),
                                                StringArgumentType.getString(ctx, "label"))))))
                .then(Commands.literal("remove")
                        .executes(ctx -> remove(ctx.getSource())))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource()))));
    }

    private static int add(CommandSourceStack src, String url, String label) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();

        if (!YoutubeMarkerData.isAllowedUrl(url)) {
            src.sendFailure(Component.literal(
                    "§cThat is not a YouTube link. Use an https:// youtube.com or youtu.be URL."));
            return 0;
        }
        if (label.isBlank()) {
            src.sendFailure(Component.literal("§cGive the marker a label."));
            return 0;
        }

        YoutubeMarkerData.get(p.level().getServer()).add(new YoutubeMarkerData.Entry(
                p.getUUID(), label.trim(), url.trim(), p.level().dimension(),
                p.getX(), p.getY(), p.getZ()));
        MarkerRefresh.markDirty(MarkerRefresh.Layer.YOUTUBE);

        src.sendSystemMessage(Component.literal("§aPinned §f" + label.trim() + "§a on the map."));
        return 1;
    }

    private static int remove(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        var removed = YoutubeMarkerData.get(p.level().getServer())
                .removeNearest(p.level().dimension(), p.getX(), p.getY(), p.getZ());

        if (removed.isEmpty()) {
            src.sendFailure(Component.literal("§cNo video markers in this dimension."));
            return 0;
        }
        MarkerRefresh.markDirty(MarkerRefresh.Layer.YOUTUBE);
        src.sendSystemMessage(Component.literal("§aRemoved §f" + removed.get().label() + "§a."));
        return 1;
    }

    private static int list(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        var all = YoutubeMarkerData.get(p.level().getServer()).all();

        if (all.isEmpty()) {
            src.sendSystemMessage(Component.literal("§7No video markers pinned yet."));
            return 1;
        }

        MutableComponent msg = Component.literal("== Video Markers ==").withStyle(ChatFormatting.GOLD);
        for (var e : all) {
            msg.append(Component.literal("\n" + e.label()).withStyle(ChatFormatting.WHITE));
            msg.append(Component.literal(String.format("  %.0f, %.0f, %.0f in %s",
                    e.x(), e.y(), e.z(), e.dimension().identifier().getPath()))
                    .withStyle(ChatFormatting.GRAY));
        }
        src.sendSystemMessage(msg);
        return 1;
    }
}
