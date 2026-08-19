package mc.smpessentials.dims;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import mc.smpessentials.commands.CommandRegistrar;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Registers the /dim command tree for runtime dimension management.
 * Supports create (overworld/nether/end/ether), delete, list, setportal, and tp subcommands.
 * Biome list format: "namespace:path[:weight] ..." (weights are ratios, single biome pins the dim).
 * Flat layer format: "blockId:height ..." listed bottom to top.
 * Ether takes keyword island params in any order, all optional, parsed by GeneratorConfig:
 * threshold, radius min max, spacing, thickness min max, height minY maxY, structures true|false,
 * then biomes last.
 *
 * Each custom dim is automatically assigned {@code minecraft:glowstone} as its portal frame block
 * when first created. {@code /dim setportal} overrides this. One frame block per dim; one dim per
 * frame block. Build a nether-portal-shaped frame (2-21 wide, 3-21 tall) in a vanilla dim and
 * right-click the frame block with a water bucket to open the portal. Portals are bidirectional.
 */
public final class DimCommands {

    private DimCommands() {}

    // Registers the entire /dim command subtree. Called from CommandRegistrar on server startup.
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("dim")
                // /dim create <id> <type> [sub-params]
                .then(Commands.literal("create")
                    .requires(CommandRegistrar::isOp)
                    .then(Commands.argument("id", DimensionArgument.dimension())
                        // Suppress suggestions on <id> — user is naming a NEW dimension
                        .suggests((ctx, builder) -> builder.buildFuture())

                        // overworld [biomes [...] | flat ...]
                        .then(Commands.literal("overworld")
                            .executes(ctx -> executeCreate(ctx.getSource(),
                                    ctx.getArgument("id", Identifier.class),
                                    "overworld", Optional.empty()))
                            .then(Commands.literal("biomes")
                                // /dim create <id> overworld biomes  — default biomes
                                .executes(ctx -> executeCreate(ctx.getSource(),
                                        ctx.getArgument("id", Identifier.class),
                                        "overworld", Optional.empty()))
                                // /dim create <id> overworld biomes <b1[:w] ...>
                                .then(Commands.argument("biome_list", StringArgumentType.greedyString())
                                    .suggests(DimCommands::suggestBiomes)
                                    .executes(ctx -> executeCreate(ctx.getSource(),
                                            ctx.getArgument("id", Identifier.class),
                                            "overworld",
                                            Optional.of("biomes " + StringArgumentType.getString(ctx, "biome_list")))))
                            )
                            .then(Commands.literal("flat")
                                // /dim create <id> overworld flat  — default layers
                                .executes(ctx -> executeCreate(ctx.getSource(),
                                        ctx.getArgument("id", Identifier.class),
                                        "overworld",
                                        Optional.of("flat minecraft:bedrock:1 minecraft:stone:6 minecraft:dirt:2 minecraft:grass_block:1")))
                                // /dim create <id> overworld flat <block:h ...>
                                .then(Commands.argument("layers", StringArgumentType.greedyString())
                                    .suggests(DimCommands::suggestBlocks)
                                    .executes(ctx -> executeCreate(ctx.getSource(),
                                            ctx.getArgument("id", Identifier.class),
                                            "overworld",
                                            Optional.of("flat " + StringArgumentType.getString(ctx, "layers")))))
                            )
                        )

                        // ether [key value ...] -- see GeneratorConfig for the grammar
                        .then(Commands.literal("ether")
                            .executes(ctx -> executeCreate(ctx.getSource(),
                                    ctx.getArgument("id", Identifier.class),
                                    "ether", Optional.empty()))
                            .then(Commands.argument("params", StringArgumentType.greedyString())
                                .suggests(DimCommands::suggestEtherParams)
                                .executes(ctx -> executeEtherCreate(ctx,
                                        StringArgumentType.getString(ctx, "params"))))
                        )

                        // nether
                        .then(Commands.literal("nether")
                            .executes(ctx -> executeCreate(ctx.getSource(),
                                    ctx.getArgument("id", Identifier.class),
                                    "nether", Optional.empty()))
                        )

                        // end
                        .then(Commands.literal("end")
                            .executes(ctx -> executeCreate(ctx.getSource(),
                                    ctx.getArgument("id", Identifier.class),
                                    "end", Optional.empty()))
                        )
                    )
                )

                // /dim delete <id>
                .then(Commands.literal("delete")
                    .requires(CommandRegistrar::isOp)
                    .then(Commands.argument("id", DimensionArgument.dimension())
                        .suggests(DimCommands::suggestCustomDims)
                        .executes(ctx -> {
                            ServerLevel level = DimensionArgument.getDimension(ctx, "id");
                            String id = level.dimension().identifier().toString();
                            String err = DimManager.destroy(ctx.getSource().getServer(), id);
                            if (err != null) {
                                ctx.getSource().sendFailure(Component.literal(err));
                                return 0;
                            }
                            ctx.getSource().sendSystemMessage(
                                Component.literal("Deleted dimension: " + id));
                            return 1;
                        })
                    )
                )

                // /dim setportal <dim_id> <block_id>
                .then(Commands.literal("setportal")
                    .requires(CommandRegistrar::isOp)
                    .then(Commands.argument("id", DimensionArgument.dimension())
                        .suggests(DimCommands::suggestCustomDims)
                        .then(Commands.argument("block", DimensionArgument.dimension())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                    BuiltInRegistries.BLOCK.keySet().stream(), builder))
                            .executes(ctx -> {
                                ServerLevel dim    = DimensionArgument.getDimension(ctx, "id");
                                Identifier blockId = ctx.getArgument("block", Identifier.class);

                                Block block = BuiltInRegistries.BLOCK.getValue(blockId);
                                if (block == null || block == Blocks.AIR) {
                                    ctx.getSource().sendFailure(Component.literal(
                                            "Unknown block: " + blockId));
                                    return 0;
                                }

                                String dimId = dim.dimension().identifier().toString();
                                DimSavedData data = DimSavedData.get(ctx.getSource().getServer());

                                data.getDimForPortalBlock(blockId.toString()).ifPresent(existing -> {
                                    if (!existing.equals(dimId)) {
                                        ctx.getSource().sendSystemMessage(Component.literal(
                                                "Warning: " + blockId + " was wired to " + existing
                                                + " — reassigning to " + dimId));
                                        data.clearPortalBlock(existing);
                                    }
                                });

                                if (!data.setPortalBlock(dimId, blockId.toString())) {
                                    ctx.getSource().sendFailure(Component.literal(
                                            dimId + " is not a custom dimension."));
                                    return 0;
                                }

                                ctx.getSource().sendSystemMessage(Component.literal(
                                        "Portal frame for " + dimId + " set to " + blockId));
                                return 1;
                            })
                        )
                    )
                )

                // /dim list
                .then(Commands.literal("list")
                    .executes(ctx -> {
                        List<ResourceKey<Level>> all = DimManager.listAll(ctx.getSource().getServer());
                        StringBuilder sb = new StringBuilder("Dimensions (" + all.size() + "):");
                        for (ResourceKey<Level> key : all) {
                            sb.append("\n  ").append(key.identifier());
                        }
                        ctx.getSource().sendSystemMessage(Component.literal(sb.toString()));
                        return all.size();
                    })
                )

                // /dim tp <id>  and  /dim tp <player> <id>
                .then(Commands.literal("tp")
                    .requires(CommandRegistrar::isOp)
                    .then(Commands.argument("id", DimensionArgument.dimension())
                        .suggests(DimCommands::suggestAllDims)
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer self)) {
                                ctx.getSource().sendFailure(Component.literal("Must be a player."));
                                return 0;
                            }
                            ServerLevel dest = DimensionArgument.getDimension(ctx, "id");
                            return tpPlayerToDim(ctx.getSource(), self, dest);
                        })
                    )
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("id", DimensionArgument.dimension())
                            .suggests(DimCommands::suggestAllDims)
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                ServerLevel dest    = DimensionArgument.getDimension(ctx, "id");
                                return tpPlayerToDim(ctx.getSource(), target, dest);
                            })
                        )
                    )
                )
        );
    }

    // Parses the keyword island params and rejects the command if anything is wrong, so a typo
    // never quietly generates a different world. Stores the canonical form so a restart rebuilds
    // exactly this dimension.
    private static int executeEtherCreate(CommandContext<CommandSourceStack> ctx, String rawParams) {
        GeneratorConfig.Ether ether = GeneratorConfig.parseEther(rawParams);
        if (!ether.problems().isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(String.join("\n", ether.problems())));
            return 0;
        }
        return executeCreate(ctx.getSource(), ctx.getArgument("id", Identifier.class),
                "ether", Optional.of(ether.toConfigString()));
    }

    // Completes the ether params: the option keys not yet used, or sample values for the key
    // currently being filled in. Inside a biomes clause it hands over to the biome registry.
    private static CompletableFuture<Suggestions> suggestEtherParams(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        String[] tokens = remaining.isBlank() ? new String[0] : remaining.strip().split("\\s+");
        boolean typing = !remaining.isEmpty() && !Character.isWhitespace(remaining.charAt(remaining.length() - 1));
        int complete = tokens.length - (typing ? 1 : 0);

        // Walk the finished tokens to find whether a key is still waiting for values.
        Set<String> used = new HashSet<>();
        String pendingKey = null;
        int valuesLeft = 0;
        for (int i = 0; i < complete; i++) {
            String token = tokens[i].toLowerCase(Locale.ROOT);
            if (valuesLeft > 0) {
                valuesLeft--;
                continue;
            }
            int arity = GeneratorConfig.etherKeyArity(token);
            if (arity < 0) return suggestBiomes(ctx, builder);
            if (arity == 0) return builder.buildFuture();
            pendingKey = token;
            valuesLeft = arity;
            used.add(token);
        }

        List<String> candidates;
        String label;
        if (valuesLeft > 0) {
            int arity = GeneratorConfig.etherKeyArity(pendingKey);
            int slot = arity - valuesLeft;
            candidates = sampleValues(pendingKey, slot);
            label = arity == 2 ? pendingKey + (slot == 0 ? " min" : " max") : pendingKey;
        } else {
            candidates = GeneratorConfig.ETHER_KEYS.stream().filter(key -> !used.contains(key)).toList();
            label = "option";
        }

        int lastSpace = remaining.lastIndexOf(' ');
        String prefix  = lastSpace >= 0 ? remaining.substring(0, lastSpace + 1) : "";
        String current = typing ? tokens[tokens.length - 1] : "";
        com.mojang.brigadier.Message tooltip = () -> label;
        for (String candidate : candidates) {
            if (candidate.startsWith(current)) builder.suggest(prefix + candidate, tooltip);
        }
        return builder.buildFuture();
    }

    // Starting points offered in tab completion. Not limits: GeneratorConfig owns the valid ranges.
    private static List<String> sampleValues(String key, int slot) {
        return switch (key) {
            case "threshold"  -> List.of("-0.95", "-0.9", "-0.85", "-0.8", "-0.7");
            case "spacing"    -> List.of("4", "6", "8", "12", "16");
            case "structures" -> List.of("true", "false");
            case "radius"     -> slot == 0 ? List.of("20", "40", "60", "80") : List.of("60", "90", "120", "150");
            case "thickness"  -> slot == 0 ? List.of("8", "12", "20", "30") : List.of("20", "40", "60", "90");
            case "height"     -> slot == 0 ? List.of("0", "16", "32", "64") : List.of("128", "192", "256");
            default           -> List.of();
        };
    }

    // Shared handler for all /dim create subcommands. Returns 1 on success, 0 on failure (Brigadier convention).
    private static int executeCreate(CommandSourceStack source, Identifier dimId,
                                      String type, Optional<String> generatorConfig) {
        String err = DimManager.create(source.getServer(), dimId.toString(), type, generatorConfig);
        if (err != null) {
            source.sendFailure(Component.literal(err));
            return 0;
        }
        String label = dimId.toString();
        if (generatorConfig.isPresent()) label += " (" + generatorConfig.get() + ")";
        source.sendSystemMessage(Component.literal("Created dimension: " + label));
        source.sendSystemMessage(Component.literal("§6[!] A server restart is required for full block interaction (breaking/placing) in this dimension. Until then, it is read-only."));
        return 1;
    }

    // Teleports player to the dim's spawn origin. Schedules spawn island/portal generation on the next tick.
    // Returns 1 on success, 0 if teleport was rejected.
    private static int tpPlayerToDim(CommandSourceStack source, ServerPlayer player, ServerLevel dest) {
        BlockPos origin = DimManager.findSpawnOrigin(source.getServer(), dest);

        // Ensure spawn structure on next tick — re-query origin so chunks are loaded
        // and the portal lands at the actual terrain surface.
        final MinecraftServer server = source.getServer();
        DimSavedData.get(server)
                .getEntry(dest.dimension().identifier().toString())
                .ifPresent(e -> server.execute(() -> {
                    BlockPos freshOrigin = DimManager.findSpawnOrigin(server, dest);
                    if ("ether".equals(e.generatorType())) {
                        DimManager.ensureSpawnPlatform(dest, freshOrigin);
                    } else {
                        DimManager.ensureReturnPortal(dest, freshOrigin);
                    }
                }));

        BlockPos safe   = player.adjustSpawnLocation(dest, origin);

        boolean ok = player.teleportTo(
                dest,
                safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot(), false);

        if (ok) {
            source.sendSystemMessage(Component.literal(
                    "Teleported " + player.getName().getString()
                    + " to " + dest.dimension().identifier()));
            return 1;
        } else {
            source.sendFailure(Component.literal(
                    "Teleport failed for " + player.getName().getString()));
            return 0;
        }
    }

    // Tab-completes the current (last) token against the biome registry for a greedy biome-list argument.
    private static CompletableFuture<Suggestions> suggestBiomes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        String prefix  = lastSpace >= 0 ? remaining.substring(0, lastSpace + 1) : "";
        String current = lastSpace >= 0 ? remaining.substring(lastSpace + 1) : remaining;
        ctx.getSource().getServer().registryAccess()
                .lookupOrThrow(Registries.BIOME)
                .listElementIds()
                .map(ResourceKey::identifier)
                .filter(id -> id.toString().startsWith(current))
                .forEach(id -> builder.suggest(prefix + id));
        return builder.buildFuture();
    }

    // Suggests all active dimension IDs (vanilla and custom). Used for /dim tp.
    private static CompletableFuture<Suggestions> suggestAllDims(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        DimManager.listAll(ctx.getSource().getServer()).stream()
                .map(key -> key.identifier().toString())
                .filter(id -> id.startsWith(builder.getRemaining()))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    // Suggests only non-vanilla dimension IDs. Used for /dim delete and /dim setportal.
    private static CompletableFuture<Suggestions> suggestCustomDims(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        DimManager.listAll(ctx.getSource().getServer()).stream()
                .filter(key -> !key.equals(Level.OVERWORLD) && !key.equals(Level.NETHER) && !key.equals(Level.END))
                .map(key -> key.identifier().toString())
                .filter(id -> id.startsWith(builder.getRemaining()))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    // Tab-completes the current token against the block registry for a greedy flat-layer argument.
    private static CompletableFuture<Suggestions> suggestBlocks(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        String prefix  = lastSpace >= 0 ? remaining.substring(0, lastSpace + 1) : "";
        String current = lastSpace >= 0 ? remaining.substring(lastSpace + 1) : remaining;
        BuiltInRegistries.BLOCK.keySet().stream()
                .filter(id -> id.toString().startsWith(current))
                .forEach(id -> builder.suggest(prefix + id));
        return builder.buildFuture();
    }
}
