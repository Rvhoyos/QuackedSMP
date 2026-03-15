package mc.smpessentials.claims;

/**
 * Module-level entry point for the claims subsystem.
 *
 * <p>Currently a placeholder; actual initialisation happens lazily when
 * {@link mc.smpessentials.claims.storage.ClaimedSavedData} is first loaded from
 * the server's {@code DataStorage} on demand.  This class exists so future
 * one-time setup (e.g., event pre-registration) has a clear home.
 */
public final class SmpClaims {
    private SmpClaims() {}

    /** Reserved for future one-time initialisation; currently a no-op. */
    public static void init() {
        // Currently nothing necessary here (storage is in-memory).
        // later add SavedData persistence, wire it here.
    }
}
