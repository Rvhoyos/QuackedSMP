package mc.smpessentials.claims;

import java.util.UUID;

/**
 * Thread-local carrier for the player UUID that triggered nether portal teleportation.
 *
 * <p>Set in {@link mc.smpessentials.mixin.NetherPortalBlockMixin} when
 * {@code getPortalDestination} is called (which has the entity reference), then
 * read by {@link mc.smpessentials.mixin.PortalForcerMixin} inside
 * {@code createPortal} (which does not). Both run synchronously on the main
 * server thread, so no locking is needed.
 *
 * <p>Cleared to {@code null} when the entity is not a player (vehicles, mobs),
 * so the forcer skips the claim check for non-player arrivals.
 */
public final class PortalEntityCapture {
    private static final ThreadLocal<UUID> ENTITY = new ThreadLocal<>();

    private PortalEntityCapture() {
    }

    public static void set(UUID uuid) {
        ENTITY.set(uuid);
    }

    public static void clear() {
        ENTITY.remove();
    }

    public static UUID get() {
        return ENTITY.get();
    }
}
