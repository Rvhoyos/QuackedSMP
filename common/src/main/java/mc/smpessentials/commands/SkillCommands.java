package mc.smpessentials.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import mc.smpessentials.skills.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * /skills — opens the Book GUI overview
 * /skills <type> — shows detailed info for a skill in chat
 * /skills admin givexp <player> <skill> <amount> — debug command
 */
public final class SkillCommands {

    private SkillCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("skills")
                // /skills — open book
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .executes(SkillCommands::openBook)

                // /skills <skill name>
                .then(Commands.argument("skill", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (SkillType st : SkillType.values()) {
                                builder.suggest(st.name().toLowerCase());
                            }
                            return builder.buildFuture();
                        })
                        .executes(SkillCommands::showSkillDetail))

                // /skills admin givexp <player> <skill> <amount>
                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))
                        // /skills admin givexp <player> <skill> <amount>
                        .then(Commands.literal("givexp")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("skill", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (SkillType st : SkillType.values()) {
                                                        builder.suggest(st.name().toLowerCase());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(1))
                                                        .executes(SkillCommands::adminGiveXp)))))
                        // /skills admin setlevel <player> <skill> <level>
                        .then(Commands.literal("setlevel")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("skill", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (SkillType st : SkillType.values()) {
                                                        builder.suggest(st.name().toLowerCase());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands
                                                        .argument("level",
                                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                                        .integer(0, 100))
                                                        .executes(SkillCommands::adminSetLevel)))))));
    }

    private static int openBook(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer sp = (ServerPlayer) ctx.getSource().getEntity();
        SkillBookGui.open(sp);
        return 1;
    }

    private static int showSkillDetail(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer sp = (ServerPlayer) ctx.getSource().getEntity();
        String skillName = StringArgumentType.getString(ctx, "skill").toUpperCase();

        SkillType skill;
        try {
            skill = SkillType.valueOf(skillName);
        } catch (IllegalArgumentException e) {
            sp.sendSystemMessage(Component.literal("\u00a7cUnknown skill: " + skillName));
            return 0;
        }

        ServerLevel sl = (ServerLevel) sp.level();
        SkillData data = SkillData.get(sl);
        double xp = data.getXp(sp.getUUID(), skill);
        int level = SkillManager.levelFromXp(xp);
        double progress = SkillManager.progressFraction(xp);
        long cooldown = data.getCooldownRemaining(sp.getUUID(), skill);

        String bar = SkillManager.progressBar(progress, 15);
        long nextXp = level < SkillManager.MAX_LEVEL ? SkillManager.xpForLevel(level + 1) : 0;
        long xpInLevel = (long) (xp - SkillManager.totalXpForLevel(level));

        sp.sendSystemMessage(Component.literal(
                skill.category().color() + "\u00a7l" + capitalize(skill.name()) + " \u00a7r\u00a77— "
                        + skill.category().displayName()));
        sp.sendSystemMessage(Component.literal(
                "\u00a7fLevel: \u00a7e" + level + "\u00a77/\u00a7e" + SkillManager.MAX_LEVEL));
        sp.sendSystemMessage(Component.literal(
                "\u00a7fXP: \u00a7a" + (long) xp + " \u00a77(" + xpInLevel + "/" + nextXp + ")"));
        sp.sendSystemMessage(Component.literal(
                "\u00a7f" + bar + " \u00a77" + (int) (progress * 100) + "%"));

        if (cooldown > 0) {
            sp.sendSystemMessage(Component.literal(
                    "\u00a7fAbility: \u00a7c\u23F1 " + formatTime(cooldown)));
        } else if (level >= 10) {
            sp.sendSystemMessage(Component.literal(
                    "\u00a7fAbility: \u00a7a\u2714 Ready"));
        } else {
            sp.sendSystemMessage(Component.literal(
                    "\u00a7fAbility: \u00a77Unlocks at Lv.10"));
        }

        return 1;
    }

    private static int adminGiveXp(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String skillName = StringArgumentType.getString(ctx, "skill").toUpperCase();
            double amount = DoubleArgumentType.getDouble(ctx, "amount");

            SkillType skill;
            try {
                skill = SkillType.valueOf(skillName);
            } catch (IllegalArgumentException e) {
                ctx.getSource().sendFailure(Component.literal("Unknown skill: " + skillName));
                return 0;
            }

            ServerLevel sl = (ServerLevel) target.level();
            SkillData data = SkillData.get(sl);
            double newTotal = data.addXp(target.getUUID(), skill, amount);
            int newLevel = SkillManager.levelFromXp(newTotal);

            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7aGave " + (long) amount + " " + capitalize(skill.name())
                            + " XP to " + target.getName().getString()
                            + " (now Lv." + newLevel + ")"),
                    true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int adminSetLevel(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String skillName = StringArgumentType.getString(ctx, "skill").toUpperCase();
            int level = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "level");

            SkillType skill;
            try {
                skill = SkillType.valueOf(skillName);
            } catch (IllegalArgumentException e) {
                ctx.getSource().sendFailure(Component.literal("Unknown skill: " + skillName));
                return 0;
            }

            // Clamped between 0 and 100
            level = Math.max(0, Math.min(SkillManager.MAX_LEVEL, level));

            ServerLevel sl = (ServerLevel) target.level();
            SkillData data = SkillData.get(sl);

            // Calculate exact XP for this level
            // define totalXpForLevel(0) as 0.0
            double targetXp = (level == 0) ? 0.0 : SkillManager.totalXpForLevel(level);

            data.setXp(target.getUUID(), skill, targetXp);

            int finalLevel = level;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7aSet " + capitalize(skill.name())
                            + " level for " + target.getName().getString()
                            + " to " + finalLevel),
                    true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static String capitalize(String name) {
        if (name == null || name.isEmpty())
            return name;
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    private static String formatTime(long seconds) {
        if (seconds >= 3600)
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        if (seconds >= 60)
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }
}
