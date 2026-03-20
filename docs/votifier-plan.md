# Votifier Listener — Feature Plan

Optional built-in Votifier v1/v2 listener so voting sites can notify the server when a player votes. No external plugin required.

---

## Overview

Voting sites (e.g., Planet Minecraft, Minecraft-Server-List) send a TCP packet to the server after a player votes. The server listens on a configurable port (default `8192`) and fires an internal event when a valid vote is received.

This is **optional** — disabled by default, enabled via config.

---

## Protocol Summary

### Transport
- **TCP** on a configurable port (default `8192`)
- One connection per vote, short-lived

### Handshake (server → client on connect)
The server sends a single line immediately on connection:

```
VOTIFIER <version> <json>\n
```

- `<version>` — a version string (e.g., `2.6.8` or similar)
- `<json>` — a JSON object containing a random **challenge** string used for v2 auth

> **GAP:** Confirm exact greeting format from NuVotifier source. Specifically: does v1-only mode omit the JSON? Does the version string matter to clients?

### v1 Packet (client → server)
- **256 bytes**, RSA-encrypted with the server's **public key**
- Decrypted with server's **private key**
- Plaintext contains newline-delimited fields:

```
VOTE\n
<serviceName>\n
<username>\n
<address>\n
<timestamp>\n
```

> **GAP:** Confirm RSA key size (likely 2048-bit), padding scheme (likely PKCS1), and exact byte layout of the decrypted plaintext. Confirm whether unused bytes are zero-padded or random.

### v2 Packet (client → server)
- Magic bytes: `0x733A` (2 bytes, big-endian)
- Payload length: 2 bytes, big-endian unsigned short
- Payload: UTF-8 JSON string

Payload JSON structure:
```json
{
  "payload": "<json string>",
  "signature": "<base64 string>"
}
```

The inner `payload` value is a JSON string (double-encoded) containing:
```json
{
  "serviceName": "...",
  "username": "...",
  "address": "...",
  "timestamp": "...",
  "challenge": "<must match server's challenge>"
}
```

The `signature` is HMAC-SHA256 of the raw `payload` string, keyed by the shared **token**, then base64-encoded.

> **GAP:** Confirm whether `payload` is a nested JSON string (double-encoded) or an object. Confirm HMAC algorithm (SHA-256 vs SHA-1). Confirm base64 variant (standard vs URL-safe).

### Server Response
> **GAP:** Does the server send any response after receiving a valid vote? After an invalid one? Or does it just close the connection? Confirm from NuVotifier source.

---

## Implementation Plan

### 1. Config (`SmpConfig.java` / `quackedsmp.json`)

Add a `votifier` block:

```json
"votifier": {
  "enabled": false,
  "port": 8192,
  "token": ""
}
```

- `enabled` — gates the listener entirely
- `port` — TCP port to bind
- `token` — shared secret for v2 HMAC verification
- RSA keypair is generated on first run and stored as files (not in JSON config)

> **GAP:** Decide where to store the RSA keypair files. NuVotifier uses `plugins/NuVotifier/rsa/` — we should pick an analogous server-relative path. Confirm how to get the server's root directory from `MinecraftServer` in both Fabric and NeoForge.

### 2. Key Management (`votifier/VotifierKeys.java`)

- On startup (if enabled): check for existing keypair files. If missing, generate a fresh RSA keypair and save to disk.
- Expose `PublicKey` and `PrivateKey` for use by the listener.
- Print the public key to the server log on startup so admins can copy it to voting sites.

> **GAP:** Confirm RSA key size. NuVotifier historically used 2048-bit. Verify the key serialization format expected by voting sites (PEM? raw DER? Base64-encoded DER?).

### 3. TCP Listener (`votifier/VotifierListener.java`)

- Runs on a dedicated daemon thread (not the server tick thread).
- Binds `ServerSocket` on the configured port.
- On each accepted connection:
  1. Set a short read timeout (e.g., 5s) to prevent hanging.
  2. Send the greeting line with a fresh random challenge (UUID or random hex string).
  3. Read the first few bytes to detect v1 vs v2:
     - If first 2 bytes == `0x733A` → v2 path
     - Otherwise → v1 path (read 256 bytes)
  4. Decrypt/verify, parse vote fields.
  5. Close connection.
- Each connection handled in its own short-lived thread (or a small thread pool).
- Listener starts when the Minecraft server starts (after `ServerStartedEvent`) and stops on `ServerStoppingEvent`.

> **GAP:** Confirm the v1/v2 detection heuristic. Does the client send the magic bytes before the server finishes the greeting, or only after? Is there a timing issue?

### 4. Vote Processing (`votifier/VoteHandler.java`)

- Receives a parsed `VoteData` record:
  ```java
  record VoteData(String serviceName, String username, String address, String timestamp) {}
  ```
- Looks up the player on the server (may be offline — handle gracefully).
- Fires a platform event or calls a common handler.
- Reward logic (TBD — see open questions below).

### 5. Platform Wiring

**NeoForge** (`SmpEvents.java`): subscribe to `ServerStartedEvent` and `ServerStoppingEvent` to start/stop the listener.

**Fabric** (`SmpUtilsModFabric.java`): use `ServerLifecycleEvents.SERVER_STARTED` and `SERVER_STOPPING`.

Both delegate to `VotifierListener.start(server)` / `VotifierListener.stop()`.

---

## Open Questions (for owner to decide before implementing)

1. **Rewards**: What should happen when a vote is received? Options:
   - Give the player items
   - Run a configurable command (e.g., `give <player> diamond 1`)
   - Broadcast a message to the server
   - Queue the reward if the player is offline and apply on next login

2. **Offline votes**: Should votes for offline players be stored and applied later? If yes, needs NBT persistence.

3. **v1 support**: v1 is legacy and uses weaker RSA-only auth. Should we support it at all, or v2-only?

4. **Admin command**: Should there be a `/smp votifier` subcommand to view stats, reload keys, or test a fake vote?

---

## Files to Create

```
common/src/main/java/mc/smpessentials/votifier/
  VotifierListener.java   — TCP server socket, thread management
  VotifierKeys.java       — RSA keypair load/generate/save
  VoteHandler.java        — vote processing and reward dispatch
  VoteData.java           — simple data record
```

Config additions to `SmpConfig.java` and `ConfigIO.java`.

---

## Research Checklist (next agent)

Before writing any code, verify the following against NuVotifier source (`https://github.com/NuVotifier/NuVotifier`):

- [ ] Exact greeting line format
- [ ] RSA key size and padding (`Cipher.getInstance(...)` call)
- [ ] RSA key file format (PEM headers? raw base64?)
- [ ] v1 plaintext byte layout
- [ ] v2 payload JSON structure (nested string vs object)
- [ ] v2 HMAC algorithm and base64 variant
- [ ] Server response after vote (if any)
- [ ] v1/v2 detection method
- [ ] How to get the server root directory path in NeoForge/Fabric for key file storage
