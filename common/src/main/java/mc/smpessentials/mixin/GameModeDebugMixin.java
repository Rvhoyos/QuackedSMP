package mc.smpessentials.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Temporary diagnostic mixin. Remove after debugging.
@Mixin(ServerPlayerGameMode.class)
public abstract class GameModeDebugMixin {

    @Shadow protected ServerLevel level;
    @Shadow protected ServerPlayer player;
    @Shadow private int destroyProgressStart;
    @Shadow private int gameTicks;

    private int lastLoggedGameTicks = -1;

    private boolean isCustomDim() {
        return level.dimension() != Level.OVERWORLD
                && level.dimension() != Level.NETHER
                && level.dimension() != Level.END;
    }

    // Log every time gameMode.tick() is called in a custom dim (throttled to once per second)
    @Inject(method = "tick", at = @At("TAIL"))
    private void quackedsmp$debugTick(CallbackInfo ci) {
        if (!isCustomDim()) return;
        if (gameTicks % 20 == 0 && gameTicks != lastLoggedGameTicks) {
            lastLoggedGameTicks = gameTicks;
            mc.smpessentials.SmpUtilsMod.LOGGER.info(
                    "[DimBreakDebug] gameMode.tick() running, gameTicks={} level={} hasTickets={}",
                    gameTicks, level.dimension().identifier(),
                    level.getChunkSource().hasActiveTickets());
        }
    }

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"))
    private void quackedsmp$debugBreakAction(BlockPos pos, ServerboundPlayerActionPacket.Action action,
                                              Direction face, int maxBuildHeight, int sequence,
                                              CallbackInfo ci) {
        if (!isCustomDim()) return;

        var logger = mc.smpessentials.SmpUtilsMod.LOGGER;

        if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            BlockState state = level.getBlockState(pos);
            float destroyProgress = state.getDestroyProgress(player, player.level(), pos);
            boolean inPlayersList = level.players().contains(player);
            boolean removed = player.isRemoved();
            boolean sameLevel = player.level() == level;
            logger.info("[DimBreakDebug] START_DESTROY {} block={} destroyProgress={} gameTicks={} hasTickets={} inPlayersList={} removed={} sameLevel={} playerLevel={}",
                    pos, state.getBlock(), destroyProgress, gameTicks,
                    level.getChunkSource().hasActiveTickets(),
                    inPlayersList, removed, sameLevel, player.level().dimension().identifier());
        } else if (action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
            BlockState state = level.getBlockState(pos);
            int elapsed = gameTicks - destroyProgressStart;
            float progress = state.getDestroyProgress(player, player.level(), pos) * (elapsed + 1);
            logger.info("[DimBreakDebug] STOP_DESTROY {} block={} elapsed={} progress={} (need>=0.7)",
                    pos, state.getBlock(), elapsed, progress);
        } else if (action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
            logger.info("[DimBreakDebug] ABORT_DESTROY {}", pos);
        }
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void quackedsmp$debugDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!isCustomDim()) return;
        var logger = mc.smpessentials.SmpUtilsMod.LOGGER;
        logger.info("[DimBreakDebug] destroyBlock called at {}", pos);
    }
}
