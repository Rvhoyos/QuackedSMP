package mc.smpessentials.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.skills.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * /skills                          — concise overview of all your skills
 * /skills gui                      — open the book GUI
 * /skills view <player>            — view another player's skills
 * /skills top [page]               — overall leaderboard
 * /skills top <skillname> [page]   — per-skill leaderboard
 * /skills <skillname>              — detailed info for one skill
 * /skills admin givexp/setlevel    — OP debug commands
 */
public final class SkillCommands {

    private SkillCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("skills")
                .requires(src -> src.getEntity() instanceof ServerPlayer)

                // /skills — concise overview
                .executes(ctx -> showAllSkills(ctx, (ServerPlayer) ctx.getSource().getEntity()))

                // /skills view <player> — view another player's overview
                .then(Commands.literal("view")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> showAllSkills(ctx, EntityArgument.getPlayer(ctx, "player")))))

                // /skills top — leaderboard
                .then(Commands.literal("top")
                        .executes(ctx -> showLeaderboard(ctx, null, 1))
                        // /skills top <page>
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> showLeaderboard(ctx, null,
                                        IntegerArgumentType.getInteger(ctx, "page"))))
                        // /skills top <skillname> [page]
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (SkillType st : SkillType.values())
                                        builder.suggest(st.name().toLowerCase());
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> showLeaderboard(ctx,
                                        StringArgumentType.getString(ctx, "skill"), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> showLeaderboard(ctx,
                                                StringArgumentType.getString(ctx, "skill"),
                                                IntegerArgumentType.getInteger(ctx, "page"))))))

                // /skills admin givexp / setlevel
                .then(Commands.literal("admin")
                        .requires(src -> Commands.LEVEL_GAMEMASTERS.check(src.permissions()))
                        .then(Commands.literal("givexp")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("skill", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (SkillType st : SkillType.values())
                                                        builder.suggest(st.name().toLowerCase());
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(1))
                                                        .executes(SkillCommands::adminGiveXp)))))
                        .then(Commands.literal("setlevel")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("skill", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (SkillType st : SkillType.values())
                                                        builder.suggest(st.name().toLowerCase());
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("level",
                                                        IntegerArgumentType.integer(0, 100))
                                                        .executes(SkillCommands::adminSetLevel))))))

                // /skills <skillname> — detail for one skill (fallback string arg, tried last)
                .then(Commands.argument("skillname", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (SkillType st : SkillType.values())
                                builder.suggest(st.name().toLowerCase());
                            return builder.buildFuture();
                        })
                        .executes(SkillCommands::showSkillDetail)));
    }

    // ---- /skills (overview) ----

    private static int showAllSkills(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        ServerPlayer viewer = (ServerPlayer) ctx.getSource().getEntity();
        ServerLevel sl = (ServerLevel) target.level();
        SkillData data = SkillData.get(sl);
        UUID uuid = target.getUUID();

        boolean isSelf = viewer.getUUID().equals(uuid);
        String header = isSelf ? "Your Skills" : target.getName().getString() + "'s Skills";
        viewer.sendSystemMessage(Component.literal("\u00a77\u2501\u2501 \u00a7e" + header + " \u00a77\u2501\u2501"));

        int totalLevel = 0;
        for (SkillType.Category cat : SkillType.Category.values()) {
            SkillType[] skills = cat.skills();
            StringBuilder line = new StringBuilder();
            line.append(cat.color()).append(cat.symbol()).append(" \u00a7l")
                    .append(cat.displayName()).append("\u00a7r  ");
            for (int i = 0; i < skills.length; i++) {
                int level = data.getLevel(uuid, skills[i]);
                totalLevel += level;
                line.append("\u00a7f").append(capitalize(skills[i].name()))
                        .append(" \u00a7e").append(level);
                if (i < skills.length - 1) line.append(" \u00a78| ");
            }
            viewer.sendSystemMessage(Component.literal(line.toString()));
        }

        int rank = data.getOverallRank(uuid);
        viewer.sendSystemMessage(Component.literal(
                "\u00a77Total: \u00a7e" + totalLevel
                        + "  \u00a77Rank: \u00a7e#" + rank
                        + "  \u00a77\u00bb \u00a7f/skills top"));
        return 1;
    }

    // ---- /skills top ----

    private static int showLeaderboard(CommandContext<CommandSourceStack> ctx, String skillArg, int page) {
        ServerPlayer viewer = (ServerPlayer) ctx.getSource().getEntity();
        ServerLevel sl = (ServerLevel) viewer.level();
        SkillData data = SkillData.get(sl);

        int pageSize = Math.max(1, SmpConfig.LEADERBOARD_SIZE);
        int offset = (page - 1) * pageSize;
        int total = data.totalProfiles();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / pageSize));

        SkillType skill = null;
        if (skillArg != null) {
            try {
                skill = SkillType.valueOf(skillArg.toUpperCase());
            } catch (IllegalArgumentException e) {
                viewer.sendSystemMessage(Component.literal("\u00a7cUnknown skill: " + skillArg));
                return 0;
            }
        }

        List<SkillData.LeaderboardEntry> entries = skill == null
                ? data.getLeaderboard(pageSize, offset)
                : data.getSkillLeaderboard(skill, pageSize, offset);

        String title = skill == null
                ? "Skill Leaderboard"
                : capitalize(skill.name()) + " Leaderboard";

        viewer.sendSystemMessage(Component.literal(
                "\u00a77\u2501\u2501 \u00a7e" + title
                        + " \u00a77(Page \u00a7f" + page + "\u00a77/\u00a7f" + totalPages + "\u00a77) \u2501\u2501"));

        if (entries.isEmpty()) {
            viewer.sendSystemMessage(Component.literal("\u00a77No data yet."));
            return 1;
        }

        for (int i = 0; i < entries.size(); i++) {
            SkillData.LeaderboardEntry e = entries.get(i);
            int rank = offset + i + 1;
            String rankColor = rank == 1 ? "\u00a76" : rank == 2 ? "\u00a77" : rank == 3 ? "\u00a7c" : "\u00a7f";
            String name = data.getDisplayName(e.uuid());
            String levelLabel = skill == null ? "Total Lv" : capitalize(skill.name()) + " Lv";
            viewer.sendSystemMessage(Component.literal(
                    rankColor + "#" + rank + " \u00a7f" + name
                            + " \u00a77" + levelLabel + ": \u00a7a" + e.level()));
        }

        if (totalPages > 1)
            viewer.sendSystemMessage(Component.literal(
                    "\u00a77Page " + page + "/" + totalPages
                            + " \u2014 /skills top"
                            + (skill != null ? " " + skill.name().toLowerCase() : "")
                            + " " + (page + 1 <= totalPages ? (page + 1) : page)));
        return 1;
    }

    // ---- /skills <skillname> (detail) ----

    private static int showSkillDetail(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer sp = (ServerPlayer) ctx.getSource().getEntity();
        String skillName = StringArgumentType.getString(ctx, "skillname").toUpperCase();

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
                skill.category().color() + "\u00a7l" + capitalize(skill.name()) + " \u00a7r\u00a77\u2014 "
                        + skill.category().displayName()));
        sp.sendSystemMessage(Component.literal(
                "\u00a7fLevel: \u00a7e" + level + "\u00a77/\u00a7e" + SkillManager.MAX_LEVEL));
        sp.sendSystemMessage(Component.literal(
                "\u00a7fXP: \u00a7a" + (long) xp + " \u00a77(" + xpInLevel + "/" + nextXp + ")"));
        sp.sendSystemMessage(Component.literal(
                "\u00a7f" + bar + " \u00a77" + (int) (progress * 100) + "%"));

        if (cooldown > 0) {
            sp.sendSystemMessage(Component.literal("\u00a7fAbility: \u00a7c\u23f1 " + formatTime(cooldown)));
        } else if (level >= SmpConfig.getAbilityUnlockLevel(skill)) {
            sp.sendSystemMessage(Component.literal("\u00a7fAbility: \u00a7a\u2714 Ready"));
        } else {
            sp.sendSystemMessage(Component.literal(
                    "\u00a7fAbility: \u00a77Unlocks at Lv." + SmpConfig.getAbilityUnlockLevel(skill)));
        }
        return 1;
    }

    // ---- Admin commands ----

    private static int adminGiveXp(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String skillName = StringArgumentType.getString(ctx, "skill").toUpperCase();
            double amount = DoubleArgumentType.getDouble(ctx, "amount");
            SkillType skill = SkillType.valueOf(skillName);
            ServerLevel sl = (ServerLevel) target.level();
            SkillData data = SkillData.get(sl);
            double newTotal = data.addXp(target.getUUID(), skill, amount);
            int newLevel = SkillManager.levelFromXp(newTotal);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7aGave " + (long) amount + " " + capitalize(skill.name())
                            + " XP to " + target.getName().getString()
                            + " (now Lv." + newLevel + ")"), true);
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Unknown skill."));
            return 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int adminSetLevel(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String skillName = StringArgumentType.getString(ctx, "skill").toUpperCase();
            int level = IntegerArgumentType.getInteger(ctx, "level");
            SkillType skill = SkillType.valueOf(skillName);
            level = Math.max(0, Math.min(SkillManager.MAX_LEVEL, level));
            ServerLevel sl = (ServerLevel) target.level();
            SkillData data = SkillData.get(sl);
            double targetXp = level == 0 ? 0.0 : SkillManager.totalXpForLevel(level);
            data.setXp(target.getUUID(), skill, targetXp);
            int finalLevel = level;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7aSet " + capitalize(skill.name())
                            + " level for " + target.getName().getString()
                            + " to " + finalLevel), true);
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Unknown skill."));
            return 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ---- Helpers ----

    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    private static String formatTime(long seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        if (seconds >= 60) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }
}
