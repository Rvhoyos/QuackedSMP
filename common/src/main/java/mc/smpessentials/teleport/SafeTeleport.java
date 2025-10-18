package mc.smpessentials.teleport;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;

import java.util.EnumSet;

/**
 * Teleports the requester to the target's exact position and rotation.
 */
public final class SafeTeleport {
    private SafeTeleport() {}

    public static boolean teleportToPlayer(ServerPlayer requester, ServerPlayer target) {
        ServerLevel level = (ServerLevel) target.level();

        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        float yaw = target.getYRot();
        float pitch = target.getXRot();

        requester.stopRiding();
        return requester.teleportTo(
            level, x, y, z,
            EnumSet.noneOf(Relative.class),
            yaw, pitch,
            false
        );
    }
}
