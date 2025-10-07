// common/src/main/java/mc/smpessentials/claims/ClaimCommands.java
package mc.smpessentials.claims;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;
import java.util.UUID;

public final class ClaimCommands {
    private ClaimCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /claim
        dispatcher.register(
            Commands.literal("claim")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException(); // may throw, Brigadier allows it
                    ServerLevel lvl = p.level();
                    ChunkPos pos = p.chunkPosition();

                    ClaimService.Result result = ClaimService.claim(p, lvl, pos);
                    switch (result) {
                        case SUCCESS -> ctx.getSource().sendSystemMessage(Component.literal("Chunk claimed."));
                        case ALREADY_CLAIMED -> ctx.getSource().sendFailure(Component.literal("This claim is already protected."));
                        case REACHED_CAP -> ctx.getSource().sendFailure(Component.literal("You reached the claim limit (" + ClaimService.MAX_PER_PLAYER + ")."));
                        case SPAWN_PROTECTED -> ctx.getSource().sendFailure(Component.literal("You can’t claim inside spawn protection."));
                    }
                    return 1;
                })
        );

        // /unclaim
        dispatcher.register(
            Commands.literal("unclaim")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ServerLevel lvl = p.level();
                    ChunkPos pos = p.chunkPosition();

                    boolean ok = ClaimService.unclaim(p, lvl, pos);
                    if (ok) {
                        ctx.getSource().sendSystemMessage(Component.literal("Chunk unclaimed."));
                    } else {
                        ctx.getSource().sendFailure(Component.literal("You don’t control this claim."));
                    }
                    return 1;
                })
        );

        // /claims (dimension-local count + current owner)
        dispatcher.register(
            Commands.literal("claims")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ServerLevel lvl = p.level();
                    ChunkPos pos = p.chunkPosition();

                    int mine = ClaimService.ownedCount(lvl, p.getUUID()); // <-- updated
                    ctx.getSource().sendSystemMessage(Component.literal("You own " + mine + " chunk(s) in this dimension."));

                    Optional<UUID> owner = ClaimService.getOwner(lvl, pos);
                    if (owner.isPresent()) {
                        boolean you = owner.get().equals(p.getUUID());
                        ctx.getSource().sendSystemMessage(Component.literal(
                            you ? "This chunk is protected by you." : "This chunk is protected."
                        ));
                    } else {
                        ctx.getSource().sendSystemMessage(Component.literal("Current chunk is unclaimed."));
                    }
                    return 1;
                })
        );
    }
}
