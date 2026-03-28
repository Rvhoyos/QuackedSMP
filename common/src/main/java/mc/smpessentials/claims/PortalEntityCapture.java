package mc.smpessentials.claims;

import java.util.UUID;

/**
 * Thread-local carrier for the player UUID that triggered nether portal teleportation.
 * Set in {@link mc.smpessentials.mixin.NetherPortalBlockMixin} when {@code getPortalDestination}
 * is called, then read by {@link mc.smpessentials.mixin.PortalForcerMixin} inside
 * {@code createPortal}. Both run on the main server thread so no locking is needed.
 * Cleared to null for non-player entities so the forcer skips the claim check.
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
