package mc.smpessentials.dashboard;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.ConfigIO;
import mc.smpessentials.config.SmpConfig;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Password hashing (PBKDF2-SHA256) and session token management for the admin panel.
 *
 * <p>Hash format stored in config: {@code pbkdf2:<iterations>:<base64-salt>:<base64-hash>}
 *
 * <p>Auth is bypassed entirely when {@code SmpConfig.ADMIN_PASSWORD_HASH} is blank.
 * This only occurs when an operator has manually set {@code admin_enabled=true} in the JSON
 * config without a password — a deliberate advanced-user choice. In-game, the only way to
 * enable the panel is {@code /smp admin setpassword}, which always sets a password atomically.
 */
public final class AdminAuth {

    private static final SecureRandom RANDOM     = new SecureRandom();
    private static final int          ITERATIONS = 100_000;
    private static final int          KEY_BITS   = 256;
    private static final long         SESSION_TTL = 24L * 60 * 60 * 1000; // 24 h

    private static final ConcurrentHashMap<String, Long>   sessions     = new ConcurrentHashMap<>();

    // Rate limiting: ip → [failCount, lockUntilMs, windowStartMs]
    private static final int  FAIL_LIMIT  = 5;
    private static final long WINDOW_MS   = 15L * 60 * 1000; // 15-minute failure window
    private static final long LOCKOUT_MS  = 5L  * 60 * 1000; // 5-minute lockout
    private static final ConcurrentHashMap<String, long[]> loginAttempts = new ConcurrentHashMap<>();

    private AdminAuth() {}

    // ── Password ───────────────────────────────────────────────────────────────

    /** Returns true if no password has been configured (raw config edit = trusted). */
    public static boolean noPasswordSet() {
        return SmpConfig.ADMIN_PASSWORD_HASH.isBlank();
    }

    /** Hashes a plaintext password and persists the hash to config. */
    public static void setPassword(String password) {
        try {
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            byte[] hash = pbkdf2(password.toCharArray(), salt, ITERATIONS);
            String stored = "pbkdf2:" + ITERATIONS + ":"
                    + Base64.getEncoder().encodeToString(salt) + ":"
                    + Base64.getEncoder().encodeToString(hash);
            SmpConfig.ADMIN_PASSWORD_HASH = stored;
            ConfigIO.save();
        } catch (Exception e) {
            SmpUtilsMod.LOGGER.error("[AdminAuth] Failed to hash password: {}", e.getMessage());
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    /** Constant-time PBKDF2 password verification. */
    public static boolean verifyPassword(String password) {
        String stored = SmpConfig.ADMIN_PASSWORD_HASH;
        if (stored.isBlank()) return false;
        try {
            String[] parts = stored.split(":");
            if (parts.length != 4 || !parts[0].equals("pbkdf2")) return false;
            int   iter     = Integer.parseInt(parts[1]);
            byte[] salt    = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual   = pbkdf2(password.toCharArray(), salt, iter);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    // ── Sessions ───────────────────────────────────────────────────────────────

    /** Creates and returns a new session token (random 128-bit hex string). */
    public static String createSession() {
        String token = new BigInteger(128, RANDOM).toString(16);
        sessions.put(token, System.currentTimeMillis() + SESSION_TTL);
        return token;
    }

    /** Returns true if the token is valid and not expired. */
    public static boolean isValidSession(String token) {
        if (token == null || token.isBlank()) return false;
        Long expiry = sessions.get(token);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    /** Invalidates all active sessions (e.g. on password change). */
    public static void clearSessions() {
        sessions.clear();
    }

    // ── Rate limiting ──────────────────────────────────────────────────────────

    /** Returns true if this IP is currently locked out from login attempts. */
    public static boolean isRateLimited(String ip) {
        long[] s = loginAttempts.get(ip);
        if (s == null) return false;
        long now = System.currentTimeMillis();
        if (s[1] > 0 && now < s[1]) return true;          // actively locked out
        if (now - s[2] > WINDOW_MS) { loginAttempts.remove(ip); return false; } // window expired
        return false;
    }

    /** Records a failed login attempt for this IP. Locks out after {@code FAIL_LIMIT} failures. */
    public static void recordFailedLogin(String ip) {
        long now = System.currentTimeMillis();
        loginAttempts.compute(ip, (k, s) -> {
            if (s == null || now - s[2] > WINDOW_MS) return new long[]{1, 0, now};
            s[0]++;
            if (s[0] >= FAIL_LIMIT) s[1] = now + LOCKOUT_MS;
            return s;
        });
    }

    /** Clears failed login state for this IP on successful login. */
    public static void clearFailedLogins(String ip) {
        loginAttempts.remove(ip);
    }

    // ── Auth check helper ──────────────────────────────────────────────────────

    /**
     * Returns true if the request is authorized to access the admin panel.
     * Always returns true when no password is set (config-edit trusted mode).
     */
    public static boolean isAuthorized(java.util.Map<String, String> headers) {
        if (noPasswordSet()) {
            mc.smpessentials.SmpUtilsMod.LOGGER.info("[AdminAuth] isAuthorized=true (no password set)");
            return true;
        }
        String auth = headers.get("authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            mc.smpessentials.SmpUtilsMod.LOGGER.warn("[AdminAuth] isAuthorized=false (no/bad Authorization header)");
            return false;
        }
        String token = auth.substring(7).trim();
        boolean valid = isValidSession(token);
        mc.smpessentials.SmpUtilsMod.LOGGER.info("[AdminAuth] isAuthorized={} token={}…", valid, token.length() > 8 ? token.substring(0, 8) : token);
        return valid;
    }

    // ── PBKDF2 helper ─────────────────────────────────────────────────────────

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
