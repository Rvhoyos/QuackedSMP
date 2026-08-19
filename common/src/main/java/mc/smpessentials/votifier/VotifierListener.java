package mc.smpessentials.votifier;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.smpessentials.SmpUtilsMod;
import mc.smpessentials.config.SmpConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// Optional NuVotifier v2 TCP listener. Binds on VOTIFIER_PORT when enabled.
// Protocol: server sends challenge, client sends HMAC-SHA256-signed JSON payload, server verifies and responds.
public final class VotifierListener {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile ServerSocket serverSocket;
    private static volatile ExecutorService pool;
    private static volatile Thread listenerThread;
    private static final AtomicBoolean running = new AtomicBoolean(false);

    private VotifierListener() {}

    public static void start() {
        if (!SmpConfig.VOTIFIER_ENABLED) return;
        if (SmpConfig.VOTIFIER_TOKEN.isBlank()) {
            String generated = new BigInteger(128, RANDOM).toString(16);
            SmpConfig.VOTIFIER_TOKEN = generated;
            mc.smpessentials.config.ConfigIO.save();
            SmpUtilsMod.LOGGER.info("[Votifier] ============================================================");
            SmpUtilsMod.LOGGER.info("[Votifier] No token found, generated a new token and saved to config.");
            SmpUtilsMod.LOGGER.info("[Votifier] Token: {}", generated);
            SmpUtilsMod.LOGGER.info("[Votifier] Use this token when registering on voting sites.");
            SmpUtilsMod.LOGGER.info("[Votifier] ============================================================");
        }
        if (running.getAndSet(true)) return;

        pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "QuackVotifier-Worker");
            t.setDaemon(true);
            return t;
        });

        listenerThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(SmpConfig.VOTIFIER_PORT);
                SmpUtilsMod.LOGGER.info("[Votifier] Listening on port {}", SmpConfig.VOTIFIER_PORT);
                while (running.get()) {
                    try {
                        Socket conn = serverSocket.accept();
                        pool.execute(() -> handle(conn));
                    } catch (IOException e) {
                        if (running.get()) {
                            SmpUtilsMod.LOGGER.warn("[Votifier] Accept error: {}", e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                SmpUtilsMod.LOGGER.error("[Votifier] Failed to bind port {}: {}", SmpConfig.VOTIFIER_PORT, e.getMessage());
                running.set(false);
            }
        }, "QuackVotifier-Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    // Closes the socket, shuts down workers, and joins the listener thread (waits up to 2s).
    public static void stop() {
        if (!running.getAndSet(false)) return;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        if (pool != null) pool.shutdownNow();
        if (listenerThread != null) {
            try { listenerThread.join(2_000); } catch (InterruptedException ignored) {}
        }
        SmpUtilsMod.LOGGER.info("[Votifier] Stopped");
    }

    public static void restart() {
        stop();
        start();
    }

    private static void handle(Socket conn) {
        try {
            conn.setSoTimeout(5_000);
            OutputStream out = conn.getOutputStream();
            InputStream in  = conn.getInputStream();

            // Generate a NuVotifier-style challenge: 130-bit SecureRandom in base-32
            String challenge = new BigInteger(130, RANDOM).toString(32);

            // 1. Send greeting
            out.write(("VOTIFIER 2 " + challenge + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();

            // 2. Read first 2 bytes, magic detection
            int b0 = in.read(), b1 = in.read();
            if (b0 < 0 || b1 < 0) return;

            if (b0 != 0x73 || b1 != 0x3A) {
                SmpUtilsMod.LOGGER.warn("[Votifier] Received v1 packet, only v2 is supported");
                sendError(out, "UnsupportedProtocol", "Only Votifier v2 is supported");
                return;
            }

            // 3. Read 2-byte big-endian payload length
            int lenHi = in.read(), lenLo = in.read();
            if (lenHi < 0 || lenLo < 0) return;
            int payloadLen = (lenHi << 8) | lenLo;
            if (payloadLen <= 0 || payloadLen > 1024) {
                sendError(out, "InvalidPacket", "Bad payload length: " + payloadLen);
                return;
            }

            // 4. Read JSON payload
            byte[] jsonBytes = in.readNBytes(payloadLen);
            if (jsonBytes.length != payloadLen) return;
            String outerJson = new String(jsonBytes, StandardCharsets.UTF_8);

            // 5. Parse outer JSON
            JsonObject outer     = JsonParser.parseString(outerJson).getAsJsonObject();
            String payloadStr    = outer.get("payload").getAsString();
            String signatureB64  = outer.get("signature").getAsString();

            // 6. Verify HMAC-SHA256 (constant-time compare via MessageDigest.isEqual)
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SmpConfig.VOTIFIER_TOKEN.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(payloadStr.getBytes(StandardCharsets.UTF_8));
            byte[] actual   = Base64.getDecoder().decode(signatureB64);
            if (!MessageDigest.isEqual(expected, actual)) {
                SmpUtilsMod.LOGGER.warn("[Votifier] HMAC mismatch, check your token");
                sendError(out, "SignatureException", "Invalid signature");
                return;
            }

            // 7. Parse inner payload and verify challenge
            JsonObject inner = JsonParser.parseString(payloadStr).getAsJsonObject();
            String voteChallenge = inner.has("challenge") ? inner.get("challenge").getAsString() : "";
            if (!challenge.equals(voteChallenge)) {
                sendError(out, "ChallengeException", "Challenge mismatch");
                return;
            }

            VoteData vote = new VoteData(
                    inner.get("serviceName").getAsString(),
                    inner.get("username").getAsString(),
                    inner.has("address")   ? inner.get("address").getAsString()   : "",
                    inner.has("timestamp") ? inner.get("timestamp").getAsString() : ""
            );

            SmpUtilsMod.LOGGER.info("[Votifier] Vote from {} via {}", vote.username(), vote.serviceName());
            VoteHandler.onVote(vote);

            // 8. Send OK
            out.write("{\"status\":\"ok\"}\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

        } catch (SocketTimeoutException e) {
            SmpUtilsMod.LOGGER.warn("[Votifier] Connection timed out");
        } catch (Exception e) {
            SmpUtilsMod.LOGGER.warn("[Votifier] Error handling connection: {}", e.getMessage());
        } finally {
            try { conn.close(); } catch (IOException ignored) {}
        }
    }

    private static void sendError(OutputStream out, String cause, String message) {
        try {
            String safe = message.replace("\\", "\\\\").replace("\"", "\\\"");
            out.write(("{\"status\":\"error\",\"cause\":\"" + cause + "\",\"error\":\"" + safe + "\"}\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {}
    }
}
