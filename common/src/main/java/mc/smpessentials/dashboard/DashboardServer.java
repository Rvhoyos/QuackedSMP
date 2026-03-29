package mc.smpessentials.dashboard;

import mc.smpessentials.SmpUtilsMod;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

// Single-port server handling plain HTTP and WebSocket on the same port.
public final class DashboardServer extends Thread {

    private final int port;
    private volatile ServerSocket serverSocket;

    // WebSocket clients — added on upgrade, removed on disconnect
    private final Set<Socket> wsClients = ConcurrentHashMap.newKeySet();

    @FunctionalInterface
    public interface RouteHandler {
        String handle(String method, Map<String, String> headers, String body);
    }

    // Like RouteHandler but receives the raw InputStream for binary uploads (bypasses 64KB body cap).
    @FunctionalInterface
    public interface UploadRouteHandler {
        String handle(String method, Map<String, String> headers, InputStream stream, long contentLength);
    }

    // HTTP route table: path → handler
    private final Map<String, RouteHandler>       routes       = new ConcurrentHashMap<>();
    // Upload route table: path → handler (bypasses the 64 KB body cap)
    private final Map<String, UploadRouteHandler> uploadRoutes = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Dashboard-Conn");
        t.setDaemon(true);
        return t;
    });

    public DashboardServer(int port) {
        super("Dashboard-Server");
        setDaemon(true);
        this.port = port;
    }

    public void addRoute(String path, Supplier<String> handler) {
        routes.put(path, (m, h, b) -> handler.get());
    }

    public void addRoute(String path, RouteHandler handler) {
        routes.put(path, handler);
    }

    public void addUploadRoute(String path, UploadRouteHandler handler) {
        uploadRoutes.put(path, handler);
    }

    // ── Server loop ────────────────────────────────────────────────────────────

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            SmpUtilsMod.LOGGER.info("[Dashboard] Server listening on port {}", port);
            while (!serverSocket.isClosed()) {
                try {
                    Socket conn = serverSocket.accept();
                    executor.execute(() -> handleConnection(conn));
                } catch (IOException e) {
                    if (!serverSocket.isClosed())
                        SmpUtilsMod.LOGGER.warn("[Dashboard] Accept error: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            SmpUtilsMod.LOGGER.error("[Dashboard] Failed to bind on port {}: {}", port, e.getMessage());
        }
    }

    // ── Connection dispatch ────────────────────────────────────────────────────

    private void handleConnection(Socket socket) {
        try {
            socket.setSoTimeout(10_000);
            InputStream in = socket.getInputStream();

            // --- Read request line ---
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) { socket.close(); return; }

            String[] parts = requestLine.split(" ", 3);
            if (parts.length < 2) { socket.close(); return; }
            String method      = parts[0];
            String path        = parts[1];
            int q = path.indexOf('?');
            String queryString = "";
            if (q != -1) { queryString = path.substring(q + 1); path = path.substring(0, q); }

            // --- Read headers (all of them, lowercase keys) ---
            Map<String, String> headers = new LinkedHashMap<>();
            String  wsKey     = null;
            boolean isUpgrade = false;
            int     contentLength = 0;
            String  line;
            while (!(line = readLine(in)).isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key = line.substring(0, colon).trim().toLowerCase();
                    String val = line.substring(colon + 1).trim();
                    headers.put(key, val);
                    if (key.equals("upgrade") && val.toLowerCase().contains("websocket")) isUpgrade = true;
                    if (key.equals("sec-websocket-key")) wsKey = val;
                    if (key.equals("content-length")) {
                        try { contentLength = Integer.parseInt(val); } catch (NumberFormatException ignored) {}
                    }
                }
            }

            // Expose query string to route handlers via pseudo-header
            headers.put("x-query-string", queryString);

            if (isUpgrade && wsKey != null) {
                handleWebSocket(socket, wsKey);
                return;
            }

            // Check for upload route BEFORE reading body — upload handlers receive the
            // raw InputStream directly so they are not limited by the 64 KB cap.
            UploadRouteHandler uploadHandler = uploadRoutes.get(path);
            if (uploadHandler != null) {
                handleHttpUpload(socket, method, headers, uploadHandler, (long) contentLength, in);
                return;
            }

            // --- Read body (standard routes, max 64 KB) ---
            String body = "";
            if (contentLength > 0 && contentLength <= 65_536) {
                byte[] bodyBytes = in.readNBytes(contentLength);
                body = new String(bodyBytes, StandardCharsets.UTF_8);
            }

            handleHttp(socket, method, path, headers, body);
        } catch (IOException | NoSuchAlgorithmException e) {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // ── HTTP ───────────────────────────────────────────────────────────────────

    private void handleHttp(Socket socket, String method, String path,
                            Map<String, String> headers, String reqBody) throws IOException {
        try (socket) {
            OutputStream out = socket.getOutputStream();
            if ("OPTIONS".equals(method)) {
                writeResponse(out, 204, "text/plain", "no-store", new byte[0]);
                return;
            }
            RouteHandler handler = routes.get(path);
            if (handler != null) {
                String result = handler.handle(method, headers, reqBody);
                if (AdminHandler.isErr(result)) {
                    byte[] body = AdminHandler.errBody(result).getBytes(StandardCharsets.UTF_8);
                    writeResponse(out, AdminHandler.errStatus(result), "application/json; charset=utf-8", "no-store", body);
                } else {
                    byte[] body = result.getBytes(StandardCharsets.UTF_8);
                    writeResponse(out, 200, "application/json; charset=utf-8", "no-store", body);
                }
            } else {
                serveStatic(out, path);
            }
        }
    }

    // Passes the raw InputStream directly to the upload handler instead of pre-reading the body.
    // Also handles OPTIONS preflight. Closes the socket when done.
    private void handleHttpUpload(Socket socket, String method, Map<String, String> headers,
                                   UploadRouteHandler handler, long contentLength, InputStream in)
            throws IOException {
        try (socket) {
            OutputStream out = socket.getOutputStream();
            if ("OPTIONS".equals(method)) {
                writeResponse(out, 204, "text/plain", "no-store", new byte[0]);
                return;
            }
            String result;
            try {
                result = handler.handle(method, headers, in, contentLength);
            } catch (Exception e) {
                SmpUtilsMod.LOGGER.warn("[Dashboard] Upload handler error: {}", e.getMessage());
                byte[] body = "{\"error\":\"Internal server error\"}".getBytes(StandardCharsets.UTF_8);
                writeResponse(out, 500, "application/json; charset=utf-8", "no-store", body);
                return;
            }
            if (AdminHandler.isErr(result)) {
                byte[] body = AdminHandler.errBody(result).getBytes(StandardCharsets.UTF_8);
                writeResponse(out, AdminHandler.errStatus(result), "application/json; charset=utf-8", "no-store", body);
            } else {
                byte[] body = result.getBytes(StandardCharsets.UTF_8);
                writeResponse(out, 200, "application/json; charset=utf-8", "no-store", body);
            }
        }
    }

    private void serveStatic(OutputStream out, String path) throws IOException {
        if (path.contains(".."))                { writeResponse(out, 403, "text/plain", "no-store", "Forbidden".getBytes()); return; }
        if (path.equals("/") || path.isEmpty()) path = "/index.html";

        InputStream resource = DashboardServer.class.getResourceAsStream("/dashboard" + path);
        if (resource == null) { writeResponse(out, 404, "text/plain", "no-store", "Not found".getBytes()); return; }

        // index.html must never be cached — it references hashed asset filenames that change each build.
        // Hashed assets (JS/CSS) are immutable for a given filename, so they can be cached forever.
        String cacheControl = path.equals("/index.html")
                ? "no-store, no-cache, must-revalidate"
                : "public, max-age=31536000, immutable";

        try (resource) {
            byte[] bytes = resource.readAllBytes();
            writeResponse(out, 200, contentType(path), cacheControl, bytes);
        }
    }

    private void writeResponse(OutputStream out, int code, String type, String cacheControl, byte[] body) throws IOException {
        String reason = switch (code) {
            case 200 -> "OK"; case 204 -> "No Content";
            case 400 -> "Bad Request"; case 401 -> "Unauthorized";
            case 403 -> "Forbidden"; case 405 -> "Method Not Allowed";
            case 413 -> "Payload Too Large"; case 429 -> "Too Many Requests";
            case 503 -> "Service Unavailable"; default -> "Not Found";
        };
        String headers = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: " + cacheControl + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\n"
                + "Access-Control-Allow-Headers: Content-Type, Authorization, X-Filename\r\n"
                + "Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    // ── WebSocket ──────────────────────────────────────────────────────────────

    private void handleWebSocket(Socket socket, String wsKey) throws IOException, NoSuchAlgorithmException {
        OutputStream out = socket.getOutputStream();
        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + computeAcceptKey(wsKey) + "\r\n\r\n";
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();

        wsClients.add(socket);
        try {
            readFrames(socket, out);
        } finally {
            wsClients.remove(socket);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void readFrames(Socket socket, OutputStream out) throws IOException {
        InputStream in = socket.getInputStream();
        socket.setSoTimeout(60_000);

        while (!socket.isClosed()) {
            int byte0, byte1;
            try {
                byte0 = in.read();
                byte1 = in.read();
            } catch (java.net.SocketTimeoutException e) {
                synchronized (socket) {
                    try { out.write(new byte[]{(byte) 0x89, 0x00}); out.flush(); } catch (IOException ex) { break; }
                }
                continue;
            }
            if (byte0 == -1 || byte1 == -1) break;

            int opcode = byte0 & 0x0F;
            boolean masked = (byte1 & 0x80) != 0;
            long payloadLen = byte1 & 0x7F;

            if (payloadLen == 126) {
                int hi = in.read(), lo = in.read();
                if (hi == -1 || lo == -1) break;
                payloadLen = ((hi & 0xFF) << 8) | (lo & 0xFF);
            } else if (payloadLen == 127) {
                payloadLen = 0;
                for (int i = 0; i < 8; i++) {
                    int b = in.read();
                    if (b == -1) return;
                    payloadLen = (payloadLen << 8) | (b & 0xFF);
                }
            }
            if (payloadLen > 65536) break;

            byte[] maskKey = new byte[4];
            if (masked) {
                int r = 0;
                while (r < 4) { int n = in.read(maskKey, r, 4 - r); if (n == -1) return; r += n; }
            }

            byte[] payload = new byte[(int) payloadLen];
            int total = 0;
            while (total < payload.length) {
                int n = in.read(payload, total, payload.length - total);
                if (n == -1) return;
                total += n;
            }
            if (masked) {
                for (int i = 0; i < payload.length; i++) payload[i] ^= maskKey[i % 4];
            }

            switch (opcode) {
                case 0x8 -> { // Close
                    synchronized (socket) {
                        try { out.write(new byte[]{(byte) 0x88, 0x00}); out.flush(); } catch (IOException ignored) {}
                    }
                    return;
                }
                case 0x9 -> { // Ping → Pong
                    byte[] pong = new byte[2 + Math.min(payload.length, 125)];
                    pong[0] = (byte) 0x8A;
                    pong[1] = (byte) Math.min(payload.length, 125);
                    System.arraycopy(payload, 0, pong, 2, pong.length - 2);
                    synchronized (socket) {
                        try { out.write(pong); out.flush(); } catch (IOException ignored) {}
                    }
                }
            }
        }
    }

    // Sends a JSON string as a WebSocket text frame to all connected clients.
    public void broadcast(String json) {
        byte[] frame = buildTextFrame(json.getBytes(StandardCharsets.UTF_8));
        for (Socket socket : wsClients) {
            try {
                synchronized (socket) {
                    socket.getOutputStream().write(frame);
                    socket.getOutputStream().flush();
                }
            } catch (IOException e) {
                wsClients.remove(socket);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    // Stops the executor, closes the server socket, and disconnects all WebSocket clients.
    public void shutdown() {
        executor.shutdown();
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        for (Socket s : wsClients) { try { s.close(); } catch (IOException ignored) {} }
        wsClients.clear();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    // Reads one CRLF-terminated line from a raw InputStream without buffering past the newline.
    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int prev = -1;
        while (true) {
            int b = in.read();
            if (b == -1) return sb.toString();
            if (b == '\n' && prev == '\r') return sb.substring(0, sb.length() - 1);
            sb.append((char) b);
            prev = b;
        }
    }

    private static String computeAcceptKey(String key) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    private static byte[] buildTextFrame(byte[] payload) {
        int len = payload.length;
        if (len < 126) {
            byte[] f = new byte[2 + len]; f[0] = (byte) 0x81; f[1] = (byte) len;
            System.arraycopy(payload, 0, f, 2, len); return f;
        } else if (len < 65536) {
            byte[] f = new byte[4 + len]; f[0] = (byte) 0x81; f[1] = (byte) 126;
            f[2] = (byte) (len >> 8); f[3] = (byte) (len & 0xFF);
            System.arraycopy(payload, 0, f, 4, len); return f;
        } else {
            byte[] f = new byte[10 + len]; f[0] = (byte) 0x81; f[1] = (byte) 127;
            for (int i = 0; i < 8; i++) f[2 + i] = (byte) ((long) len >> (56 - 8 * i));
            System.arraycopy(payload, 0, f, 10, len); return f;
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (path.endsWith(".css"))  return "text/css; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".svg"))  return "image/svg+xml";
        if (path.endsWith(".png"))  return "image/png";
        return "application/octet-stream";
    }
}
