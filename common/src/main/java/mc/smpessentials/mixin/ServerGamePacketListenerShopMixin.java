package mc.smpessentials.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.shops.ShopData;
import mc.smpessentials.shops.ShopEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Vanilla handleUseItemOn calls MinecraftServer.isUnderSpawnProtection before any
// loader event fires; when it returns true, vanilla sends the
// "x,y,z is under spawn protection" red message and aborts. Report false here for
// registered spawn-shop chests so the right-click continues into useItemOn,
// where ShopService.onRightClickBlock can open the buy GUI.
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerShopMixin {

    @WrapOperation(method = "handleUseItemOn",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;isUnderSpawnProtection(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean quackedsmp$allowSpawnShopRightClick(
            MinecraftServer server, ServerLevel level, BlockPos pos, Player player, Operation<Boolean> original) {
        if (!original.call(server, level, pos, player)) return false;
        if (!SmpConfig.SHOPS_ENABLED) return true;
        boolean isSpawnShop = ShopData.get(server)
                .getShopAt(level.dimension(), pos)
                .map(ShopEntry::spawnShop)
                .orElse(false);
        return !isSpawnShop;
    }
}
