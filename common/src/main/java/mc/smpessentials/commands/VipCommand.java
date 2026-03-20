package mc.smpessentials.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import mc.smpessentials.config.ConfigIO;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class VipCommand {
    private VipCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("vip")
                        .requires(src -> CommandRegistrar.isOp(src))
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "player");
                                            if (SmpConfig.VIPS.contains(name)) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal(name + " is already a VIP."));
                                                return 0;
                                            }
                                            SmpConfig.VIPS.add(name);
                                            ConfigIO.save();
                                            ctx.getSource().sendSystemMessage(
                                                    Component.literal(name + " is now a VIP."));
                                            return 1;
                                        })))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "player");
                                            if (!SmpConfig.VIPS.remove(name)) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal(name + " is not a VIP."));
                                                return 0;
                                            }
                                            ConfigIO.save();
                                            ctx.getSource().sendSystemMessage(
                                                    Component.literal(name + " is no longer a VIP."));
                                            return 1;
                                        })))
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    if (SmpConfig.VIPS.isEmpty()) {
                                        ctx.getSource().sendSystemMessage(
                                                Component.literal("No VIPs configured."));
                                    } else {
                                        ctx.getSource().sendSystemMessage(
                                                Component.literal("VIPs: " + String.join(", ", SmpConfig.VIPS)));
                                    }
                                    return 1;
                                })));
    }
}
