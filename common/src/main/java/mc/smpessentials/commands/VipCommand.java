package mc.smpessentials.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.tier.PlayerTierData;
import mc.smpessentials.tier.TierService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

public final class VipCommand {
    private VipCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("vip")
                .requires(src -> CommandRegistrar.isOp(src))
                .then(Commands.literal("set")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("tier", IntegerArgumentType.integer(0))
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "player");
                                int tier    = IntegerArgumentType.getInteger(ctx, "tier");
                                MinecraftServer server = ctx.getSource().getServer();

                                if (tier > 0 && SmpConfig.TIERS.stream().noneMatch(t -> t.tier() == tier)) {
                                    ctx.getSource().sendFailure(Component.literal("Tier " + tier + " is not defined in config."));
                                    return 0;
                                }

                                ServerPlayer target = server.getPlayerList().getPlayerByName(name);
                                if (target == null) {
                                    ctx.getSource().sendFailure(Component.literal(name + " is not online. Player must be online to assign a tier."));
                                    return 0;
                                }

                                PlayerTierData.get(server).set(target.getUUID(), tier);
                                String tierName = TierService.getTierName(tier);
                                ctx.getSource().sendSystemMessage(Component.literal(
                                    tier > 0
                                        ? name + " set to " + tierName + " (Tier " + tier + ")."
                                        : name + "'s tier cleared."));
                                return 1;
                            }))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "player");
                            MinecraftServer server = ctx.getSource().getServer();
                            PlayerTierData data = PlayerTierData.get(server);

                            // Try online first
                            ServerPlayer online = server.getPlayerList().getPlayerByName(name);
                            if (online != null) {
                                if (data.getTier(online.getUUID()) == 0) {
                                    ctx.getSource().sendFailure(Component.literal(name + " has no tier."));
                                    return 0;
                                }
                                data.remove(online.getUUID());
                                ctx.getSource().sendSystemMessage(Component.literal(name + "'s tier removed."));
                                return 1;
                            }

                            // Offline: reverse-lookup UUID via name cache
                            UUID found = findUUIDByName(name, data, server);
                            if (found == null) {
                                ctx.getSource().sendFailure(Component.literal(name + " not found or has no tier."));
                                return 0;
                            }
                            data.remove(found);
                            ctx.getSource().sendSystemMessage(Component.literal(name + "'s tier removed."));
                            return 1;
                        })))
                .then(Commands.literal("list")
                    .executes(ctx -> {
                        MinecraftServer server = ctx.getSource().getServer();
                        Map<UUID, Integer> all = PlayerTierData.get(server).getAll();
                        if (all.isEmpty()) {
                            ctx.getSource().sendSystemMessage(Component.literal("No players have a tier."));
                        } else {
                            StringBuilder sb = new StringBuilder("Player tiers:");
                            for (Map.Entry<UUID, Integer> entry : all.entrySet()) {
                                String displayName = resolveDisplayName(entry.getKey(), server);
                                sb.append("\n  ").append(displayName)
                                  .append(" -> Tier ").append(entry.getValue())
                                  .append(" (").append(TierService.getTierName(entry.getValue())).append(")");
                            }
                            ctx.getSource().sendSystemMessage(Component.literal(sb.toString()));
                        }
                        return 1;
                    }))
                .then(Commands.literal("info")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "player");
                            MinecraftServer server = ctx.getSource().getServer();

                            ServerPlayer online = server.getPlayerList().getPlayerByName(name);
                            if (online == null) {
                                ctx.getSource().sendFailure(Component.literal(name + " must be online to view full tier info."));
                                return 0;
                            }

                            int tier   = TierService.getTier(online.getUUID(), server);
                            int earned = TierService.getEarnedTier(online);

                            StringBuilder sb = new StringBuilder("Tier info for " + name + ":");
                            sb.append("\n  Tier:   ").append(tier > 0
                                ? "Tier " + tier + " (" + TierService.getTierName(tier) + ")" : "None");
                            sb.append("\n  Earned: ").append(earned > 0
                                ? "Tier " + earned + " (" + TierService.getTierName(earned) + ")" : "None");
                            ctx.getSource().sendSystemMessage(Component.literal(sb.toString()));
                            return 1;
                        }))));
    }

    // Looks up a UUID for an offline player by scanning the tier entries and matching the cached display name.
    private static UUID findUUIDByName(String name, PlayerTierData data, MinecraftServer server) {
        for (UUID uuid : data.getAll().keySet()) {
            String display = resolveDisplayName(uuid, server);
            if (display.equalsIgnoreCase(name)) return uuid;
        }
        return null;
    }

    private static String resolveDisplayName(UUID uuid, MinecraftServer server) {
        var profile = server.services().nameToIdCache().get(uuid).orElse(null);
        if (profile != null && profile.name() != null) return profile.name();
        return uuid.toString();
    }
}
