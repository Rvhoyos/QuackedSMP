package mc.smpessentials.dashboard;

import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.ConfigIO;
import mc.smpessentials.config.SmpConfig;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

// Password hashing (PBKDF2-SHA256) and session token management for the admin panel.
// Auth is bypassed when ADMIN_PASSWORD_HASH is blank; this only happens via manual config edit.
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

    public static boolean noPasswordSet() {
        return SmpConfig.ADMIN_PASSWORD_HASH.isBlank();
    }

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

    public static String createSession() {
        String token = new BigInteger(128, RANDOM).toString(16);
        sessions.put(token, System.currentTimeMillis() + SESSION_TTL);
        return token;
    }

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

    public static void invalidateSession(String token) {
        if (token != null) sessions.remove(token);
    }

    public static void clearSessions() {
        sessions.clear();
    }

    // ── Scoped resource tokens ─────────────────────────────────────────────────

    // An <img> tag cannot send an Authorization header, so image routes take a
    // signed token in the URL instead. It is scoped to one resource group and
    // expires, so a leaked URL cannot be replayed against the rest of the panel
    // the way the session token could. The key is regenerated every boot, which
    // also invalidates every outstanding token on restart.
    private static final byte[] SCOPE_KEY    = newScopeKey();
    // Expiries snap to this grid so repeated mints of one scope return the SAME
    // token. A token that changed per call would change the URL built from it, and
    // a changed URL is a cache miss: the panel polls once a second during a capture,
    // which would re-download every image it is showing.
    private static final long   SCOPE_BUCKET = 60L * 60 * 1000;  // 1 h
    private static final int    SCOPE_BUCKETS = 6;               // so 5 h to 6 h of validity

    private static byte[] newScopeKey() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        return key;
    }

    /** Signs {@code scope}. Stable within the hour, so it is safe to mint on every poll. */
    public static String mintScopeToken(String scope) {
        long expiry = (System.currentTimeMillis() / SCOPE_BUCKET + SCOPE_BUCKETS) * SCOPE_BUCKET;
        return expiry + "." + signScope(scope, expiry);
    }

    /** True when {@code token} was minted for exactly this scope and has not expired. */
    public static boolean isValidScopeToken(String scope, String token) {
        if (token == null || token.isBlank()) return false;
        int dot = token.indexOf('.');
        if (dot <= 0) return false;
        long expiry;
        try {
            expiry = Long.parseLong(token.substring(0, dot));
        } catch (NumberFormatException e) {
            return false;
        }
        if (System.currentTimeMillis() > expiry) return false;
        byte[] expected = signScope(scope, expiry).getBytes(StandardCharsets.UTF_8);
        byte[] actual   = token.substring(dot + 1).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static String signScope(String scope, long expiry) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SCOPE_KEY, "HmacSHA256"));
            mac.update(scope.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            mac.update(Long.toString(expiry).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    // ── Rate limiting ──────────────────────────────────────────────────────────

    public static boolean isRateLimited(String ip) {
        long[] s = loginAttempts.get(ip);
        if (s == null) return false;
        long now = System.currentTimeMillis();
        if (s[1] > 0 && now < s[1]) return true;          // actively locked out
        if (now - s[2] > WINDOW_MS) { loginAttempts.remove(ip); return false; } // window expired
        return false;
    }

    public static void recordFailedLogin(String ip) {
        long now = System.currentTimeMillis();
        loginAttempts.compute(ip, (k, s) -> {
            if (s == null || now - s[2] > WINDOW_MS) return new long[]{1, 0, now};
            s[0]++;
            if (s[0] >= FAIL_LIMIT) s[1] = now + LOCKOUT_MS;
            return s;
        });
    }

    public static void clearFailedLogins(String ip) {
        loginAttempts.remove(ip);
    }

    // ── Auth check helper ──────────────────────────────────────────────────────

    public static boolean isAuthorized(java.util.Map<String, String> headers) {
        // Intentionally quiet: this runs on every admin request. Logging tokens (even
        // prefixes) or per-request success/fail floods logs and leaks session material
        // when log packs are shared. Callers return 403 on false; login still rate-limits.
        if (noPasswordSet()) {
            return true;
        }
        String auth = headers.get("authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return false;
        }
        return isValidSession(auth.substring(7).trim());
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
