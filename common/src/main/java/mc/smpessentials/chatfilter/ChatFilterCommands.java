package mc.smpessentials.chatfilter;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import mc.smpessentials.commands.CommandRegistrar;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * OP-only commands: /chatfilter add|remove|list
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
                                .then(Commands.literal("add")
                                                .then(Commands.argument("word", StringArgumentType.word())
                                                                .executes(ctx -> {
                                                                        CommandSourceStack src = ctx.getSource();
                                                                        var data = ChatFilter.getData(src.getServer());
                                                                        String word = StringArgumentType.getString(ctx,
                                                                                        "word");
                                                                        boolean ok = data.add(word);
                                                                        if (ok) {
                                                                                src.sendSuccess(
                                                                                                () -> Component
                                                                                                                .literal("Added to chat filter: "
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
                                .then(Commands.literal("remove")
                                                .then(Commands.argument("word", StringArgumentType.word())
                                                                .executes(ctx -> {
                                                                        CommandSourceStack src = ctx.getSource();
                                                                        var data = ChatFilter.getData(src.getServer());
                                                                        String word = StringArgumentType.getString(ctx,
                                                                                        "word");
                                                                        boolean ok = data.remove(word);
                                                                        if (ok) {
                                                                                src.sendSuccess(
                                                                                                () -> Component
                                                                                                                .literal("Removed from chat filter: "
                                                                                                                                + ChatFilter.normalize(
                                                                                                                                                word)),
                                                                                                false);
                                                                        } else {
                                                                                src.sendFailure(
                                                                                                Component.literal(
                                                                                                                "Not found: " + ChatFilter
                                                                                                                                .normalize(word)));
                                                                        }
                                                                        return ok ? 1 : 0;
                                                                })))
                                .then(Commands.literal("list")
                                                .executes(ctx -> {
                                                        CommandSourceStack src = ctx.getSource();
                                                        var words = ChatFilter.getData(src.getServer()).snapshot();
                                                        String joined = words.isEmpty() ? "(empty)"
                                                                        : String.join(", ", words);
                                                        src.sendSuccess(() -> Component
                                                                        .literal("Chat filter words: " + joined),
                                                                        false);
                                                        return words.size();
                                                }))
                                .then(Commands.literal("load")
                                                .executes(ctx -> {
                                                        var src = ctx.getSource();
                                                        var res = ChatFilterConfig.mergeFromConfig(src.getServer());
                                                        src.sendSuccess(() -> Component
                                                                        .literal("Loaded " + res.added()
                                                                                        + " new words (total "
                                                                                        + res.total() + ")."),
                                                                        false);
                                                        return res.added();
                                                })));
        }
}
