package mc.smpessentials.dims;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Registers the /dim command tree for runtime dimension management.
 * Supports create (overworld/nether/end/ether), delete, list, setportal, and tp subcommands.
 * Biome list format: "namespace:path[:weight] ..." (weights are ratios, single biome pins the dim).
 * Flat layer format: "blockId:height ..." listed bottom to top.
 * Ether accepts positional island params: threshold (-1..0), minRadius, maxRadius (5..500),
 * spacing (1..32). Each is optional -- stop at any valid point. Biomes via literal at any stop.
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

                        // ether [<threshold> [<minRadius> <maxRadius> [<spacing>]]] [biomes <list>]
                        .then(Commands.literal("ether")
                            .executes(ctx -> executeEtherCreate(ctx, Optional.empty(),
                                    Optional.empty(), Optional.empty(), Optional.empty()))
                            .then(etherBiomes())
                            .then(Commands.argument("threshold", FloatArgumentType.floatArg(-1.0f, 0.0f))
                                .suggests((ctx, b) -> suggestValues(b, "threshold", "-0.95", "-0.9", "-0.85", "-0.8", "-0.7"))
                                .executes(ctx -> executeEtherCreate(ctx,
                                        Optional.of(FloatArgumentType.getFloat(ctx, "threshold")),
                                        Optional.empty(), Optional.empty(), Optional.empty()))
                                .then(etherBiomes())
                                .then(Commands.argument("minRadius", FloatArgumentType.floatArg(5.0f, 500.0f))
                                    .suggests((ctx, b) -> suggestValues(b, "minRadius", "20", "40", "60", "80", "100"))
                                    .then(Commands.argument("maxRadius", FloatArgumentType.floatArg(5.0f, 500.0f))
                                        .suggests((ctx, b) -> suggestValues(b, "maxRadius", "60", "90", "120", "150"))
                                        .executes(ctx -> executeEtherCreate(ctx,
                                                Optional.of(FloatArgumentType.getFloat(ctx, "threshold")),
                                                Optional.of(FloatArgumentType.getFloat(ctx, "minRadius")),
                                                Optional.of(FloatArgumentType.getFloat(ctx, "maxRadius")),
                                                Optional.empty()))
                                        .then(etherBiomes())
                                        .then(Commands.argument("spacing", IntegerArgumentType.integer(1, 32))
                                            .suggests((ctx, b) -> suggestValues(b, "spacing", "4", "6", "8", "10", "12"))
                                            .executes(ctx -> executeEtherCreate(ctx,
                                                    Optional.of(FloatArgumentType.getFloat(ctx, "threshold")),
                                                    Optional.of(FloatArgumentType.getFloat(ctx, "minRadius")),
                                                    Optional.of(FloatArgumentType.getFloat(ctx, "maxRadius")),
                                                    Optional.of(IntegerArgumentType.getInteger(ctx, "spacing"))))
                                            .then(etherBiomes())
                                        )
                                    )
                                )
                            )
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

    // Builds the "biomes <list>" subtree reused at every ether stopping point.
    private static LiteralArgumentBuilder<CommandSourceStack> etherBiomes() {
        return Commands.literal("biomes")
                .then(Commands.argument("biome_list", StringArgumentType.greedyString())
                    .suggests(DimCommands::suggestBiomes)
                    .executes(ctx -> {
                        // Grab whichever ether args were provided before "biomes"
                        Optional<Float> threshold = getOptionalFloat(ctx, "threshold");
                        Optional<Float> minRadius = getOptionalFloat(ctx, "minRadius");
                        Optional<Float> maxRadius = getOptionalFloat(ctx, "maxRadius");
                        Optional<Integer> spacing = getOptionalInt(ctx, "spacing");
                        String biomes = StringArgumentType.getString(ctx, "biome_list");
                        return executeEtherCreate(ctx, threshold, minRadius, maxRadius, spacing, biomes);
                    }));
    }

    // Builds the generatorConfig string from typed ether args and delegates to executeCreate.
    private static int executeEtherCreate(CommandContext<CommandSourceStack> ctx,
                                           Optional<Float> threshold, Optional<Float> minRadius,
                                           Optional<Float> maxRadius, Optional<Integer> spacing) {
        return executeEtherCreate(ctx, threshold, minRadius, maxRadius, spacing, null);
    }

    private static int executeEtherCreate(CommandContext<CommandSourceStack> ctx,
                                           Optional<Float> threshold, Optional<Float> minRadius,
                                           Optional<Float> maxRadius, Optional<Integer> spacing,
                                           String biomes) {
        StringBuilder config = new StringBuilder();
        threshold.ifPresent(v -> config.append("threshold ").append(v).append(' '));
        if (minRadius.isPresent() && maxRadius.isPresent()) {
            config.append("radius ").append(minRadius.get()).append(' ').append(maxRadius.get()).append(' ');
        }
        spacing.ifPresent(v -> config.append("spacing ").append(v).append(' '));
        if (biomes != null && !biomes.isBlank()) {
            config.append("biomes ").append(biomes);
        }
        String configStr = config.toString().trim();
        return executeCreate(ctx.getSource(), ctx.getArgument("id", Identifier.class),
                "ether", configStr.isEmpty() ? Optional.empty() : Optional.of(configStr));
    }

    private static Optional<Float> getOptionalFloat(CommandContext<CommandSourceStack> ctx, String name) {
        try { return Optional.of(FloatArgumentType.getFloat(ctx, name)); }
        catch (IllegalArgumentException e) { return Optional.empty(); }
    }

    private static Optional<Integer> getOptionalInt(CommandContext<CommandSourceStack> ctx, String name) {
        try { return Optional.of(IntegerArgumentType.getInteger(ctx, name)); }
        catch (IllegalArgumentException e) { return Optional.empty(); }
    }

    private static CompletableFuture<Suggestions> suggestValues(SuggestionsBuilder builder,
                                                                String label, String... values) {
        com.mojang.brigadier.Message tooltip = () -> label;
        for (String v : values) {
            if (v.startsWith(builder.getRemaining())) builder.suggest(v, tooltip);
        }
        return builder.buildFuture();
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
