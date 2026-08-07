package mc.smpessentials.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import mc.smpessentials.hardcore.HardcoreSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// The client draws the withered hardcore heart HUD when the login packet's hardcore flag is
// true (ClientboundLoginPacket -> ClientLevelData.isHardcore). That flag is set once per
// connection at login and cannot change afterward. Override it to true for players in a
// hardcore session so they get the hardcore HUD, without flipping the world-level flag that
// would affect everyone. Non-session players fall through to the real value.
@Mixin(PlayerList.class)
public abstract class PlayerListHardcoreHeartsMixin {

    @ModifyExpressionValue(method = "placeNewPlayer",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/LevelData;isHardcore()Z"))
    private boolean quackedsmp$hardcoreHeartsForSessionMembers(
            boolean original, @Local(argsOnly = true) ServerPlayer player) {
        return original || HardcoreSavedData.shouldShowHardcoreHearts(player);
    }
}
