package mc.smpessentials.dims;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.Set;

/**
 * Void fall-through mechanic for {@code ether} dimensions.
 *
 * <p>If a player falls below the dimension floor ({@code minY}) in an ether dim, they are
 * teleported to Y=300 above world-spawn XZ in the overworld, where they fall down naturally
 * onto the surface.
 *
 * <p>Call {@link #tick(ServerPlayer)} once per player per server tick.
 */
public final class EtherFallthrough {

    private EtherFallthrough() {}

    /**
     * Checks whether {@code player} is in an ether dimension and has fallen below its minimum Y.
     * If so, teleports them to Y=300 above overworld world-spawn XZ so they fall back down
     * naturally onto the surface. A hotbar message is shown to inform the player.
     *
     * <p>This method is a no-op for players in non-ether or vanilla dimensions.
     */
    public static void tick(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        if (!DimManager.isEtherDim(level.dimension().identifier().toString())) return;

        int minY = level.dimensionTypeRegistration().value().minY();
        if (player.getY() >= minY) return;

        // Drop into the overworld sky at world-spawn XZ — player falls down naturally
        ServerLevel overworld = level.getServer().overworld();
        BlockPos spawnXZ = overworld.getRespawnData().pos();

        player.teleportTo(overworld,
                spawnXZ.getX() + 0.5, 300, spawnXZ.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot(), false);

        player.displayClientMessage(
                Component.literal("The void pulls you back to the overworld."), true);
    }
}
