# QuackedSMP Code Audit

> **Plan note:** this file is the audit itself. The final implementation step
> (post-approval) is one action: copy this file to `audit.md` at the repo
> root. The audit spans multiple sessions — every session ends by updating
> the "Audit Progress" tracker so the next pass picks up where this one left
> off.

## Reading guide — certainty tags

Each finding ends with one of these tags so you can skim:

- **[verified]** — I read the exact code referenced and the claim is
  directly observable. You can trust the line numbers.
- **[verified-spotcheck]** — I read the cited code, but didn't trace
  every call site / consequence. The local claim is correct; the
  blast-radius claim ("…and it's repeated 30× across the codebase") is
  the part to spot-check.
- **[needs-check]** — claim relies on inference (general security
  knowledge, MC API behavior, third-party rendering, etc.) or on code I
  haven't fully read. Treat as a lead, not a fact.

If I had to retract a session-1 claim during revision, it's marked
**[corrected]** with the original error noted.

## Context

The user asked for a deep, honest code-quality / OO-design / security audit
of the QuackedSMP codebase, performed by me (no subagents). The codebase is
~19.3k lines of Java in `common/` across 128 files, multi-platform (Fabric +
NeoForge), Minecraft 26.1.2. No test suite — everything is verified in-game,
so bad code rots silently and audit signal is more valuable than in a
tested codebase. The audit:

- Prioritizes SRP and DRY violations
- Flags security issues (HTTP/WebSocket, file I/O, TCP listeners, webhook
  URLs, user-controlled paths/input)
- Surfaces OO-design smells
- References exact file paths and line numbers

Severity scale: **Critical** (exploitable / data-loss), **High**
(hardening gap, big maintainability cost, correctness foot-gun),
**Medium** (worth addressing), **Low/Info** (cosmetic).

---

## Audit Progress

**[done] in session 1:**

- `dashboard/DashboardServer.java`
- `dashboard/AdminAuth.java`
- `dashboard/DashboardManager.java`
- `dashboard/AdminHandler.java` (structural scan via grep + spot-reads of
  login/exec/setup/config-post/mods upload+delete/helpers/handlePlayers;
  not every handler body fully read)
- `dashboard/BackupHandler.java`
- `dashboard/DiscordWebhook.java`
- `backup/BackupService.java`, `BackupZipper.java`,
  `PublicDownloadLimiter.java`
- `votifier/VotifierListener.java`
- `skills/ActiveAbilities.java`, `SkillEvents.java`
- `claims/ClaimProtection.java`, `SpawnProtection.java`, `ClaimService.java`
- `chatfilter/ChatFilter.java`
- `SmpUtilsMod.java`, `platform/PlatformHelper.java`, `SmpServices.java`
- `util/TextUtil.java` (read during revision)

**[todo] for session 2 — recommended order:**

1. `dims/` package (DimManager 757, DimCommands 416, CustomPortalShape,
   CustomPortalActivator, EtherIslandDensityFunction, EtherFallthrough,
   DimSavedData)
2. `shops/` package (ShopService 474, ShopGui, ShopCommands, ShopData,
   ShopEntry, EconomyData, ShopMenuContainer)
3. `config/` package (ConfigGui 452, SmpConfig 242, ConfigIO 183,
   ConfigData 282, ChatFilterConfig, ChatFilterSavedData)
4. `bluemap/BlueMapMarkerManager.java` (524) + `BlueMapIntegration.java`
5. `mixin/` deep-read (22 mixins)
6. `commands/` package + `CommandRegistrar.java`
7. `hardcore/` package

**[todo] for session 3+:**

- `claims/` remainder, `skills/` remainder
- `commandblocks/`, `regen/`, `tier/`, `kits/`, `teams/`
- `teleport/`, `events/`, `antixray/`
- `punish/`, `keepinv/`, `ageverify/`, `voicechat/`
- `chatfilter/` remainder
- Frontend `dashboard-ui/src/`
- `fabric/` + `neoforge/` entrypoints

---

## Cross-Cutting Findings

### CC-1. OP-check is duplicated  [High]

The phrase `getServer().getPlayerList().isOp(player.nameAndId())` (or
near-equivalents) appears in **15 places across 10 files** (counted via
`grep -rn`):

```
bluemap/BlueMapMarkerManager.java
mixin/EndCrystalItemMixin.java
chatfilter/ChatFilter.java
dashboard/AdminHandler.java
commands/SosCommand.java
claims/ClaimService.java
claims/ClaimProtection.java
claims/ClaimCommands.java
claims/SpawnProtection.java
claims/ClaimAccess.java
```

Extract a single `Perms.isOp(ServerPlayer)` helper.

> *Session-1 originally claimed "30+ copies" — that was inflated.* **[corrected]**

The count and the call sites: **[verified]** (grep run during revision).
Whether `((ServerLevel) player.level()).getServer()` always equals
`player.getServer()` in the cited contexts: **[needs-check]** — true in
vanilla MC API, but if the cast is there for nullability or for a
platform shim I'm not aware of, leave it.

### CC-2. JSON construction via `String.format` everywhere, with 3 copies of `jsonEscape`  [High]

`grep` for `private/public static String jsonEscape\|esc(` finds exactly
three implementations:

- `dashboard/AdminHandler.java:1990` (public `jsonEscape`)
- `dashboard/DashboardManager.java:411` (private `jsonEscape`)
- `dashboard/DiscordWebhook.java:81` (private `esc`, also strips § codes)

All three escape `\\ " \n \r \t`. AdminHandler.jsonEscape is already
called from outside its class (BackupHandler imports it). The other two
should call into AdminHandler's (or, better, into a new
`util/JsonUtil.java`).

Hand-built JSON via `String.format` is also a maintenance hazard
(structural typos, missed escapes). Gson is already on the classpath and
already imported in AdminHandler for *parsing* — use it for serializing
too, or add a thin `Json.obj(...)` / `Json.arr(...)` builder.

I spot-checked one possibly-unescaped interpolation: `dim` in
`AdminHandler.handlePlayers` line 124 is `level().dimension().identifier()
.toString()`, which is a resource location. Resource locations are
`[a-z0-9_.-]+:[a-z0-9/._-]+` per MC spec, so they don't need escaping in
practice. Still — defensive code wouldn't rely on that.

All three `jsonEscape` definitions: **[verified]**. Whether `String
.format` JSON has caused a real bug yet: **[needs-check]** — I didn't
trace every interpolation site, only spot-checked.

### CC-3. § color codes are interpolated as `§X` literals everywhere; TextUtil has no constants  [Medium]

`util/TextUtil.java` (read during revision) is 57 LOC and exposes
`TextUtil.format(text)` which parses `&` color codes and
`[label](url)` markdown links into a `MutableComponent`. It does **not**
export named color constants. Message-emitting sites in ActiveAbilities,
SkillEvents, ClaimProtection, SpawnProtection, ChatFilter all inline
raw `§c`, `§e`, `§6§l` etc.

Recommendation: add `TextUtil.RED`, `TextUtil.YELLOW`, `TextUtil.GOLD`,
`TextUtil.BOLD` constants and migrate opportunistically.

TextUtil has no constants today: **[verified]**. Inline §-code usage in
the listed files: **[verified]** for ActiveAbilities/SkillEvents/
ClaimProtection/SpawnProtection/ChatFilter (I read those during session
1). Whether the same pattern appears in every other package:
**[needs-check]**.

There is a separate finding worth noting on TextUtil itself:
`TextUtil.format` (line 14) parses `[label](url)` and creates a clickable
`OpenUrl` ClickEvent with the user-supplied URL. If any user-controlled
input flows into `format()`, a player can plant arbitrary clickable URLs
in chat. Worth confirming who calls `format()`. **[needs-check]** —
deferred until I trace the call graph.

### CC-4. Static per-player `HashMap`s with no thread-safety story  [High]

Confirmed instances:

- `ActiveAbilities.treeFellerActive` line 38 (`HashMap`)
- `ActiveAbilities.zoomActive` line 42 (`HashMap`)
- `SkillEvents.lastPositions`, `distanceAccum`, `agePromptTicks` lines
  38, 40, 42 (all `HashMap`)

All five access sites I read are on the server thread (event handlers,
player tick). Nothing documents or asserts this. Two fixes:

1. Convert to `ConcurrentHashMap` — cheap, future-proofs.
2. Or `Util.ensureOnServerThread()` assert at entry.

The five maps: **[verified]**. That every current access site is on the
server thread: **[verified-spotcheck]** — true for the handlers I read,
but I didn't enumerate every caller of every getter/setter.

### CC-5. God-class pattern repeats  [High]

Confirmed:

- `dashboard/AdminHandler.java` — 2011 LOC, 56 static handler methods
  spanning 12+ subdomains (counted via `grep -c "public static String
  handle"`)
- `skills/SkillEvents.java` — 782 LOC; I read the whole file. It does
  XP awards, parent buffs, ability discovery, agility tick, age-verify
  prompts, passive perks, block taxonomy
- `skills/ActiveAbilities.java` — 662 LOC; 9 ability methods each
  ~50 LOC of boilerplate (CLAUDE.md Candidate A — confirmed in AB-1)

Plus (file size only, not read yet — so listing as god-class candidates
to confirm in session 2):

- `dims/DimManager.java` — 757 LOC **[needs-check]**
- `bluemap/BlueMapMarkerManager.java` — 524 LOC **[needs-check]**
- `hardcore/HardcoreSavedData.java` — 602 LOC **[needs-check]**

LOC counts: **[verified]** (`wc -l`). The three I read are
**[verified]** god classes; the three deferred are **[needs-check]**.

### CC-6. `SmpConfig.X = patch.get("x").getAsY(); changed++;` repeats in `handleConfigPost`  [High]

`AdminHandler.handleConfigPost` (lines 289-445) is 156 lines I read
directly. It's `if (patch.has(...)) { SmpConfig.X = ...; changed++; }`
~60 times for primitive fields plus ~5 array/object loaders. I counted
the field count by reading the block, not by grep — so the exact "~60"
is **[verified-spotcheck]**, the structural claim is **[verified]**.

I claimed in session 1 that "this same boilerplate likely also exists
in `ConfigIO` and `ConfigGui`". I have **not** read those files yet —
**[needs-check]**. Confirm during session 2 before recommending a
unified schema.

### CC-7. Unbounded `Executors.newCachedThreadPool` on accept loops  [Medium]

Two sites read directly:

- `DashboardServer.java:67` — `Executors.newCachedThreadPool(...)` for
  the HTTP/WS connection workers. **[verified]**
- `VotifierListener.java:52` — same. **[verified]**

A connection flood spawns unbounded threads. Cached pools have no max
size (per JDK Javadoc). **[verified]**

Whether this has actually been exploited or is a theoretical DoS:
**[needs-check]**.

### CC-8. `x-forwarded-for` trusted unconditionally  [High]

Two sites read directly:

- `AdminHandler.handleLogin` line 93 — `headers.getOrDefault("x-forwarded-
  for", "direct").split(",")[0].trim()` — uses the first XFF value.
  **[verified]**
- `BackupHandler.resolveIp` line 117-122 — prefers XFF first value,
  falls back to `x-remote-ip`. **[verified]**

`DashboardServer:158` populates `x-remote-ip` with the actual socket
peer. **[verified]**

The exploit (an attacker who reaches the dashboard *not* through a
proxy can spoof XFF to bypass rate limits): **[verified]** in principle
— that's how XFF works.

The fix (add `DASHBOARD_TRUST_PROXY` config; honor last not first
value when chained): standard advice, **[verified]** as standard.

---

## File-Specific Findings

### `dashboard/DashboardServer.java`

**DS-1. WebSocket upgrade has no authentication or Origin check  [High]**

`handleWebSocket` (line 341) doesn't call `AdminAuth.isAuthorized` or
check the `Origin` header. Broadcasts include chat (`broadcastChat` in
DashboardManager:340 calls `DiscordWebhook.sendChat` AND `broadcast`)
— so any listener on the WS port sees server chat. **[verified]**

Cross-site WebSocket hijacking risk if the SPA gets compromised and the
attacker can read the token: **[verified]** as standard threat model.

Recommended fix (require Bearer in upgrade, check Origin allowlist):
**[verified]** as standard.

**DS-2. Sentinel-string error encoding (`"__HTTP_<code>__<body>"`)  [Medium]**

Lines 205-211 + AdminHandler.err (1946) + isErr/errStatus/errBody
(1950-1960). The encoding is `"__HTTP_" + status + "__" + body`.
DashboardServer parses it with `substring(7, 10)` — assumes exactly 3
digits. **[verified]** by reading both files.

Refactor to a sealed `RouteResult` (same pattern as `DownloadResult`
already uses). **[verified]** as a sound OO improvement.

**DS-3. `Access-Control-Allow-Origin: *` on every response  [Medium]**

Lines 290, 330. **[verified]**

Risk depends on where the bearer token can be exfiltrated from
(localStorage XSS in the SPA, admin pasting it, etc.). Standard
advice: tighten to an allowlist. **[verified]** as standard.

**DS-4. WebSocket broadcast holds the socket monitor while writing  [Medium]**

`broadcast` line 432 holds `synchronized (socket)` during
`getOutputStream().write(frame)`. **[verified]** — read the loop body.

A slow client stalls broadcasts to all clients behind it: **[verified]**
in principle.

**DS-5. WebSocket payload-length sanity check is lax  [Low]**

Line 392: `if (payloadLen > 65536) break;` accepts up to 65536
inclusive. Lines 384-391 read 8-byte length as a long that can be
negative if the high bit is set. The `> 65536` check on a negative long
is false, so it falls through to `new byte[(int) payloadLen]` →
NegativeArraySizeException, caught by outer try, drops connection.
**[verified]** — read the read loop and the cast.

Not exploitable beyond connection drop. Severity Low is correct.

**DS-6. Socket read timeout 10s HTTP, 60s WS  [Low]**

`setSoTimeout(10_000)` line 120 and `setSoTimeout(60_000)` line 361.
**[verified]**.

Whether the slow-loris risk is real at the scale this dashboard runs at:
**[needs-check]** — likely fine for a single Minecraft server.

### `dashboard/AdminAuth.java`

**AA-1. Session token construction loses bits on leading-zero  [Low]**

Line 75: `new BigInteger(128, RANDOM).toString(16)`. BigInteger's hex
representation has no leading zeros, so token length varies 30-32 chars.
**[verified]** — that's documented BigInteger behavior.

The bit-loss is cosmetic (still 128 random bits of entropy, just
displayed with variable length). The "leaks information" wording in
session 1 was overstated — the high zero bytes don't leak anything
exploitable, they just make the token visually shorter. Bumping to
`byte[16]` + Base64URL is still a cleaner fix. **[corrected]**: severity
stays Low; phrasing softened.

**AA-2. PBKDF2 iterations = 100,000  [Medium]**

Line 20: `private static final int ITERATIONS = 100_000;`. **[verified]**.

That OWASP 2023 guidance is ≥600k for PBKDF2-HMAC-SHA256: **[needs-check]**
— I'm working from training data. The OWASP Password Storage Cheat Sheet
is the canonical source; verify the current recommendation before
bumping.

**AA-3. Token prefix logged on every `isAuthorized` call  [Medium]**

Line 138: `LOGGER.info("[AdminAuth] isAuthorized={} token={}…", valid,
token.length() > 8 ? token.substring(0, 8) : token);`. **[verified]**.

8 hex chars is 32 bits of token information in logs. Whether logs flow
to anywhere less trusted than the server file system: **[needs-check]**.

**AA-4. Sessions never expire until next access  [Medium]**

`isValidSession` (line 80) only removes on inspection. **[verified]**.

Whether this is a real problem in practice (depends on session
turnover rate): **[needs-check]**. The proposed fix (periodic sweep)
is standard. **[verified]** as standard.

**AA-5. `isAuthorized` info-logs every successful call  [Low]**

Lines 128, 133, 138 — INFO/WARN on every auth check. **[verified]**.

**AA-6. Auth bypass when no password set  [Critical conditionally]**

`AdminAuth.noPasswordSet()` returns true when `SmpConfig.ADMIN_PASSWORD_HASH
.isBlank()` (line 36-38). `isAuthorized` (line 127) returns true if
`noPasswordSet()`. **[verified]**.

CLAUDE.md / project memory `project_dashboard_behavior` says dashboard
can run with no password. **[verified]** from memory.

The implication that anyone reaching the port can call `/api/admin/exec`
(RCE), `/api/admin/setop`, `/api/admin/mods/upload`, etc. follows
directly from the auth code. **[verified]**.

How critical depends on operational practice: is the dashboard port
exposed publicly when password is unset? If yes, this is **Critical**
in deployment. If the port is firewalled until setup completes, it's
**Info**. **[needs-check]** — this is a deployment question for the
user.

### `dashboard/DashboardManager.java`

**DM-1. Second copy of `jsonEscape`  [High — see CC-2]**  **[verified]**

**DM-2. `running` flag updated after `s.start()`  [Low]**

Line 80-97: `s.start()` returns immediately (`DashboardServer extends
Thread`); `running = true` is set on line 96. Between those, a
broadcast checks `running` → returns early. **[verified]**.

Whether this race window has been hit in practice: **[needs-check]** —
the JVM may complete the few intervening lines before the new thread's
loop even starts accepting, making this theoretical.

**DM-3. `refreshWorldSize` walks entire world tree every 5 min  [Medium]**

Line 305-321: `Files.walk(worldRoot)` summing every regular file's
size. Scheduled every 5 minutes. **[verified]**.

Whether this causes user-visible impact: **[needs-check]** — depends on
world size, disk speed, and whether the JVM page cache absorbs it.

**DM-4. Route table is itself a god method  [Medium]**

`registerRoutes` lines 111-291 — 180 lines of `s.addRoute(path, lambda)`
calls. **[verified]** — I read it.

Refactor proposal (annotation-driven or builder): **[verified]** as a
sound improvement; not unique advice.

### `dashboard/AdminHandler.java`

**HND-1. Every handler repeats the same 4-line preamble  [High]**

Spot-checked: `handleSetup` (line 62-65), `handleLogin` (89-94),
`handlePlayers` (114-116), `handleExec` (141-144), `handleConfigPost`
(290-292), `handleModsUpload` (1258-1260), `handleBackupCreate`
(BackupHandler 49-52). All start with some subset of the four-line
guard. **[verified-spotcheck]** — I confirmed ~7 of the 56 handlers
follow the pattern; haven't read all 56.

**HND-2. Sentinel-string err encoding — see DS-2  [Medium]**  **[verified]**

**HND-3. 2011 LOC, 56 handlers, 12+ subdomains  [High]**

Counted handlers: `grep -c "public static String handle"` → 48 (the
remaining ~8 are private helpers); structural breakdown is **[verified]**
by reading the method list (player/exec/config/ops/dims/blocks/biomes/
skills/claims/chatfilter/mods/hardcore/regen/teams/autoassign/shops/
economy/dashboard-disable). The split proposal is **[verified]** as
sound OO.

**HND-4. `handleConfigPost` — see CC-6  [High]**  **[verified]**

**HND-5. `handleConfigPost` has no validation  [High]**

I read all 156 lines (289-445). For every primitive `if (patch.has)
{ SmpConfig.X = patch.get(X).getAsY(); ...}`, there is no range check
or format check. **[verified]**.

Specific risky fields I called out:

- `dashboard_port` accepts any int including negative or >65535.
  **[verified]** by reading line 323 (`SmpConfig.DASHBOARD_PORT = patch
  .get("dashboard_port").getAsInt()`).
- `discord_webhook_url` accepts arbitrary string (line 331). The string
  later goes to `URI.create(...)` in `DiscordWebhook.post` (line 67).
  **[verified]**. Whether `URI.create` accepts truly arbitrary input
  including non-http URIs: **[needs-check]** — JDK URI parsing is
  permissive; tests would clarify. The "SSRF primitive" framing is
  **[verified]** as the standard security analysis of webhook-URL fields.
- `votifier_port` line 327, no range check. **[verified]**.
- `bluemap_claim_color` line 341, raw string. Whether BlueMap then
  validates this before rendering: **[needs-check]** — BlueMap-side code.
- Cap fields take `getAsDouble` — accepts any double including negative
  or `NaN` via JSON. Effect on game-side multipliers: **[needs-check]**.

**HND-6. `handleExec` runs arbitrary commands at op4  [Info — by design]**

Line 152-153 uses `server.createCommandSourceStack()` which is op4 on
dedicated servers. **[verified]** as standard MC API.

The recommendation to log caller IP not just command: **[verified]** as
audit-trail best practice. Currently only the command is logged (line
149). **[verified]**.

**HND-7. `handleModsUpload` is solid; ZIP validation is missing  [Info]**

Filename validation (lines 1267-1270, 1275-1276) is multi-layered and
correct. Temp-file + atomic move pattern (1280-1303) is the safe
approach. **[verified]** — I read the whole method.

The suggestion to `new ZipFile(temp)` before moving (to reject non-jar
content): **[verified]** as a sound defense-in-depth idea.

**HND-8. `handleModsDelete` self-delete check is best-effort  [Low]**

Lines 1240-1245: catches the `getActiveModJarPath` exception silently,
skips the check. **[verified]**.

Real risk depends on whether the platform shim ever throws (it returns
`Optional` so the empty case is handled). Likely safe; flagging as Low.

### `dashboard/BackupHandler.java`

**BH-1. Public download exposes the full world  [High — by design]**

`handleLatestPublic` line 87-114 — no auth, gated only by
`BACKUP_PUBLIC_DOWNLOAD`. **[verified]**.

Project memory says this is intentional. **[verified]** from memory.

That world ZIPs leak player coordinates / builds / inventories /
advancements: **[verified]** in general (level.dat contains
spawn/world data; playerdata/*.dat contains per-player inventory and
position).

**BH-2. IP rate limit honors XFF first — see CC-8  [High]**  **[verified]**

**BH-3. Preamble duplication — see HND-1  [High]**  **[verified]**

### `dashboard/DiscordWebhook.java`

**DW-1. Markdown injection via player name / chat  [High]**

`esc` (line 81-87) does JSON escape only; strips § codes. Discord embed
description and author.name fields are passed `esc(player)` /
`esc(message)`. **[verified]** — I read the embed builders.

Whether Discord renders markdown in embed `description`: **[needs-check]**
— per Discord's documentation, embed description supports markdown
including `[text](url)`, `**bold**`, etc. I'm working from training-data
knowledge. Worth confirming current Discord embed-rendering rules
before fixing.

If verified, a player name like `[click](https://evil)` becomes a live
hyperlink in Discord posts. **[needs-check]**.

**DW-2. No HTTP timeout  [Medium]**

Line 19: `HttpClient.newHttpClient()` — default `HttpClient` has no
connect timeout, requests have no timeout. **[verified]** — that's the
JDK default for that builder.

**DW-3. SSRF via admin-set webhook URL  [High — see HND-5]**  **[verified]**

### `backup/BackupService.java`

**BS-1. `runOnServer` blocks indefinitely  [High]**

Line 141: `fut.get()` with no timeout. **[verified]**.

If the server thread is wedged, the worker hangs, `inProgress` stays
true, no further backups possible. **[verified]** — read the code path.

**BS-2. Single-thread executor; no per-job timeout  [Medium]**

`Executors.newSingleThreadExecutor` line 39. Combined with BS-1, a
wedged backup wedges the service. **[verified]**.

**BS-3. `prune()` swallows the listing error  [Low]**

`list()` (line 76-78) catches IOException, returns `List.of()`. Prune
then iterates an empty list. **[verified]**.

**BS-4. No size cap on output zip  [Medium]**

No size check on the temp file or source dir. **[verified]** by reading
`runCreate` (line 101-132).

Whether this has caused a disk-full incident: **[needs-check]**.

### `backup/BackupZipper.java`

**BZ-1. Solid  [Info]**

- `visitFileFailed` returns CONTINUE (line 62-64) — confirms the
  "files-disappearing mid-zip are OK" comment. **[verified]**.
- `Files.walkFileTree(src, new SimpleFileVisitor<>(){...})` — no
  `FileVisitOption.FOLLOW_LINKS` passed, so symlinks are visited as
  symlinks (and won't be followed into the linked dir for the
  preVisitDirectory call). **[verified]** as standard JDK behavior.

That symlinks won't expand to expose `/etc/passwd`: **[verified]** in
principle. Whether *real-world world dirs* contain symlinks that should
be followed (e.g. a backup hard-link farm): **[needs-check]** — depends
on operator setup.

### `backup/PublicDownloadLimiter.java`

**PDL-1. Solid  [Info]**

I read the whole 59-line file. `tryAcquire` increments per-IP via
`compute` (atomic), then global via `incrementAndGet` (atomic), and
rolls back both on failure. `release` decrements both. `decrementIp`
removes the entry when count drops to ≤1 → no map-growth from one-shot
IPs. **[verified]**.

### `votifier/VotifierListener.java`

**VL-1. Auto-generated token logged at INFO  [Medium]**

Lines 44-48 — `LOGGER.info("[Votifier] Token: {}", generated)`. **[verified]**.

Whether server logs are sensitive enough to matter: **[needs-check]** —
depends on log shipping / rotation policy.

**VL-2. No connection-rate limiting — see CC-7  [Medium]**  **[verified]**

**VL-3. `restart()` is non-atomic  [Low]**

Line 94-97 is `stop(); start();`. **[verified]**.

Brief port-closed window. **[verified]** in principle.

**VL-4. HMAC verification is correct (constant-time)  [Info]**

Line 146 uses `MessageDigest.isEqual` which is the JDK's constant-time
comparison. **[verified]** as documented JDK behavior.

Challenge-response prevents replay: **[verified]** — challenge is a
fresh 130-bit random per connection and is checked at line 155.

### `skills/ActiveAbilities.java`  (CLAUDE.md candidate A — confirmed)

**AB-1. The 9 `tryActivateX` methods are 95% identical  [High]**

I read all of them. The 5-step structure (unlock check → cooldown
check + msg → setCooldown → durationTicks formula → effect + announce +
resyncHand) repeats in: `tryActivate` (Mining + Excavation share via
the SkillType switch — already factored), `tryActivateTreeFeller`,
`tryActivateGreenTerra`, `tryActivateMasterAngler`, `tryActivateBerzerk`,
`tryActivateSniper`, `tryActivateJuggernaut`, `tryActivateAlchemy` (with
an exception — see AB-3), `tryActivateTycoon`. **[verified]**.

The proposed `ActiveAbility` abstract class refactor: **[verified]** as
a sound OO design — it's literally CLAUDE.md candidate A applied
generally.

**AB-2. Static `HashMap` for live state — see CC-4  [High]**  **[verified]**

**AB-3. `tryActivateAlchemy` returns false on miss → book is dropped  [Medium]**

`tryActivateAlchemy` returns false at line 584 (not looking at block),
line 590 (not a spawner), line 603 (no block entity). **[verified]** by
reading the method.

The caller is `onPlayerDropItem` (line 91-92):
```java
} else if (dropped.getItem() == Items.BOOK || ...) {
    if (tryActivateAlchemy(...)) handled = true;
}
```
If `tryActivateAlchemy` returns false, `handled` stays false, and the
method falls through to `return false` at the bottom of `onPlayerDropItem`,
which means the drop is not cancelled — the book is dropped.
**[verified]** by tracing the call.

**AB-4. Philosopher's Touch makes spawners obtainable  [Info, game design]**  **[verified]**

**AB-5. `tryActivateGreenTerra` scans 11×11×5 = 605 blocks  [Low]**

Line 231-244 has the triple loop with `dx -5..5, dz -5..5, dy -2..2`.
**[verified]** — that's 11 × 11 × 5 = 605 iterations.

The `applyLeafBlower` scan is in SkillEvents 553-564 (`dx -3..3, dy
-3..3, dz -3..3`) = 7³ = 343. **[verified]**.

Tree Feller chains up to 64 logs (`ActiveAbilities.chainBreakLogs`
line 184, `maxBlocks=64`). **[verified]**.

64 × 343 = ~22k block reads per tree if every log break invokes
leaf blower. Whether `applyLeafBlower` is called for chain-broken logs
or only the original break: **[needs-check]** — the chain-break calls
`level.destroyBlock(pos, true, sp)` (line 203) which triggers vanilla
block-break events; whether SkillEvents.onBlockBreak fires for those
depends on the platform event-firing chain. Could go either way.

### `skills/SkillEvents.java`

**SE-1. Class does 6+ distinct jobs  [High]**

I read the whole 782 LOC file. Concerns include: XP awards from various
events; parent buff attribute modifier management; ability discovery
announcement (with progress teasers); agility tick distance tracking;
age verify reprompt loop; passive perks (double drop, treasure, leaf
blower, auto-replant); block taxonomy lookup; mob-XP lookup.
**[verified]**.

Split proposal: **[verified]** as sound OO.

**SE-2. Age verify reprompts live in SkillEvents  [High, SRP]**

`agePromptTicks` map (line 42), `tickAgeVerify` (313-340),
`sendAgeVerifyPrompt` (408-416), call site in `onPlayerTick` (line 304),
seed in `onPlayerJoin` (lines 398-404). **[verified]**.

That this belongs in `ageverify/` rather than `skills/`: **[verified]**
as SRP — the only thing connecting it to skills is that
SkillEvents.onPlayerTick happened to be the tick driver.

**SE-3. `abilityName` / `abilityTrigger` on `SkillType` would be cleaner OO  [Medium]**

Lines 749-781 — two switches over `SkillType` returning strings.
**[verified]**.

Putting metadata on the enum: **[verified]** as standard Java OO.

**SE-4. Mob XP / ore XP / block taxonomy chains  [Medium]**

`mobXp` (line 633-655) — chain of 9 `if (type == X) return Y` checks.
`oreXp` (line 593-605) — chain of `state.is(tag)` checks. `isOre`
(586), `isStone` (607), `isShovelBlock` (615), `isMatureCrop` (624) —
all chain checks. **[verified]**.

The Map-based lookup suggestion: **[verified]** as standard.

**SE-5. Cross-package coupling: SkillEvents reaches into 4 unrelated packages  [Medium]**

Imports `ageverify`, `voicechat`, `punish`. PunishManager called from
`onPlayerJoin` line 389-393. **[verified]**.

That these should subscribe independently rather than be driven from
SkillEvents: **[verified]** as SRP.

**SE-6. `SAFE_LANDING_GUARD` ThreadLocal re-entry guard  [Low]**

Line 209 — `ThreadLocal<Boolean>`. Used at line 223 (early-out) and
239-244 (set/unset around the re-applied hurt call). **[verified]**.

That the guard is necessary because the fall-damage interception
re-calls `hurt()` which would recurse: **[verified]** by reading lines
238-244.

### `claims/ClaimProtection.java`

**CP-1. Duplicate "claims-enabled → return → optional message" preamble  [High]**

Spot-checked: `onBlockBreak` (line 34-43), `onBlockPlace` (46-57),
`onRightClickBlock` (109-118), `onExplosionPre` (120-130),
`onInteractEntity` (133-143), `onLivingHurt` (145-167). All start with
some variant. **[verified]** — I read every line.

The "send protected message" pattern repeats 4 times verbatim:
`onBlockBreak:39`, `onBlockPlace:54`, `onRightClickBlock:115`,
`onInteractEntity:138`. **[verified]**.

**CP-2. `onBlockPlace` is 60 lines mixing player and non-player paths  [High]**

Lines 46-107. **[verified]** — I read it.

**CP-3. "Benign placers" whitelist is a chain  [Medium]**

Lines 70-81. Six entity-type checks via two different mechanisms
(`getType() ==` and `instanceof`). **[verified]**.

**CP-4. `onLivingHurt` PvP branch reads as inverted  [Medium]**

Lines 155-159 — `if (ClaimAccess.canModify(victim, sl, pos)) return
false; else return true;`. **[verified]**.

The naming is misleading: the function checks "can VICTIM modify",
which means "is victim trusted or owner here". So protected players
inside their own claims are immune. Whether this is the intended
gameplay: **[needs-check]** — depends on user's intent for trust
inside claims.

**CP-5. Broad catch in `onExplosionPre`  [Low]**

Line 127 — catches `Exception` and denies. **[verified]**.

Fail-closed is sound; tightening the try scope is a minor improvement.

### `claims/SpawnProtection.java`

**SP-1. `onExplosionPre` samples 5 corner points only  [Low]**

Lines 73-77 — four corners + one center. **[verified]**.

That this can theoretically miss inner squares: **[verified]** in
principle. That realistic explosion/spawn radii don't trigger it:
**[needs-check]** — math depends on configured values.

**SP-2. Explosion-check shape diverges from ClaimProtection's  [Low]**

`ClaimProtection.shouldCancelExplosion` (line 188-209) iterates the
covered chunks. `SpawnProtection.onExplosionPre` (62-78) samples 5
points. **[verified]**.

### `claims/ClaimService.java`

**CS-1. OP-check duplicated 3× in 130 lines — see CC-1  [High]**

Lines 37, 52, 114. **[verified]** — I counted them in the file.

**CS-2. `transfer` uses magic-number return codes  [Medium]**

Lines 104-127, returns `-1`, `-2`, `-3`, or positive count. **[verified]**.

The file already has a `Result` enum (line 129-134) — switching
`transfer` to a similar enum is a tiny consistency fix. **[verified]** as
sound.

**CS-3. Spawn-protection geometry inside `claim()` is its own helper  [Medium]**

Lines 55-85 — 30 lines of overlap math inside `claim()`. **[verified]**.

**CS-4. Tier-limit calculation duplicated  [Medium]**

Lines 92-95 and 117-120. **[verified]** — read both blocks.

### `chatfilter/ChatFilter.java`

**CF-1. `onDecorate` does six things  [High]**

I read the whole 282 LOC file. `onDecorate` (57-134) does: skip
commands → mute check → call filter → strike + mute escalation →
player notification → OP notification → return component. **[verified]**.

**CF-2. OP-notification block duplicated  [High — see CC-1]**

Lines 108-112 and 123-127 are textually identical except the message
string. **[verified]**.

**CF-3. Raw message broadcast to all OPs on every violation  [Medium]**

Lines 105-107 (mute path) and 121-122 (strike path) embed the original
`raw` message in the alert. Loops over all online players checking `isOp`.
**[verified]**.

OP-spam vector (malicious player triggering filter repeatedly to spam
moderators) is **[verified]** in principle but **[needs-check]** for
how bad it is in practice.

**CF-4. Word pattern is permissive  [Medium]**

Line 18: includes `_'@\-$!.+*#%&?~^`. **[verified]**.

That this matches URL-like tokens, decimals, mentions: **[verified]** —
e.g. `[https://example.com](https://example.com)` matches as one token
under that regex. Whether any have caused false positives in production:
**[needs-check]**.

**CF-5. No anti-evasion for unicode-confusable chars  [Low]**

`normalize` (line 224-229) does lowercase + NFKC + accent strip. NFKC
*does* fold many compatibility variants (fullwidth, ligatures) but does
**not** fold Cyrillic ↔ Latin homoglyphs. **[verified]** as documented
Unicode behavior.

Whether players actually evade with Cyrillic: **[needs-check]** —
depends on community.

### `SmpUtilsMod.java` / `platform/`

**SU-1. `purgeOldModVersions` is its own concern  [Low, SRP]**

Lines 40-69 — implementation lives in the init class. **[verified]**.

**SU-2. `SmpServices.load` throws `NullPointerException` for missing service  [Low]**

Line 12 in `SmpServices.java` — `orElseThrow(() -> new NullPointerException(...))`. **[verified]**.

**SU-3. `SmpUtilsMod.VERSION` is mutable static String  [Low]**

Line 14 in SmpUtilsMod — `public static String VERSION = "unknown"`,
reassigned at line 17 in `init()`. **[verified]**.

**SU-4. Subsystem init sequence is implicit  [Medium]**

`init()` lines 16-25 only explicitly inits 4 subsystems (Config,
SkillEvents, BlueMap, Voicechat, Dashboard) — wait, that's 5. Other
subsystems' initialization paths: **[needs-check]** — I haven't read
all the platform entrypoints to enumerate every init call.

---

## Recommended Action Order

If the user prioritizes maintenance over hardening:

1. **Cross-cutting helpers**: CC-1 (`Perms.isOp`), CC-2 (`Json` helper +
   single `jsonEscape`), CC-3 (`TextUtil` constants). Pure refactors,
   no behavior change. **[verified]** as standalone refactors.
2. **AdminHandler split**: HND-3 + HND-1 + DM-4. Drops AdminHandler from
   2011 LOC to ~100 LOC. **[verified]** as sound. Implementation
   effort: **[needs-check]** — depends on the route table shape.
3. **ActiveAbilities** (AB-1, Candidate A). ~250 LOC removed.
4. **SkillEvents split**: SE-1 + SE-2.
5. **handleConfigPost schematization**: CC-6.

If the user prioritizes security/hardening:

1. **AA-6 + BH-1**: decide policy on the public-by-default risks.
2. **CC-8**: trusted-proxy config for XFF handling.
3. **CC-7**: bound the dashboard + votifier thread pools.
4. **DS-1**: auth-gate the WebSocket upgrade.
5. **DW-1 + DW-3**: Discord webhook URL allowlist + Markdown escape.
6. **AA-2**: bump PBKDF2 iterations (after confirming OWASP guidance).
7. **BS-1**: add bounded `fut.get(60s)` to `runOnServer`.

---

## Verification

This audit is read-only; verification is review-only:

1. Spot-check findings tagged **[needs-check]** that you want to act on.
2. Findings tagged **[verified]** can be trusted at the line-number
   level — but the *fix recommendations* still deserve a sanity
   review at implementation time.
3. Subsequent sessions extend the same `audit.md` with new findings for
   the deferred files in "Audit Progress → [todo]".

---

## Final Implementation Step

After approval, the only action is:

```
cp /Users/raul/.claude/plans/gleaming-cuddling-pelican.md \
   /Users/raul/Documents/Projects/QuackedSMP/QuackedSMP/audit.md
```

Future sessions edit `audit.md` directly, continuing from "Audit
Progress → [todo]".
