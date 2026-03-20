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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Registers the {@code /dim} command tree for runtime dimension management.
 *
 * <pre>
 *   /dim create &lt;id&gt; overworld                         — Vanilla overworld terrain and biomes (OP)
 *   /dim create &lt;id&gt; overworld biomes [b1[:w]...]      — Overworld terrain, custom biome list (OP)
 *   /dim create &lt;id&gt; overworld flat &lt;block:h&gt;...        — Flat terrain (OP)
 *   /dim create &lt;id&gt; ether                             — Floating islands, overworld biomes (OP)
 *   /dim create &lt;id&gt; ether biomes [b1[:w]...]          — Floating islands, custom biome list (OP)
 *   /dim create &lt;id&gt; nether                            — Vanilla nether (OP)
 *   /dim create &lt;id&gt; end                               — Vanilla end (OP)
 *   /dim delete &lt;id&gt;                                   — Delete a custom dimension (OP)
 *   /dim list                                          — List all active dimensions (everyone)
 *   /dim setportal &lt;dim_id&gt; &lt;block_id&gt;                 — Wire a block type as portal frame (OP)
 *   /dim tp &lt;id&gt;                                       — Teleport self to dimension (OP)
 *   /dim tp &lt;player&gt; &lt;id&gt;                              — Teleport player to dimension (OP)
 * </pre>
 *
 * <h2>Biome list format</h2>
 * <p>Space-separated tokens of the form {@code namespace:path} (weight 1) or
 * {@code namespace:path:weight} (explicit weight). Only the ratio between weights matters —
 * {@code plains:1 desert:1} and {@code plains:7 desert:7} produce the same 50/50 split.
 * A single biome token pins the entire dimension to that biome. Omitting {@code biomes}
 * entirely uses the full vanilla biome set.
 *
 * <h2>Flat layer format</h2>
 * <p>Space-separated tokens of the form {@code namespace:path:height}, listed
 * <strong>bottom to top</strong>. Example: {@code minecraft:bedrock:1 minecraft:dirt:3 minecraft:grass_block:1}.
 *
 * <h2>Portal system</h2>
 * <p>Each custom dim is automatically assigned {@code minecraft:glowstone} as its portal frame
 * block when first created (if glowstone is not already claimed). {@code /dim setportal} overrides
 * this with any block of your choice. Only one frame block per dim; only one dim per frame block.
 *
 * <p>To open a portal: build a nether-portal-shaped frame (2–21 wide, 3–21 tall) anywhere in a
 * vanilla dimension and right-click any frame block with a water bucket. Portals are bidirectional
 * — stepping through from the custom dim returns to the overworld. Portal creation is intentionally
 * blocked inside custom dims to prevent vanilla from auto-generating unwanted return portals in
 * the overworld.
 */
public final class DimCommands {

    private DimCommands() {}

    /**
     * Registers the entire {@code /dim} command subtree with the given dispatcher.
     * Called from {@link mc.smpessentials.commands.CommandRegistrar} during server startup.
     */
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

                        // ether [biomes [...]]
                        .then(Commands.literal("ether")
                            .executes(ctx -> executeCreate(ctx.getSource(),
                                    ctx.getArgument("id", Identifier.class),
                                    "ether", Optional.empty()))
                            .then(Commands.literal("biomes")
                                // /dim create <id> ether biomes  — default overworld biomes
                                .executes(ctx -> executeCreate(ctx.getSource(),
                                        ctx.getArgument("id", Identifier.class),
                                        "ether", Optional.empty()))
                                // /dim create <id> ether biomes <b1[:w] ...>
                                .then(Commands.argument("biome_list", StringArgumentType.greedyString())
                                    .suggests(DimCommands::suggestBiomes)
                                    .executes(ctx -> executeCreate(ctx.getSource(),
                                            ctx.getArgument("id", Identifier.class),
                                            "ether",
                                            Optional.of("biomes " + StringArgumentType.getString(ctx, "biome_list")))))
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

    /**
     * Shared handler for all {@code /dim create} subcommands. Delegates to
     * {@link DimManager#create} and sends a success or failure message to the command source.
     *
     * @return {@code 1} on success, {@code 0} on failure (Brigadier convention)
     */
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
        return 1;
    }

    /**
     * Teleports {@code player} to the spawn origin of {@code dest}.
     * Schedules spawn-structure generation on the next tick (ether island for ether dims,
     * return portal for all other custom dims) so chunks are loaded when the heightmap is queried.
     *
     * @return {@code 1} on success, {@code 0} if the teleport was rejected by the server
     */
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

    /**
     * Suggests biome IDs for a greedy-string biome list argument.
     * Handles multi-token input: completes the current (last) token against the biome registry.
     */
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

    /**
     * Suggests all active dimension IDs (vanilla + runtime-created custom dims).
     * Used for {@code /dim tp} where any loaded dim is a valid destination.
     */
    private static CompletableFuture<Suggestions> suggestAllDims(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        DimManager.listAll(ctx.getSource().getServer()).stream()
                .map(key -> key.identifier().toString())
                .filter(id -> id.startsWith(builder.getRemaining()))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    /**
     * Suggests only custom (non-vanilla) dimension IDs.
     * Reads from the live levels map so datapack-registered dims are included alongside
     * plugin-created ones. Vanilla dims (overworld, nether, end) are excluded.
     * Used for {@code /dim delete} and {@code /dim setportal} where vanilla dims are not valid targets.
     */
    private static CompletableFuture<Suggestions> suggestCustomDims(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        DimManager.listAll(ctx.getSource().getServer()).stream()
                .filter(key -> !key.equals(Level.OVERWORLD) && !key.equals(Level.NETHER) && !key.equals(Level.END))
                .map(key -> key.identifier().toString())
                .filter(id -> id.startsWith(builder.getRemaining()))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    /**
     * Suggests block IDs for a greedy-string flat-layer argument.
     * Handles multi-token input: completes the current (last) token against the block registry.
     */
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
