# Dashboard Admin Panel — Implementation Plan

Gated admin panel tab inside the existing React dashboard. Disabled by default, protected
by a shared token. Can serve as both an operator tool and a live showcase of everything
QuackedSMP offers.

---

## Config

Two new fields under the existing `dashboard` block:

```json
"dashboard": {
  "enabled": true,
  "port": 8125,
  "admin_enabled": false,
  "admin_token": ""
}
```

- `admin_enabled` — gates the entire admin surface. `false` = all `/api/admin/*` routes
  return 403, no admin tab rendered.
- `admin_token` — shared secret. Frontend sends it as `Authorization: Bearer <token>` on
  every admin request. Server validates before executing anything. If blank, admin stays
  disabled even if `admin_enabled: true`.

---

## Backend — New REST Endpoints

All under `/api/admin/`. All require valid `Authorization: Bearer <token>` header.
All run on the Minecraft server thread via `server.execute()`.

| Method | Path | Description |
|---|---|---|
| `GET`  | `/api/admin/players` | Online player list: name, uuid, ping, gamemode, coords (dim only), op status |
| `POST` | `/api/admin/exec` | Run any server command. Body: `{"command":"kick Notch griefing"}` |
| `GET`  | `/api/admin/config` | Read current live `SmpConfig` values as JSON |
| `POST` | `/api/admin/config` | Write + hot-reload specific config fields (same as `/smp reload`) |

`/api/admin/exec` is the power tool — pre-built action buttons on the frontend just POST
pre-filled commands to this endpoint. No separate kick/ban/mute endpoints needed;
vanilla Minecraft commands handle all of it.

---

## Frontend

### Auth Flow
- Admin tab only renders when `admin_enabled: true` (returned in `/api/health`)
- First visit: modal overlay asking for admin token. Entered token stored in
  `sessionStorage` (cleared on tab close, not persisted).
- Wrong token: server returns 403 → "Invalid token" error state, prompt retry.
- Correct token: panel unlocks, token cached for the session.

### Layout — Four Sections (tabbed within the Admin panel)

#### 1. Players
Table of online players. Columns: avatar (Minotar/Crafatar head), name, ping ms, dimension,
op badge. Per-row action buttons: **Kick**, **Mute**, **Ban**, **TP to Spawn** — each opens
a small confirm popover before executing. "No players online" empty state.

#### 2. Commands
Two sub-areas:
- **Quick Actions** — icon-button grid of the most useful commands, grouped:
  - *Server*: Reload Config, Reset End Dragon, Queue End World Reset, Broadcast Message
  - *Skills*: View Leaderboard (read-only, displayed inline)
  - *Claims*: List Claims (player input)
  - *Server Control*: Gamemode toggle, Time set, Weather set
- **Terminal** — a styled `<textarea>`-based command input. Type any command, hit Enter or
  click Run. Output appears below (server response captured via log intercept or echoed by
  a dedicated command response route). Maintains a 50-entry history navigable with ↑/↓.

#### 3. Config Editor
Grouped inputs mirroring `SmpConfig`. On submit, calls `POST /api/admin/config`.
Groups:
- General (max claims, tp warmup, welcome message)
- Skills (xp exponent, cooldowns — simple number inputs)
- Dashboard (port, admin_enabled toggle — saving this live is read-only; requires restart)
- Votifier (enabled toggle, token, broadcast, rewards list editor)
- Discord (webhook url, toggles)
- BlueMap (enable/disable toggles, colors as hex pickers)

#### 4. Showcase (bonus — "everything we've got")
Static/dynamic overview of all QuackedSMP features — acts as a living feature list.
Useful to show new players or potential server partners what's installed.
- Cards per feature: Claims, Skills (list all 12 with icons), Teleport, Chat Filter,
  Custom Dimensions, Votifier, Voice Chat, BlueMap, Dashboard.
- Each card shows: feature name, status pill (active/disabled), key config values.
- Read-only. Pulls from `/api/health` extended + config endpoint.

---

## UI Design Spec

Palette: existing Catppuccin Mocha vars already in `global.css`. No new color system needed.

Typography: keep existing stack. Admin-specific: monospace `var(--font-mono)` for the
terminal and any UUIDs/tokens.

Motion: entrance fade + slight translateY (100ms, `ease-out`). No dramatic scroll
animations — this is a tool, not a marketing page. Action feedback via button loading
spinners (200ms debounce before showing).

Components to build:
- `AdminGate.jsx` — token prompt modal
- `AdminPanel.jsx` — tabbed container
- `PlayerTable.jsx` — player rows + action popovers
- `QuickActions.jsx` — command button grid
- `Terminal.jsx` — command input + output + history
- `ConfigEditor.jsx` — grouped form fields
- `FeatureShowcase.jsx` — status cards grid

---

## Security Notes

- Token is in plaintext in `quackedsmp.json` — warn in docs to keep this file out of git
  (it already is, it lives in the run dir). Token should be rotated if leaked.
- `exec` endpoint runs commands as console OP (level 4). There is no command allowlist —
  this is intentional since the panel is admin-only. Document clearly.
- Rate-limit: reject >10 requests/second from same IP (simple counter in `DashboardServer`).
- No WebSocket auth needed — WS only pushes public events (join/leave/chat/TPS).

---

## Files to Create / Modify

```
common/.../dashboard/AdminHandler.java       — request routing + auth check
common/.../dashboard/DashboardServer.java    — register /api/admin/* routes
common/.../config/SmpConfig.java            — admin_enabled, admin_token fields
common/.../config/ConfigIO.java             — defaultJson + backfill + save

dashboard-ui/src/components/admin/
  AdminGate.jsx
  AdminGate.module.css
  AdminPanel.jsx
  AdminPanel.module.css
  PlayerTable.jsx
  PlayerTable.module.css
  QuickActions.jsx
  QuickActions.module.css
  Terminal.jsx
  Terminal.module.css
  ConfigEditor.jsx
  ConfigEditor.module.css
  FeatureShowcase.jsx
  FeatureShowcase.module.css

dashboard-ui/src/App.jsx                    — add Admin tab to header nav
dashboard-ui/src/App.module.css             — nav tab styles
```

---

## Open Questions (decide before implementing)

1. **Command output**: Does the exec endpoint need to return command output, or just
   "executed" confirmation? Capturing console output is non-trivial in Minecraft.
   Simpler: return `{"ok":true}` and let the WebSocket feed show side-effects (e.g. a
   kicked player's leave event). → **Recommendation: just return ok, watch WS feed.**

2. **Config hot-reload scope**: Which fields should `POST /api/admin/config` allow
   changing live vs. requiring restart? Suggested live-reloadable: all except port numbers.

3. **Broadcast command UI**: Should "Broadcast Message" open a modal with a text field,
   or just route through the Terminal?

4. **Player head avatars**: Use Minotar (`https://minotar.net/avatar/<name>/20`) or
   Crafatar for player faces in the table? Both are external CDN calls.
