package mc.smpessentials.items;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Hands an item the server is giving to a player: into the inventory, or onto the ground at their
 * feet when it does not fit.
 *
 * The overflow is built the way vanilla /give builds its own (GiveCommand), not through
 * Player.drop: drop swings the player's arm and runs the drop hooks, which belong to items the
 * player throws themselves, and the sneak + drop ability trigger is one of those hooks.
 */
public final class ItemHandoff {

    private ItemHandoff() {}

    public static void give(ServerPlayer player, ItemStack stack) {
        if (player.getInventory().add(stack)) return;

        ItemEntity overflow = player.createItemStackToDrop(stack, false, false);
        if (overflow == null) return;
        overflow.setNoPickUpDelay();
        overflow.setTarget(player.getUUID());
        overflow.level().addFreshEntity(overflow);
    }
}
