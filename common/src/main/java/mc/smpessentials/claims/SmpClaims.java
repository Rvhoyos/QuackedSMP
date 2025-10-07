package mc.smpessentials.claims;

public final class SmpClaims {
    private SmpClaims() {}

    /** Call once from common init if you want a single entrypoint. */
    public static void init() {
        // Currently nothing necessary here (storage is in-memory).
        // later add SavedData persistence, wire it here.
    }
}
