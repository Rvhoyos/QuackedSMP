package mc.smpessentials.claims;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.InteractionEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ChunkPos;

public final class ClaimProtection {
    private ClaimProtection() {}

    public static void init() {
        // Block break: (level, pos, state, player, exp) -> EventResult
        BlockEvent.BREAK.register((level, pos, state, player, exp) -> {
            if (!(player instanceof ServerPlayer sp)) return EventResult.pass();
            ServerLevel sl = (ServerLevel) level; // ensure ServerLevel for canModify
            boolean ok = ClaimAccess.canModify(sp, sl, new ChunkPos(pos));
            return ok ? EventResult.pass() : EventResult.interruptFalse();
        });

        // Block place: (level, pos, state, placer) -> EventResult
        BlockEvent.PLACE.register((level, pos, state, placer) -> {
            if (!(placer instanceof ServerPlayer sp)) return EventResult.pass();
            ServerLevel sl = (ServerLevel) level;
            boolean ok = ClaimAccess.canModify(sp, sl, new ChunkPos(pos));
            return ok ? EventResult.pass() : EventResult.interruptFalse();
        });

        // Right-click block: returns InteractionResult (not EventResult in your API)
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            ServerLevel sl = (ServerLevel) sp.level();
            boolean ok = ClaimAccess.canModify(sp, sl, new ChunkPos(pos));
            return ok ? InteractionResult.PASS : InteractionResult.FAIL; // cancel interaction if not allowed
        });
    }
}
