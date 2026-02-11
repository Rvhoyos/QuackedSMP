package mc.smpessentials.chatfilter;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import mc.smpessentials.commands.CommandRegistrar;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * OP-only commands: /chatfilter add|remove|list|load|test|whitelist|strikes
 * Registered from CommandRegistrar to keep a single command hub.
 */
public final class ChatFilterCommands {
        private ChatFilterCommands() {
        }

        @SuppressWarnings("unchecked")
        public static void register(CommandDispatcher<?> rawDispatcher) {
                CommandDispatcher<CommandSourceStack> dispatcher = (CommandDispatcher<CommandSourceStack>) rawDispatcher;

                dispatcher.register(Commands.literal("chatfilter")
                                .requires(CommandRegistrar::isOp)

                                // --- add (greedyString for phrase support) ---
                                .then(Commands.literal("add")
                                                .then(Commands.argument("word", StringArgumentType.greedyString())
                                                                .executes(ctx -> {
                                                                        CommandSourceStack src = ctx.getSource();
                                                                        var data = ChatFilter.getData(src.getServer());
                                                                        String word = StringArgumentType.getString(ctx,
                                                                                        "word");
                                                                        boolean ok = data.add(word);
                                                                        if (ok) {
                                                                                src.sendSuccess(
                                                                                                () -> Component.literal(
                                                                                                                "Added to chat filter: "
                                                                                                                                + ChatFilter.normalize(
                                                                                                                                                word)),
                                                                                                false);
                                                                        } else {
                                                                                src.sendFailure(
                                                                                                Component.literal(
                                                                                                                "Already present: "
                                                                                                                                + ChatFilter.normalize(
                                                                                                                                                word)));
                                                                        }
                                                                        return ok ? 1 : 0;
                                                                })))

                                // --- remove (greedyString for phrase support) ---
                                .then(Commands.literal("remove")
                                                .then(Commands.argument("word", StringArgumentType.greedyString())
                                                                .executes(ctx -> {
                                                                        CommandSourceStack src = ctx.getSource();
                                                                        var data = ChatFilter.getData(src.getServer());
                                                                        String word = StringArgumentType.getString(ctx,
                                                                                        "word");
                                                                        boolean ok = data.remove(word);
                                                                        if (ok) {
                                                                                src.sendSuccess(
                                                                                                () -> Component.literal(
                                                                                                                "Removed from chat filter: "
                                                                                                                                + ChatFilter.normalize(
                                                                                                                                                word)),
                                                                                                false);
                                                                        } else {
                                                                                src.sendFailure(
                                                                                                Component.literal(
                                                                                                                "Not found: "
                                                                                                                                + ChatFilter.normalize(
                                                                                                                                                word)));
                                                                        }
                                                                        return ok ? 1 : 0;
                                                                })))

                                // --- list ---
                                .then(Commands.literal("list")
                                                .executes(ctx -> {
                                                        CommandSourceStack src = ctx.getSource();
                                                        Set<String> words = ChatFilter.getData(src.getServer())
                                                                        .snapshot();
                                                        String joined = words.isEmpty() ? "(empty)"
                                                                        : String.join(", ", words);
                                                        src.sendSuccess(() -> Component
                                                                        .literal("Chat filter words: " + joined),
                                                                        false);
                                                        return words.size();
                                                }))

                                // --- load ---
                                .then(Commands.literal("load")
                                                .executes(ctx -> {
                                                        var src = ctx.getSource();
                                                        try {
                                                                var res = ChatFilterConfig
                                                                                .mergeFromConfig(src.getServer());
                                                                src.sendSuccess(() -> Component
                                                                                .literal("Loaded " + res.added()
                                                                                                + " new words (total "
                                                                                                + res.total() + ")."),
                                                                                false);
                                                                for (String warn : res.warnings()) {
                                                                        src.sendFailure(Component.literal(
                                                                                        "\u00a7e[Warning] \u00a7f"
                                                                                                        + warn));
                                                                }
                                                                return res.added();
                                                        } catch (mc.smpessentials.config.ConfigIO.ConfigParseException e) {
                                                                src.sendFailure(Component
                                                                                .literal("\u00a7c[Error] \u00a7f"
                                                                                                + e.getMessage()));
                                                                return 0;
                                                        }
                                                }))

                                // --- test (dry-run: shows what the filter would do) ---
                                .then(Commands.literal("test")
                                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                                                .executes(ctx -> {
                                                                        CommandSourceStack src = ctx.getSource();
                                                                        var data = ChatFilter.getData(src.getServer());
                                                                        String input = StringArgumentType.getString(ctx,
                                                                                        "message");
                                                                        String filtered = ChatFilter.filterText(input,
                                                                                        data);
                                                                        src.sendSuccess(() -> Component.literal(
                                                                                        "\u00a76Original: \u00a7f"
                                                                                                        + input),
                                                                                        false);
                                                                        if (filtered.equals(input)) {
                                                                                src.sendSuccess(() -> Component.literal(
                                                                                                "\u00a7aFiltered: \u00a7f"
                                                                                                                + filtered
                                                                                                                + " \u00a77(no changes)"),
                                                                                                false);
                                                                        } else {
                                                                                src.sendSuccess(() -> Component.literal(
                                                                                                "\u00a7cFiltered: \u00a7f"
                                                                                                                + filtered),
                                                                                                false);
                                                                        }
                                                                        return 1;
                                                                })))

                                // --- whitelist add|remove|list ---
                                .then(Commands.literal("whitelist")
                                                .then(Commands.literal("add")
                                                                .then(Commands.argument("word",
                                                                                StringArgumentType.greedyString())
                                                                                .executes(ctx -> {
                                                                                        CommandSourceStack src = ctx
                                                                                                        .getSource();
                                                                                        var data = ChatFilter.getData(
                                                                                                        src.getServer());
                                                                                        String word = StringArgumentType
                                                                                                        .getString(ctx, "word");
                                                                                        boolean ok = data.addWhitelist(
                                                                                                        word);
                                                                                        if (ok) {
                                                                                                src.sendSuccess(() -> Component
                                                                                                                .literal(
                                                                                                                                "Whitelisted: " + ChatFilter
                                                                                                                                                .normalize(word)),
                                                                                                                false);
                                                                                        } else {
                                                                                                src.sendFailure(Component
                                                                                                                .literal(
                                                                                                                                "Already whitelisted: "
                                                                                                                                                + ChatFilter.normalize(
                                                                                                                                                                word)));
                                                                                        }
                                                                                        return ok ? 1 : 0;
                                                                                })))
                                                .then(Commands.literal("remove")
                                                                .then(Commands.argument("word",
                                                                                StringArgumentType.greedyString())
                                                                                .executes(ctx -> {
                                                                                        CommandSourceStack src = ctx
                                                                                                        .getSource();
                                                                                        var data = ChatFilter.getData(
                                                                                                        src.getServer());
                                                                                        String word = StringArgumentType
                                                                                                        .getString(ctx, "word");
                                                                                        boolean ok = data
                                                                                                        .removeWhitelist(
                                                                                                                        word);
                                                                                        if (ok) {
                                                                                                src.sendSuccess(() -> Component
                                                                                                                .literal(
                                                                                                                                "Removed from whitelist: "
                                                                                                                                                + ChatFilter.normalize(
                                                                                                                                                                word)),
                                                                                                                false);
                                                                                        } else {
                                                                                                src.sendFailure(Component
                                                                                                                .literal(
                                                                                                                                "Not in whitelist: "
                                                                                                                                                + ChatFilter.normalize(
                                                                                                                                                                word)));
                                                                                        }
                                                                                        return ok ? 1 : 0;
                                                                                })))
                                                .then(Commands.literal("list")
                                                                .executes(ctx -> {
                                                                        CommandSourceStack src = ctx.getSource();
                                                                        Set<String> wl = ChatFilter
                                                                                        .getData(src.getServer())
                                                                                        .whitelistSnapshot();
                                                                        String joined = wl.isEmpty() ? "(empty)"
                                                                                        : String.join(", ", wl);
                                                                        src.sendSuccess(() -> Component.literal(
                                                                                        "Whitelist: " + joined), false);
                                                                        return wl.size();
                                                                })))

                                // --- strikes [player] ---
                                .then(Commands.literal("strikes")
                                                .executes(ctx -> {
                                                        // No player specified: show all strikes
                                                        CommandSourceStack src = ctx.getSource();
                                                        Map<UUID, Integer> all = ChatFilterStrikeTracker.snapshot();
                                                        if (all.isEmpty()) {
                                                                src.sendSuccess(() -> Component.literal(
                                                                                "No strikes recorded this session."),
                                                                                false);
                                                                return 0;
                                                        }
                                                        StringBuilder sb = new StringBuilder(
                                                                        "\u00a76Strikes this session:\n");
                                                        for (var entry : all.entrySet()) {
                                                                // Try to resolve player name
                                                                var player = src.getServer().getPlayerList()
                                                                                .getPlayer(entry.getKey());
                                                                String name = player != null
                                                                                ? player.getName().getString()
                                                                                : entry.getKey().toString();
                                                                sb.append("\u00a7e").append(name).append(": \u00a7f")
                                                                                .append(entry.getValue()).append("\n");
                                                        }
                                                        String result = sb.toString().trim();
                                                        src.sendSuccess(() -> Component.literal(result), false);
                                                        return all.size();
                                                })
                                                .then(Commands.argument("player", EntityArgument.player())
                                                                .executes(ctx -> {
                                                                        CommandSourceStack src = ctx.getSource();
                                                                        ServerPlayer target = EntityArgument
                                                                                        .getPlayer(ctx, "player");
                                                                        int count = ChatFilterStrikeTracker
                                                                                        .get(target.getUUID());
                                                                        String name = target.getName().getString();
                                                                        src.sendSuccess(() -> Component.literal(
                                                                                        "\u00a7e" + name + " \u00a7fhas \u00a7e"
                                                                                                        + count
                                                                                                        + "\u00a7f strike(s) this session."),
                                                                                        false);
                                                                        return count;
                                                                }))));
        }
}
