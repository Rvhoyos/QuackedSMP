package mc.smpessentials.mixin;

import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

// 1. Scout Zoom activation/deactivation via SWAP_ITEM_WITH_OFFHAND (F key).
// 2. Offhand slot lock while zoom is active to prevent spyglass duplication.
// 3. Auto-deactivates zoom when the player opens any external container.
// isSameThread() guard prevents double-firing on netty->server-thread reschedule.
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerActionMixin {

    @Shadow
    public ServerPlayer player;

    // ── Scout Zoom activation ────────────────────────────────────────────────

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void onHandlePlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        if (!server.isSameThread()) return;

        if (packet.getAction() != ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) return;

        UUID uuid = player.getUUID();

        if (mc.smpessentials.skills.ActiveAbilities.isZoomActive(uuid)) {
            // F while zoom is active → deactivate
            mc.smpessentials.skills.ActiveAbilities.deactivateZoom(uuid, player);
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("\u00a77Scout Zoom deactivated."), true);
            ci.cancel();
            return;
        }

        // Sneak + F with both hands empty → activate
        if (player.isShiftKeyDown()
                && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().isEmpty()) {
            mc.smpessentials.skills.SkillEvents.onZoomActivate(player);
            ci.cancel();
        }
    }

    // ── Scout Zoom offhand lock ──────────────────────────────────────────────

    // Blocks all interactions on the offhand slot (SHIELD_SLOT) while zoom is active to prevent spyglass duplication.
    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void onHandleContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        if (!server.isSameThread()) return;

        if (!mc.smpessentials.skills.ActiveAbilities.isZoomActive(player.getUUID())) return;

        if (packet.containerId() == InventoryMenu.CONTAINER_ID) {
            // Player's own inventory — block the offhand slot only.
            if (packet.slotNum() == InventoryMenu.SHIELD_SLOT) {
                player.inventoryMenu.sendAllDataToRemote(); // revert any client-side cursor desync
                ci.cancel();
            }
        } else {
            // Player clicked inside an external container (chest, furnace, crafting table,
            // etc.) while zoom is active. Cancel zoom so the spyglass is cleaned up and
            // the player can interact normally.
            mc.smpessentials.skills.ActiveAbilities.deactivateZoom(player.getUUID(), player);
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("\u00a77Scout Zoom ended."), true);
        }
    }
}
