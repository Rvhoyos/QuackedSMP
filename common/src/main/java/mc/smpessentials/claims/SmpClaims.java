package mc.smpessentials.claims;

/**
 * Module-level entry point for the claims subsystem. Currently a placeholder; storage is
 * loaded lazily via {@link mc.smpessentials.claims.storage.ClaimedSavedData} on demand.
 */
public final class SmpClaims {
    private SmpClaims() {}

    /** Reserved for future one-time initialisation; currently a no-op. */
    public static void init() {
        // Currently nothing necessary here (storage is in-memory).
        // later add SavedData persistence, wire it here.
    }
}
