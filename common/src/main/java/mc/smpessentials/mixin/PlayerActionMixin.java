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

/**
 * Three responsibilities:
 *
 * <p><b>1. Scout Zoom activation/deactivation</b> — intercepts
 * {@code SWAP_ITEM_WITH_OFFHAND} (F key). Sneak + F with both hands empty
 * activates Scout Zoom. F while zoom is active deactivates it early.
 *
 * <p><b>2. Scout Zoom offhand lock</b> — intercepts container clicks on the
 * offhand slot ({@link InventoryMenu#SHIELD_SLOT} = 45) while zoom is active,
 * preventing the player from dragging, shift-clicking, or dropping the injected
 * spyglass. Without this, the item can be moved into the player's inventory and
 * duplicated on the next activation.
 *
 * <p><b>3. Scout Zoom external-container cancellation</b> — when the player opens
 * any container other than their own inventory (chest, furnace, crafting table, etc.)
 * while zoom is active, zoom is automatically deactivated so the spyglass is cleaned
 * up and normal interaction proceeds.
 *
 * <p><b>Thread note</b>: Both {@code handlePlayerAction} and
 * {@code handleContainerClick} call {@code PacketUtils.ensureRunningOnSameThread}
 * as their first statement, which reschedules from the netty thread to the server
 * thread. Our HEAD injections therefore fire twice per packet. We guard with
 * {@code server.isSameThread()} so game-state mutations only happen on the second
 * (server-thread) invocation.
 */
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
            player.displayClientMessage(
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

    /**
     * Blocks all container interactions targeting the offhand slot (slot
     * {@link InventoryMenu#SHIELD_SLOT}) in the player's own inventory
     * ({@link InventoryMenu#CONTAINER_ID}) while Scout Zoom is active.
     *
     * <p>This prevents the player from dragging the injected spyglass into their
     * main inventory, dropping it to the world, or shift-clicking it away — all
     * of which would cause item duplication on the next zoom activation.
     * The client is immediately resynced so no inventory desync occurs.
     */
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
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("\u00a77Scout Zoom ended."), true);
        }
    }
}
