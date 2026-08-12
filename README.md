[![Release](https://img.shields.io/github/v/release/Rvhoyos/QuackedSMP)](https://github.com/Rvhoyos/QuackedSMP/releases)
![GitHub Downloads](https://img.shields.io/github/downloads/Rvhoyos/QuackedSMP/total)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1360431?label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/mc-mods/quackedsmp)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/quackedsmp?label=Modrinth%20downloads)](https://modrinth.com/mod/quackedsmp)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-red)](LICENSE)

# QuackedSMP

Server-side mod for **Minecraft 26.1.2** (Fabric + NeoForge). Claims, skills, anti-grief, custom dims, shops, hardcore, and more. Optional browser admin panel for config, backups, players, and commands.

## Quick Start

1. Put the Fabric or NeoForge JAR in `mods/` and start the server.
2. As OP (password at least 8 characters):
   ```
   /smp admin setpassword <your-password>
   ```
3. Open in a browser:
   - Same PC as the server: `http://localhost:8125` (or `http://127.0.0.1:8125`)
   - Other device on your network: `http://<server-ip>:8125`

Panel is optional. Game features work without it. Panel stays off until you set a password.

**Security (short):**

- Admin needs the password. Public metrics / chat feed / leaderboard do not.
- Panel is plain HTTP on port **8125**. Fine on localhost or home LAN. Do not open 8125 to the whole internet without HTTPS (Caddy/Nginx reverse proxy) or a private tunnel (SSH / VPN).
- Never set `dashboard.enabled=true` in config without a password unless you want open admin.

## Features

- Land claims + anti-grief
- RPG skills and abilities
- Custom dimensions and portal frames
- Chat filter and auto-mutes
- VIP tiers and kits (`/vip set`)
- Anti-xray (on by default)
- Wilderness regen, player shops, hardcore sessions, world backups
- Optional: Discord, BlueMap, Votifier, Spark, Simple Voice Chat

Most systems can be toggled in the panel Config tab or `config/quackedsmp.json`.

---

## Admin Panel

| Tab | What you can do |
| :--- | :--- |
| **Players** | Online players, dimension, OP level. Grant/revoke OP, set VIP tier. |
| **Commands** | Run server commands from the browser. Quick actions (weather, time, broadcast, end reset, wilderness regen, stop). |
| **Dimensions** | Create, delete, and configure custom dimensions. Wire portal frame blocks. |
| **Skills** | Browse skill levels. Set level / adjust XP per skill. |
| **Claims** | View claim counts. Force-unclaim all chunks for a player. |
| **Chat Filter** | Blocked words and whitelist. Active mutes, unmute. |
| **Hardcore** | Active sessions, force-end a session. |
| **Teams** | Scoreboard teams, members, dimension auto-assign. |
| **Shops** | List player shops, delete shops, view bank balances, toggle economy. |
| **Cmd Blocks** | List, edit, and delete command blocks in loaded chunks (requires command blocks enabled on the server). |
| **Mods** | Upload, replace, or remove JARs in `mods/`. |
| **Backups** | Create / list / download / delete world snapshots. Toggle public download and panel URL messaging. |
| **Config** | Edit configurable values live. Most settings need no restart. |
| **Features** | In-panel feature overview. |

### Public dashboard

Anyone who can reach the port (no login):

- **Metrics:** RAM, disk, uptime; TPS / CPU / MSPT if Spark is installed
- **Player count**
- **Event feed:** join / leave and chat over WebSocket
- **Skills leaderboard**
- **World download** (opt-in only): when `backup_public_download` is true, a download button streams the latest snapshot

<details>
<summary><strong>Security details</strong> (auth, rate limits, public API)</summary>

Rules of thumb are in **Quick Start** above. This section is the technical breakdown.

**Auth**

- Passwords hashed with PBKDF2-SHA256 (100,000 iterations)
- Constant-time comparison on verify
- 128-bit random session tokens, 24-hour TTL
- Admin API uses a Bearer token in the `Authorization` header (no cookies)

**Brute-force protection**

- 5 failed logins within 15 minutes lock that IP out for 5 minutes (HTTP 429)
- Counter clears on successful login

**Public download rate limiting**

- `/api/backups/latest/download`: per-IP concurrent cap (`backup_public_max_per_ip`, default 2) and global concurrent cap (`backup_public_max_concurrent`, default 0 = follow `max-players` from `server.properties`)
- Over cap returns HTTP 429

**Path traversal**

- Static files reject `..` and serve only from the embedded classpath

**Mod upload**

- Filename must end in `.jar`, no path separators, no `..`
- 100 MB size cap
- Written to a temp file, then moved into `mods/`

**Public endpoints (no admin auth)**

- `/api/health` - player count, server name, public flags
- `/api/metrics` - heap, RAM, disk, uptime, threads
- `/api/spark/*` - TPS, CPU, MSPT (requires Spark)
- `/api/skills/leaderboard` - skill rankings
- `/api/backups/latest/download` - latest world snapshot only when `backup_public_download` is enabled
- `/api/admin/status` - whether admin is enabled and whether a password is set (no secrets)
- WebSocket - join / leave and chat events

</details>

---

<details>
<summary><strong>Features</strong></summary>

Most systems are toggleable from the admin **Config** tab or `config/quackedsmp.json`. The dashboard itself defaults **off** until `/smp admin setpassword`. Command-block tools follow the server's `enable-command-blocks` setting.

### RPG Skills

12 skills in 4 categories. XP from normal play, passive category buffs, active abilities.

| Category | Skills | XP sources (examples) |
| :--- | :--- | :--- |
| **Industrial** | Mining, Excavation, Woodcutting | Ores, digging, logs |
| **Nature** | Farming, Fishing, Agility | Crops, fish, sprint / swim |
| **Combat** | Melee, Archery, Defense | Damage dealt, bow kills, damage taken |
| **Knowledge** | Enchanting, Alchemy, Trading | Enchanting, brewing, villager trades |

**Leveling**

- Levels 1-10: flat 50 XP per level
- Levels 11-100: `floor(100 * level ^ xp_exponent)` (default exponent `1.5`)

**Parent buffs** (from average category level, scales to caps at high levels):

- Industrial → movement speed (cap +50%)
- Nature → max health (cap +10 hearts)
- Combat → attack damage (cap +100%)
- Knowledge → XP orb gain (cap +100%)

**Active abilities**

Default unlock levels: most skills **10**. Exceptions: **Agility 3**, **Defense 5**, **Scout Zoom (Archery) 5**, **Trading 1**. All configurable.

Buff durations (unless noted): `(10 + level × 0.2)` seconds (12s at level 10, 30s at level 100). Tycoon's Charm: `(30 + level × 0.5)` s. Scout Zoom: `(10 + level × 0.25)` s; Night Vision II at Archery 67+.

Cooldowns use a configured base (seconds), then scale with skill level (about 75% of base at level 10, 25% of base at level 100).

| Skill | Ability | Effect | Cooldown (approx. Lv 10 → Lv 100) |
| :--- | :--- | :--- | :--- |
| Mining | Super Breaker | Haste V | 3m → 1m |
| Excavation | Giga Drill | Haste V | 3m 45s → 1m 15s |
| Woodcutting | Tree Feller | Chain-breaks logs (max 64) | 3m 45s → 1m 15s |
| Farming | Green Terra | Bonemeals crops in 5-block radius | 2m 15s → 45s |
| Fishing | Master Angler | Luck V (duration scales with level) | 3m 45s → 1m 15s |
| Melee | Berzerk | Strength II + Speed II | 3m 45s → 1m 15s |
| Archery | Sniper | Slow Falling + Night Vision | 2m 15s → 45s |
| Archery | Scout Zoom | Spyglass zoom, mob glow in cone, NV / slow falling | ~22s → ~7s |
| Defense | Juggernaut | Resistance IV + Slowness IV | 7m 30s → 2m 30s |
| Enchanting | Arcane Infusion | Repair held item by 10% durability | 15m → 5m |
| Alchemy | Philosopher's Touch | Silk Touch a Spawner into inventory | 7m 30s → 2m 30s |
| Trading | Tycoon's Charm | Hero of the Village (amplifier scales) | 15m → 5m |
| Agility | Dash | Velocity boost in look direction | ~7s → ~2s |

**Triggers**

- **Tool / item abilities:** hold the matching item + Sneak + Drop (Q). Drop is cancelled and the item is returned.
  - Pickaxe, shovel, axe, hoe, rod, sword, bow/crossbow, shield as listed above
  - Alchemy: Book or Enchanted Book, looking at a Spawner
  - Trading: Emerald
  - Arcane Infusion: any damaged damageable item (can fire with another ability on the same drop)
- **Dash:** sprint + sneak while not on the ground (typically sprint-jump then sneak)
- **Scout Zoom:** Sneak + F (swap offhand) with both hands empty; F again to cancel early

**Passive perks (examples)**

- Double drops (Industrial parent level)
- Bleed on melee, arrow recovery on projectile kills
- Treasure Hunter (excavation), Leaf Blower (woodcutting), Auto Replant (farming)
- Defense armor bonus, Agility Safe Landing (fall damage absorb scales with level)

### Land Claiming

Chunk protection: untrusted players cannot build, break, or interact inside your claims.

**Anti-grief (toggleable)**

- Explosions blocked when they would affect claims (`protect_explosions`, default true)
- Fire protection inside claims (`protect_fire_claims`, default true)
- Wilderness fire and lava placement gated (`allow_fire_wilderness` / `allow_lava_wilderness`, default **false**)
- Water does not flow into another owner's claim from outside
- Farmland trampling blocked in claims and spawn protection (`protect_farmland`)
- Endermen cannot grief claims / spawn protection (`protect_enderman`)
- Armor stands, item frames, paintings, villagers protected from damage / theft in claims
- PvP immunity inside own claims; separate spawn PvP block via `spawn_no_pvp`

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/claim` | Claim current chunk (or brush grid) | Everyone |
| `/claim size <1-7>` | Claim brush size (odd sizes; resets on restart) | Everyone |
| `/unclaim` | Unclaim current chunk | Everyone |
| `/claim info` | Owned count, limit, remaining, current chunk | Everyone |
| `/claim map` | Nearby claim map in chat | Everyone |
| `/claim name <name>` | Name claim (BlueMap) | VIP / OP |
| `/claim transfer <player>` | Transfer all claims | Everyone |
| `/claim trust <player>` | Trust player in all your claims | Everyone |
| `/claim untrust <player>` | Revoke trust | Everyone |
| `/claim trustlist` | List trusted players | Everyone |
| `/sos` | Eject untrusted players from your claims | Everyone |

`/trust`, `/untrust`, and `/trustlist` also work as root commands.

### Teleportation

Warmup-based (default 5 seconds). Movement cancels the teleport.

- `/home` - bed / respawn point, else world spawn
- `/spawn` - world spawn
- `/tpr <player>` - request teleport to a player
- `/tpa accept` / `/tpa yes` / `/tpa deny` / `/tpa no` - respond to a request

### Chat Management

- Keyword filter with leet / separator style evasion checks
- Whitelist for false positives
- Progressive auto-mutes (`mute_levels_minutes`, default 60 / 120 / 240 / 480 / 1440)
- Periodic tip broadcasts (`message_interval`, default 300s)
- `/mute <player> <minutes>` and `/unmute <player>` (OP)
- Manage lists and mutes from the **Chat Filter** admin tab

### Custom Dimensions (`/dim`)

Runtime dimensions without datapacks.

> [!IMPORTANT]
> **Restart required** after create for full block interaction (break/place). Until restart the new dim is effectively read-only for players.

| Type | Terrain |
| :--- | :--- |
| `overworld` | Standard overworld noise |
| `ether` | Floating islands; void fall returns; shared central spawn island for portals |
| `nether` | Vanilla nether |
| `end` | Vanilla end |

Optional: weighted biomes; flat layers (`overworld` only); ether threshold / radii / spacing. Examples:

```
/dim create quacksmp:flatworld overworld flat minecraft:bedrock:1 minecraft:dirt:2 minecraft:grass_block:1
/dim create quacksmp:sky_jungle ether -0.7 10 80 20 biomes minecraft:jungle
```

**Portals:** new custom dims default to a **glowstone** frame block (`/dim setportal` to change). Build a nether-portal-shaped frame (2-21 wide, 3-21 tall) in a vanilla dimension, activate with a **water bucket**. Travel is bidirectional; return portals are placed on first entry.

| Command | Permission |
| :--- | :--- |
| `/dim create <id> <type> [params]` | OP |
| `/dim delete <id>` | OP |
| `/dim list` | Everyone |
| `/dim tp <id>` / `/dim tp <player> <id>` | OP |
| `/dim setportal <dim_id> <block_id>` | OP |

### Discord

Set a webhook URL in Config to mirror join/leave and/or chat (toggles `discord` join/leave and chat flags).

### BlueMap

Optional [BlueMap](https://modrinth.com/plugin/bluemap) (compile API **2.7.3**). When present and `bluemap_enable` is true: claim outlines (normal / VIP / OP colors), home markers, world border, optional spawn-protection outline.

### Hardcore Mode

Off by default (`hardcore_enabled`). Password sessions under `/smp hardcore`.

- Create picks a random start at least **1000** blocks from world center, inside the world border
- Join stashes inventory / XP / effects and teleports to the session start with a clean survival loadout
- **Leave** restores normal life and **suspends** hardcore state; **join again with the password** resumes where you left off
- Deaths tracked; when deaths reach `hardcore_death_percent` of peak players (default 50%), session ends for everyone
- Dead players spectate until leave or session end
- Optional withered hardcore hearts HUD for session members (`hardcore_withered_hearts`, default true; applies on connect)
- Entering the End as an alive member ensures a dragon if none is present; killing the dragon can **win** the session for alive members in the End

| Command | Description |
| :--- | :--- |
| `/smp hardcore create <name> <password>` | Create and auto-join |
| `/smp hardcore join <name> <password>` | Join or resume |
| `/smp hardcore leave` | Leave (always allowed even if feature toggled off mid-session) |
| `/smp hardcore status [name]` | Status |
| `/smp hardcore list` | Active sessions |

### Kits

Daily kit claim (`kits_enabled`, default true). Global cooldown default **86400** seconds. Tier-gated kits editable from the panel. Default kits: `starter` (tier 0), `vip` (tier 1).

| Command | Description |
| :--- | :--- |
| `/smp kit` / `/smp kit list` | List kits and cooldown |
| `/smp kit <name>` | Claim a kit |

### Simple Voice Chat

Optional age-gate (compile API **2.6.0**). When `voicechat_enable` is true and SVC is installed: unverified players cannot use voice until `/smp verify confirm` (or `/verify confirm`). Deny with `deny`.

### End Reset

- `/smp end reset dragon` - respawn dragon fight without wiping the dimension
- `/smp end reset world` - queue full End reset on next restart

### Wilderness Regen

`/smp regen` (OP): queue wipe/regen of **unclaimed** chunks (3-chunk buffer around claims kept) on next shutdown. Confirm / cancel / status subcommands. Also available from the dashboard Commands tab.

### Spawn PvP Protection

`spawn_no_pvp` (default true): cancel PvP if attacker or victim is inside vanilla spawn protection radius.

### Anti-XRay

`antixray_enabled` (default true): server-side obfuscation of fully hidden ores and stone in chunk data. Hidden blocks are sent as plain stone / deepslate / netherrack (depth-aware), so x-ray clients see no ore locations and leak nothing. Real blocks reveal as you break into them and via proximity as you move, so any reveal reads as a natural stone-to-ore transition rather than a flicker.

### Player Shops

Off by default (`shops_enabled`). Look at a chest you may use, `/shop create <price> [currency] [unit]`. Stock is the chest inventory. Spawn-protection chests are admin/OP spawn shops (unlimited stock rules).

**Economy bank** (`economy_enabled`, default false): virtual emeralds for deposit / withdraw / transfer / balance. Shop purchases still use **physical** items only.

> [!WARNING]
> The economy bank is a **beta** feature. Enable at your own risk.

| Command | Notes |
| :--- | :--- |
| `/shop create <price> [currency] [unit]` | Create on looked-at chest |
| `/shop delete` / `info` / `list` | Manage / inspect |
| `/shop deposit` / `withdraw` / `transfer` / `balance` | Bank (if economy on) |

### Command Blocks Dashboard

Scan loaded chunks for command blocks; edit or delete from the panel when the server allows command blocks.

### Vote Rewards (Votifier)

Optional NuVotifier v2 TCP listener (`votifier.enabled` default false, port **8192**). Shared `votifier.token`. Tier-stacked reward commands; offline queue.

### World Backups

Admin panel: create / list / download / delete zips under `backup_dir` (default `backups`), retention `backup_max_count` (default 5). Optional public download and `panel_url` + periodic / `/smp download` messaging when public download is on and a URL is set.

</details>

---

<details>
<summary><strong>Full Command Reference</strong></summary>

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/smp help` | Short command tips | Everyone |
| `/smp reload` | Reload config from disk | OP |
| `/smp bluemap` | Force-refresh BlueMap markers | OP |
| `/smp config` | In-game config GUI | OP |
| `/smp config reset` | Factory reset config | OP |
| `/smp admin setpassword <password>` | Set panel password and enable dashboard (min 8 chars) | OP |
| `/smp download` | Send panel / world download link (only if public download + `panel_url` set) | Everyone |
| `/smp end reset dragon` | Reset Ender Dragon fight | OP |
| `/smp end reset world` | Queue End dimension reset | OP |
| `/smp regen` | Wilderness regen warning + click confirm | OP |
| `/smp regen confirm` | Queue wilderness regen | OP |
| `/smp regen cancel` | Cancel pending regen | OP |
| `/smp regen status` | Pending regen? | OP |
| `/smp keepinv` / `on` / `off` | Keep inventory preference (also `/keepinv`) | Everyone |
| `/smp verify confirm` / `deny` | Voice chat age gate (also `/verify`) | Everyone |
| `/smp hardcore create\|join\|leave\|status\|list` | Hardcore sessions | Everyone (feature flag) |
| `/smp kit` / `list` / `<name>` | Daily kits | Everyone (feature flag) |
| `/mute <player> <mins>` | Mute | OP |
| `/unmute <player>` | Unmute | OP |
| `/rules` | Server rules | Everyone |
| `/claim` … / `/unclaim` / `/sos` | Claims (see Features) | Everyone / VIP for name |
| `/trust` / `/untrust` / `/trustlist` | Trust list (also under `/claim`) | Everyone |
| `/home` | Bed / respawn / spawn fallback | Everyone |
| `/spawn` | World spawn | Everyone |
| `/tpr <player>` | Teleport request | Everyone |
| `/tpa accept\|yes\|deny\|no` | Answer teleport request | Everyone |
| `/vip set <player> <tier>` | Assign tier | OP |
| `/vip remove <player>` | Clear assigned tier | OP |
| `/vip list` | List assigned tiers | OP |
| `/vip info <player>` | Assigned / earned / effective tier | OP |
| `/punish <player>` | Wipe inventory and claim items | OP |
| `/skills` … | Skills overview, view, top, admin givexp/setlevel | Everyone / OP |
| `/shop` … | Player shops and bank | Everyone (feature flags) |
| `/dim` … | Custom dimensions | OP for mutate / tp |

</details>

---

<details>
<summary><strong>Configuration Reference</strong></summary>

File: `config/quackedsmp.json`. Most values editable from the admin Config tab. Hot-reload: `/smp reload`. Defaults below match the mod's built-in `ConfigData` defaults.

| Key | Default | Description |
| :--- | :--- | :--- |
| `max_claims` | `50` | Max claim chunks for a normal player |
| `tiers` | one VIP tier (1, 100h, +20 claims) | Tier definitions |
| `tp_warmup` | `5` | Teleport standup seconds |
| `message_interval` | `300` | Seconds between tip broadcasts |
| `welcome_message` | `&6Welcome to QuackedSMP, {player}!` | Join message; `{player}` and `{server}` (falls back to `QuackedSMP` if `dashboard.server_name` blank) |
| `allow_lava_wilderness` | `false` | Allow lava placement outside claims |
| `allow_fire_wilderness` | `false` | Allow fire spread / placement rules in wilderness |
| `spawn_no_pvp` | `true` | Block PvP in vanilla spawn protection |
| `protect_explosions` | `true` | Claim explosion protection |
| `protect_fire_claims` | `true` | Fire protection inside claims |
| `protect_enderman` | `true` | Enderman grief block in claims / spawn |
| `protect_farmland` | `true` | Farmland trampling protection |
| `claims_enabled` | `true` | Claiming system |
| `skills_enabled` | `true` | Skills system |
| `chatfilter_enabled` | `true` | Chat filter |
| `voicechat_enable` | `true` | Simple Voice Chat age-gate |
| `mute_levels_minutes` | `[60,120,240,480,1440]` | Auto-mute escalation |
| `dashboard.enabled` | `false` | HTTP dashboard process |
| `dashboard.port` | `8125` | Dashboard port |
| `dashboard.admin_enabled` | `false` | Admin UI enabled (set via setpassword) |
| `dashboard.server_name` | `""` | Name shown in panel header |
| `bluemap_enable` | `true` | BlueMap integration when mod present |
| `bluemap_show_homes` | `true` | Homes on map |
| `bluemap_show_claims` | `true` | Claim outlines |
| `bluemap_claim_color` | `00FFFF` | Normal claim color |
| `bluemap_op_claim_color` | `FFD700` | OP claim color |
| `bluemap_vip_claim_color` | `8A2BE2` | VIP claim color |
| `bluemap_show_worldborder` | `true` | World border outline |
| `bluemap_worldborder_color` | `FF3C3C` | Border color |
| `bluemap_show_spawn_protection` | `true` | Spawn protection outline |
| `bluemap_spawn_protection_color` | `80409040` | Spawn outline color |
| `antixray_enabled` | `true` | Ore obfuscation |
| `shops_enabled` | `false` | `/shop` |
| `economy_enabled` | `false` | Emerald bank (beta) |
| `backup_max_count` | `5` | Snapshot retention |
| `backup_dir` | `"backups"` | Snapshot directory |
| `backup_public_download` | `false` | Public latest-snapshot download |
| `backup_public_max_per_ip` | `2` | Concurrent public downloads per IP |
| `backup_public_max_concurrent` | `0` | Global concurrent public downloads (`0` = max-players) |
| `panel_url` | `""` | Public panel URL for `/smp download` and optional broadcast |
| `panel_message` | see JSON | Chat template; `{url}` substituted |
| `panel_message_enabled` | `false` | Periodic panel link broadcast |
| `panel_message_interval` | `1800` | Seconds between panel link broadcasts (min 60 on save) |
| `votifier.enabled` | `false` | Vote listener |
| `votifier.port` | `8192` | Vote TCP port |
| `votifier.token` | `""` | Shared secret |
| `votifier.broadcast` | see JSON | Vote chat line |
| `kits_enabled` | `true` | Kit system |
| `kits.cooldownSeconds` | `86400` | Global kit cooldown |
| `kits.kits` | starter + vip defaults | Kit definitions |
| `hardcore_enabled` | `false` | Hardcore sessions |
| `hardcore_death_percent` | `50` | Deaths vs peak players to end session |
| `hardcore_withered_hearts` | `true` | Hardcore heart HUD for session members |
| `team_auto_assign` | `{}` | Team name → dimension IDs |
| `skills.xp_exponent` | `1.5` | XP curve exponent |
| `skills.ability_unlock_levels` | see JSON | Per-ability unlock levels |
| `skills.cooldowns` | see JSON | Base ability cooldowns (seconds) |
| `skills.caps` | see JSON | Passive caps at high level |

**Caps (max at level 100, defaults)**

- `industrial_speed` 0.5 → +50% movement speed
- `nature_health` 10.0 → +10 hearts
- `combat_damage` 1.0 → +100% attack damage
- `knowledge_xp` 1.0 → +100% XP orb gain
- `double_drop` 0.5 → max 50% double drop chance
- `defense_armor` 10.0 → max +10 armor points
- `safe_landing` 1.0 → max 100% fall damage absorbed

</details>

---

## License

Copyright (c) 2025 QuackedSMP. All Rights Reserved.

You may use this mod on your Minecraft server. You may not redistribute, repackage, or publish it as your own.
