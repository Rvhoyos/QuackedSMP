package mc.smpessentials.dims;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.border.WorldBorder;

import java.util.Set;

/**
 * Void fall-through mechanic for {@code ether} dimensions.
 *
 * Falling out the bottom of an ether dim drops you into the overworld at Y=300, at the same XZ you
 * fell through, so you land where you left rather than at spawn. Player-made entities make the same
 * trip; mobs do not, and neither does anything else that falls out.
 *
 * Players are checked by {@link #tick(ServerPlayer)} once per player per server tick. Everything
 * else arrives through {@link #transferOnVoidFall(Entity)}, called from EntityBelowWorldMixin at the
 * moment vanilla would have discarded it.
 */
public final class EtherFallthrough {

    private EtherFallthrough() {}

    // High enough in the overworld to clear terrain, low enough to stay inside the build limit.
    private static final int DROP_Y = 300;

    /**
     * Checks whether {@code player} is in an ether dimension and has fallen below its minimum Y.
     * If so, drops them into the overworld sky above where they fell. A hotbar message is shown to
     * inform the player.
     *
     * This method is a no-op for players in non-ether or vanilla dimensions.
     */
    public static void tick(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        if (!DimManager.isEtherDim(level.dimension().identifier().toString())) return;

        int minY = level.dimensionTypeRegistration().value().minY();
        if (player.getY() >= minY) return;

        dropIntoOverworld(player, level);
        player.sendSystemMessage(
                Component.literal("The void pulls you back to the overworld."), true);
    }

    /**
     * Sends a player-made entity that fell out of an ether dim to the overworld instead of letting
     * vanilla discard it. Returns true when the entity was moved, in which case the caller must skip
     * the discard.
     *
     * Mobs and anything carrying a passenger are left to vanilla, so a mob cannot ride a boat into
     * the overworld.
     */
    public static boolean transferOnVoidFall(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        if (!DimManager.isEtherDim(level.dimension().identifier().toString())) return false;
        if (!isPlayerMade(entity) || !entity.getPassengers().isEmpty()) return false;

        dropIntoOverworld(entity, level);
        return true;
    }

    // Things a player dropped, built, placed, lit, or threw. Deliberately a whitelist: ArmorStand
    // is a LivingEntity, so "not a mob" would not have been enough to tell them apart.
    // Projectiles go by owner, so a player's trident carries into the overworld and can still hit
    // someone there, while a skeleton's arrow does not.
    private static boolean isPlayerMade(Entity entity) {
        return entity instanceof ItemEntity
                || entity instanceof VehicleEntity        // boats, rafts, every minecart
                || entity instanceof ArmorStand
                || entity instanceof FallingBlockEntity
                || entity instanceof PrimedTnt
                || (entity instanceof Projectile projectile && projectile.getOwner() instanceof Player);
    }

    // Drops into the overworld sky at the XZ it fell through, clamped inside the border so a fall
    // from beyond it cannot strand anything outside. Ether is 1:1 with the overworld, so the
    // coordinates carry over unscaled.
    private static void dropIntoOverworld(Entity entity, ServerLevel etherLevel) {
        ServerLevel overworld = etherLevel.getServer().overworld();
        WorldBorder border = overworld.getWorldBorder();
        double x = Mth.clamp(entity.getX(), border.getMinX() + 1.0, border.getMaxX() - 1.0);
        double z = Mth.clamp(entity.getZ(), border.getMinZ() + 1.0, border.getMaxZ() - 1.0);

        entity.teleportTo(overworld, x, DROP_Y, z,
                Set.of(), entity.getYRot(), entity.getXRot(), false);
    }
}
