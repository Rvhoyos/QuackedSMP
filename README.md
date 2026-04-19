[![Release](https://img.shields.io/github/v/release/Rvhoyos/QuackedSMP)](https://github.com/Rvhoyos/QuackedSMP/releases)
![GitHub Downloads](https://img.shields.io/github/downloads/Rvhoyos/QuackedSMP/total)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1360431?label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/mc-mods/quackedsmp)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/quackedsmp?label=Modrinth%20downloads)](https://modrinth.com/mod/quackedsmp)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-red)](LICENSE)

# QuackedSMP

Server-side mod for **Minecraft 1.21.11** (Fabric + NeoForge) with a built-in browser admin panel. Drop the JAR and manage your server from a browser, no external tools or config editing needed.

The panel is a password-protected SPA served directly by the mod over an embedded HTTP server, with WebSocket for live events, Bearer token auth, and PBKDF2-hashed passwords. Claims, skills, chat filter, custom dimensions, Discord, BlueMap, Votifier: every feature is toggleable from the panel.

---

## Quick Start

1. Drop the JAR into your server's `mods/` folder and start the server.
2. In-game as OP, run:
   ```
   /smp admin setpassword <your-password>
   ```
3. Open `http://your-server-ip:8125` in any browser.

> Once enabled, the public dashboard (metrics, leaderboard, event feed) is accessible to anyone. Admin features require the password.

> [!WARNING]
> Manually setting `dashboard.enabled=true` in `config/quackedsmp.json` without a password is intentional if you want passwordless access, but admin will be open to anyone who can reach the port. Use `/smp admin setpassword` if you want the panel password-protected.

---

## Admin Panel

| Tab | What you can do |
| :--- | :--- |
| **Players** | See all online players, their dimension, OP status. Grant/revoke OP directly. |
| **Commands** | Run any server command from the browser. Quick actions for weather, time, broadcast, end reset, wilderness regen, and server stop. |
| **Dimensions** | Create, delete, and configure custom dimensions. Wire portal frame blocks. |
| **Skills** | Browse every player's skill levels. Set or adjust XP and levels per skill. |
| **Claims** | View all active claims on the server. Force-unclaim all chunks owned by a player. |
| **Teams** | Create, configure, and delete scoreboard teams. Auto-assign players to teams by dimension. |
| **Chat Filter** | Add/remove blocked words. View active mutes, unmute players. |
| **Config** | Edit every configurable value live. No file editing, no restart for most settings. |
| **Mods** | Upload, replace, or remove server mods directly from the browser. |

### Public Dashboard

Accessible to anyone who can reach the port. No login required:

- **Live metrics**: RAM, disk, uptime. TPS, CPU, MSPT if [Spark](https://modrinth.com/plugin/spark) is installed.
- **Player count**: Live online count
- **Event feed**: Join/leave events and chat in real time (WebSocket)
- **Skills leaderboard**: Top players per skill and overall

### Security

The panel serves plain HTTP. Without TLS, your session token is unencrypted on the wire. The password itself is protected (PBKDF2, rate limiting), but the token can be sniffed after login on an untrusted network.

> [!WARNING]
> If your server has a domain, put the panel behind a reverse proxy (Caddy, Nginx) with TLS. If you don't, accessing it from home or a VPN is fine in practice.

**Auth**
- Passwords hashed with PBKDF2-SHA256 (100,000 iterations)
- Constant-time comparison on verify
- 128-bit random session tokens, 24-hour TTL
- All admin endpoints require a Bearer token sent explicitly in the `Authorization` header. Cookies are never used, which eliminates CSRF entirely: a malicious page cannot trigger authenticated requests on a user's behalf because it cannot access or inject the token.

**Brute-force protection**
- 5 failed logins within 15 minutes locks the IP out for 5 minutes (HTTP 429)
- Counter clears on successful login

**Path traversal**
- Static files reject any path with `..` and serve only from the embedded classpath

**Mod upload**
- Filename must end in `.jar`, no path separators, no `..`
- 100 MB size cap
- Written to a temp file first, then moved atomically

**Public endpoints (no auth)**
- `/api/health` - player count, server name
- `/api/metrics` - heap, RAM, disk, uptime, threads
- `/api/spark/*` - TPS, CPU, MSPT (requires [Spark](https://modrinth.com/plugin/spark))
- `/api/skills/leaderboard` - skill rankings
- WebSocket - join/leave events and chat

---

<details>
<summary><strong>Features</strong></summary>

All features are **individually toggleable** from the admin panel Config tab or directly in `config/quackedsmp.json`.

### RPG Skills

12 skills across 4 categories. Players earn XP through in-game actions, unlock passive buffs, and trigger active abilities.

| Category | Skills | XP Sources |
| :--- | :--- | :--- |
| **Industrial** | Mining, Excavation, Woodcutting | Breaking ores, digging, chopping logs |
| **Nature** | Farming, Fishing, Agility | Harvesting crops, catching fish, sprinting & swimming |
| **Combat** | Melee, Archery, Defense | Dealing damage, bow kills, taking damage |
| **Knowledge** | Enchanting, Alchemy, Trading | Enchanting, brewing, villager trading |

**Leveling**
- Levels 1–10: Fast (flat 50 XP/level)
- Levels 11–100: Exponential curve (configurable exponent, default 1.5)

**Parent Buffs** (passive attributes based on average category level, scales 0 to 100):
- Industrial → +Movement Speed (max +50%)
- Nature → +Max Health (max +10 hearts)
- Combat → +Attack Damage (max +100%)
- Knowledge → +XP Orb gain (max +100%)

**Active Abilities** (unlock at Level 10 by default, configurable per skill):

| Skill | Ability | Effect | Cooldown (Lv 10 → Lv 100) |
| :--- | :--- | :--- | :--- |
| Mining | Super Breaker | Haste V | 3m → 1m |
| Excavation | Giga Drill | Haste V | 3m 45s → 1m 15s |
| Woodcutting | Tree Feller | Chain-breaks logs (max 64) | 3m 45s → 1m 15s |
| Farming | Green Terra | Bonemeals crops in 5-block radius | 2m 15s → 45s |
| Fishing | Master Angler | Luck V for 30s | 3m 45s → 1m 15s |
| Melee | Berzerk | Strength II + Speed II | 3m 45s → 1m 15s |
| Archery | Sniper | Slow Falling + Night Vision | 2m 15s → 45s |
| Archery | Scout Zoom | Spyglass zoom + mob glow in cone; Night Vision II at Lv 67+ | 30s |
| Defense | Juggernaut | Resistance IV + Slowness IV | 7m 30s → 2m 30s |
| Enchanting | Arcane Infusion | Repair held item by 10% | 15m → 5m |
| Alchemy | Philosopher's Touch | Silk Touch a Spawner (Sneak+Q on Spawner) | 7m 30s → 2m 30s |
| Trading | Tycoon's Charm | Hero of the Village (Sneak+Q with Emerald) | 15m → 5m |
| Agility | Dash | Velocity boost in look direction | 7.5s → 2.5s |

> Cooldowns decrease as you level up.

**Triggering Abilities:**
- **Tool abilities**: Hold tool + Sneak + Drop (Q). Item drop is cancelled.
- **Dash**: Sprint + Jump + Sneak (tap Shift) while moving.
- **Scout Zoom**: Sneak + F (swap offhand) with both hands empty. F again to cancel early.

> [!NOTE]
> If you hold a damaged tool and use Sneak + Drop, both the tool ability and Arcane Infusion can trigger simultaneously.

**Passive Perks:**
- **Double Drops**: Chance for extra items from mining/logging/harvesting
- **Bleed**: Chance to apply bleed damage on melee hits
- **Treasure Hunter**: Chance to find rare items in dirt/sand
- **Arrow Recovery**: Chance to retrieve arrows from killed mobs
- **Damage Reduction**: Flat mitigation from Defense level
- **Leaf Blower**: Woodcutting clears surrounding leaves automatically
- **Auto Replant**: Farming replants crops on harvest
- **Safe Landing**: Agility absorbs fall damage proportionally to level

---

### Land Claiming

Chunk-based land protection. Claim a chunk and no one else can build, break, or interact inside it.

**Anti-grief measures:**
- Explosions blocked if they affect claimed chunks
- Fire and lava spread/flow prevented inside claims
- Optional wilderness fire throttle to prevent arson griefing (toggle `allow_fire_wilderness`)
- Water only flows into a claim from the same owner's source
- Farmland trampling blocked inside claims and spawn protection
- Endermen cannot pick up or place blocks inside claims and spawn protection
- Armor Stands, Item Frames, Paintings, and Villagers protected from damage and theft
- PvP immunity inside own claims

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/claim` | Claim current chunk (or NxN grid if size is set) | Everyone |
| `/claim size <1-7>` | Set claim brush radius (e.g. 3 = 3x3 grid). Resets to 1 on restart | Everyone |
| `/unclaim` | Unclaim current chunk | Everyone |
| `/claim info` | Show owned count, limit, remaining, and current chunk status | Everyone |
| `/claim map` | Visualize nearby chunks in chat | Everyone |
| `/claim name <name>` | Name a claim (used by BlueMap) | VIP / OP |
| `/claim transfer <player>` | Transfer all claims to another player | Everyone |
| `/claim trust <player>` | Trust a player in all your claims | Everyone |
| `/claim untrust <player>` | Revoke trust | Everyone |
| `/claim trustlist` | List all trusted players | Everyone |
| `/sos` | Eject all untrusted players from current claim | Everyone |

> `/trust`, `/untrust`, and `/trustlist` also work as standalone commands (legacy aliases).

---

### Teleportation

Warmup-based teleportation. Movement cancels the teleport.

- `/home`: teleport to bed/respawn point
- `/spawn`: teleport to world spawn
- `/tpr <player>`: request teleport to a player
- `/tpa <player>`: accept or deny a request

Warmup defaults to 5 seconds, configurable in the panel.

---

### Chat Management

- **Keyword filter** with anti-evasion: detects leet speak, separators, and repeated characters
- **Whitelist** to prevent false positives
- **Progressive auto-mutes**: 3 violations → auto-mute, escalating per offence (configurable durations)
- **Periodic announcements**: broadcast messages on a configurable interval
- **Manual mute/unmute** via `/mute <player> <minutes>` and `/unmute <player>` (OP)
- Chat filter managed entirely from the **Chat Filter** tab in the admin panel

---

### Custom Dimensions (`/dim`)

Create and manage runtime dimensions without datapacks.
 
> [!IMPORTANT]
> **Server Restart Required**: While dimensions are created immediately, a server restart is currently required for the Minecraft chunk engine to properly register players for block interaction (breaking/placing) in any newly created dimension. Until the restart, the dimension is "read-only."

**Generator types:**

| Type | Terrain |
| :--- | :--- |
| `overworld` | Standard overworld noise |
| `ether` | Floating islands. Fall through the void to return. All portals share one central spawn island. |
| `nether` | Vanilla nether |
| `end` | Vanilla end |

`overworld` and `ether` accept optional sub-parameters:

**Custom biomes:**
```
/dim create <id> overworld biomes <biome1[:weight]> [biome2[:weight]] ...
```
Weights control coverage, only ratios matter. Single biome = entire dim uses that biome. No biomes specified = full vanilla set.

**Flat terrain** (`overworld` only):
```
/dim create <id> overworld flat <block:height> [block:height] ...
```
Layers listed bottom to top.

**Ether parameters** (`ether` only):
```
/dim create <id> ether [<threshold> [<minRadius> <maxRadius> [<spacing>]]] [biomes <list>]
```
- `threshold` (-1.0 to 0.0): Noise density cutoff. Lower (e.g., -0.85) creates distinct separated islands; higher (e.g., -0.1) creates dense, massive overlapping continents. (Default: -0.85)
- `minRadius` & `maxRadius` (5.0 to 500.0): Determines the size bounds of the islands in blocks. (Default: 40 & 90)
- `spacing` (1 to 32): Controls the gaps between islands by scaling noise frequency. Higher values stretch the noise and push islands further apart. (Default: 8)

```
# Classic superflat
/dim create quacksmp:flatworld overworld flat minecraft:bedrock:1 minecraft:dirt:2 minecraft:grass_block:1

# Floating jungle islands with custom island sizes and density
/dim create quacksmp:sky_jungle ether -0.7 10 80 20 biomes minecraft:jungle
```

**Portal system:**
Wire any block type as a portal frame for a custom dimension:
```
/dim setportal <dim_id> <block_id>
```
Build the frame (same shape as nether portal, 2–21 wide, 3–21 tall), activate with a water bucket. Travel is bidirectional. Return portals are placed automatically on first entry.

| Command | Permission |
| :--- | :--- |
| `/dim create <id> <type> [sub-params]` | OP |
| `/dim delete <id>` | OP |
| `/dim list` | Everyone |
| `/dim tp <id>` | OP |
| `/dim tp <player> <id>` | OP |
| `/dim setportal <dim_id> <block_id>` | OP |

> `/dim delete` works on datapack dimensions too. Removes from session, deletes chunk data, and removes the datapack JSON.

---

### Discord Integration

Set a Discord webhook URL in the Config tab to mirror join/leave events and chat messages to a channel automatically.

---

### BlueMap Integration

Optional integration with [BlueMap](https://modrinth.com/plugin/bluemap) (built against API v2.7.3). Any BlueMap version shipping that API or newer should work. When BlueMap is installed, QuackedSMP automatically syncs:

- Claim region outlines (color-coded by normal / VIP / OP)
- Player home markers
- World border outline

All toggleable and color-configurable from the admin panel.

---

### Hardcore Mode

Password-protected hardcore sessions. Any player can create a session, share the password, and others join in. Inventory is stashed on join and restored when the session ends or you leave.

**How it works:**
- Creator picks a random start location (1,000+ blocks from spawn, within the world border)
- All participants are teleported there with empty inventories
- Deaths are tracked. When deaths reach the threshold (configurable % of peak players), the session ends for everyone
- Dead players become spectators at their death location until they leave or the session ends
- Leaving mid-session is always allowed; your inventory is restored immediately
- Players who left cannot rejoin the same session
- Sessions survive server restarts and long periods of downtime

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/smp hardcore create <name> <password>` | Create and auto-join a session | Everyone |
| `/smp hardcore join <name> <password>` | Join an existing session | Everyone |
| `/smp hardcore leave` | Leave your current session | Everyone |
| `/smp hardcore status` | View your session's status | Everyone |
| `/smp hardcore status <name>` | View a specific session's status | Everyone |
| `/smp hardcore list` | List all active sessions | Everyone |

Sessions can also be viewed and force-ended from the admin panel.

---

### Kits

Daily kit claim system. Players choose one kit per cooldown period from their available pool. Kits contain an armor set and items, and can be tier-gated for VIP exclusives.

- Global cooldown (default 24 hours) shared across all kits — pick one per day
- Each kit has a minimum tier requirement (0 = everyone, 1+ = VIP)
- Armor equips to the slot if empty, otherwise goes to inventory, otherwise drops at feet
- Items overflow to the ground if inventory is full
- Fully configurable from the admin panel VIP tab: armor slots with per-slot suggestions, item grid editor, cooldown, tier requirements

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/smp kit` | List available kits and cooldown status | Everyone |
| `/smp kit list` | Same as above | Everyone |
| `/smp kit <name>` | Claim a kit | Everyone |

---

### Simple Voice Chat Integration

Optional age-gate for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) (built against API v2.6.0). Any Simple Voice Chat version shipping that API or newer should work. When enabled:

- Only players with the Simple Voice Chat client installed are prompted
- Prompted shortly after joining once the voice chat client connects
- Unverified players cannot speak or hear others
- Re-prompted every 5 minutes until they respond
- Players can verify at any time with `/smp verify confirm` (or `/verify confirm`)

---

### End Reset

- `/smp end reset dragon`: respawn the Ender Dragon live without resetting the dimension
- `/smp end reset world`: queue a full End dimension reset for next server restart

---

### Wilderness Regen

Regenerate all unclaimed chunks across all dimensions. Claimed chunks and a 3-chunk buffer around them are preserved; everything else is wiped and regenerated from the world seed when players next visit.

- Queued via `/smp regen` (in-game with clickable confirmation) or the dashboard Commands tab
- Executes on the next server shutdown after worlds are saved
- Region files with no remaining chunks are deleted entirely to reclaim disk space
- Processes `region/`, `entities/`, and `poi/` subdirectories across overworld, nether, end, and custom dimensions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/smp regen` | Show warning and clickable confirm | OP |
| `/smp regen confirm` | Queue wilderness regen for next shutdown | OP |
| `/smp regen cancel` | Cancel a pending regen | OP |
| `/smp regen status` | Check if a regen is pending | OP |

---

### Spawn PvP Protection

Blocks PvP damage inside the vanilla spawn protection radius (set via `spawn-protection` in `server.properties`). Independent of the claims system. If either the attacker or victim is within the spawn protection square, damage is cancelled.

Toggle: `spawn_no_pvp` in config (default: `true`)

---

### Anti-XRay

Server-side ore obfuscation that defeats x-ray cheat clients. When enabled, all blocks fully surrounded by solid blocks are replaced with random ores in the data sent to clients. X-ray users see meaningless noise instead of real ore locations. Real blocks are revealed naturally as players mine adjacent blocks.

- Dimension-aware palettes: stone ores (y >= 0), deepslate ores (y < 0), nether ores
- Deterministic replacement prevents visual flickering on chunk re-sends
- Zero impact on legitimate players: ores appear normally as you dig
- No commands, no player interaction needed. Toggle on and forget.

Toggle: `antixray_enabled` in config (default: `true`)

</details>

---

<details>
<summary><strong>Full Command Reference</strong></summary>

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/smp help` | List top-level commands | Everyone |
| `/smp reload` | Reload configuration from disk | OP |
| `/smp bluemap` | Force-refresh all BlueMap markers | OP |
| `/smp config` | Open in-game config GUI | OP |
| `/smp config reset` | Factory reset config | OP |
| `/smp admin setpassword <password>` | Set admin panel password and enable the panel | OP |
| `/mute <player> <mins>` | Mute a player | OP |
| `/unmute <player>` | Unmute a player | OP |
| `/rules` | View server rules | Everyone |
| `/claim` | Claim current chunk (or NxN grid if size is set) | Everyone |
| `/claim size <1-7>` | Set claim brush radius | Everyone |
| `/unclaim` | Unclaim current chunk | Everyone |
| `/claim info` | Show owned count, limit, remaining, and current chunk status | Everyone |
| `/claim map` | Visualize nearby chunks in chat | Everyone |
| `/claim name <name>` | Name a claim | VIP / OP |
| `/claim transfer <player>` | Transfer all claims | Everyone |
| `/claim trust <player>` | Trust a player globally (also: `/trust`) | Everyone |
| `/claim untrust <player>` | Revoke trust (also: `/untrust`) | Everyone |
| `/claim trustlist` | List trusted players (also: `/trustlist`) | Everyone |
| `/sos` | Eject untrusted players from claim | Everyone |
| `/home` | Teleport to bed/spawn | Everyone |
| `/spawn` | Teleport to world spawn | Everyone |
| `/tpr <player>` | Request teleport | Everyone |
| `/tpa <player>` | Accept or deny teleport | Everyone |
| `/smp end reset dragon` | Reset Ender Dragon fight | OP |
| `/smp end reset world` | Queue End dimension reset | OP |
| `/smp regen` | Show wilderness regen warning | OP |
| `/smp regen confirm` | Queue wilderness regen | OP |
| `/smp regen cancel` | Cancel pending regen | OP |
| `/smp regen status` | Check regen status | OP |
| `/vip set <player> <tier>` | Set player tier | OP |
| `/vip remove <player>` | Remove assigned tier | OP |
| `/vip list` | List all assigned tiers | OP |
| `/vip info <player>` | Show assigned, earned, and effective tier | OP |
| `/punish <player>` | Wipe player inventory and claim items | OP |
| `/skills` | Skill overview | Everyone |
| `/skills <skill>` | Detailed skill info | Everyone |
| `/skills view <player>` | View another player's skills | Everyone |
| `/skills top` | Overall leaderboard | Everyone |
| `/skills top <skill>` | Per-skill leaderboard | Everyone |
| `/skills admin givexp <player> <skill> <amount>` | Give skill XP | OP |
| `/skills admin setlevel <player> <skill> <level>` | Set skill level | OP |
| `/smp keepinv` | Show keep inventory status (also: `/keepinv`) | Everyone |
| `/smp keepinv on` | Keep items and XP on death (also: `/keepinv on`) | Everyone |
| `/smp keepinv off` | Drop on death — vanilla (also: `/keepinv off`) | Everyone |
| `/smp verify confirm` | Confirm 18+ for voice chat (also: `/verify confirm`) | Everyone |
| `/smp verify deny` | Decline voice chat (also: `/verify deny`) | Everyone |
| `/smp hardcore create <name> <password>` | Create and auto-join a hardcore session | Everyone |
| `/smp hardcore join <name> <password>` | Join an existing hardcore session | Everyone |
| `/smp hardcore leave` | Leave current hardcore session | Everyone |
| `/smp hardcore status [name]` | View session status | Everyone |
| `/smp hardcore list` | List all active sessions | Everyone |
| `/smp kit` | List available kits and cooldown status | Everyone |
| `/smp kit list` | Same as above | Everyone |
| `/smp kit <name>` | Claim a kit | Everyone |
| `/dim create <id> <type> [sub-params]` | Create a custom dimension | OP |
| `/dim delete <id>` | Delete a custom dimension | OP |
| `/dim list` | List all active dimensions | Everyone |
| `/dim tp <id>` | Teleport self to dimension | OP |
| `/dim tp <player> <id>` | Teleport player to dimension | OP |
| `/dim setportal <dim_id> <block_id>` | Wire portal frame block to dimension | OP |

</details>

---

<details>
<summary><strong>Configuration Reference</strong></summary>

Config lives at `config/quackedsmp.json`. Most settings are editable live from the admin panel without touching the file. Hot-reloadable via `/smp reload`.

| Key | Default | Description |
| :--- | :--- | :--- |
| `max_claims` | `50` | Maximum chunks a normal player can claim |
| `tiers` | `[{tier:1, name:"VIP", minPlaytimeHours:100, bonusClaims:20}]` | Tier definitions: level, display name, playtime threshold, bonus claims |
| `player_tiers` | `[]` | Admin-assigned tiers by player name |
| `tp_warmup` | `5` | Seconds to stand still before teleporting |
| `message_interval` | `300` | Seconds between periodic tip broadcasts |
| `allow_lava_wilderness` | `false` | Allow lava placement outside claims |
| `allow_fire_wilderness` | `true` | Allow fire to spread in unclaimed wilderness |
| `spawn_no_pvp` | `true` | Block PvP inside vanilla spawn protection radius |
| `claims_enabled` | `true` | Enable the claiming system |
| `skills_enabled` | `true` | Enable the skills system |
| `chatfilter_enabled` | `true` | Enable the chat filter |
| `voicechat_enable` | `true` | Enable Simple Voice Chat age-gate |
| `mute_levels_minutes` | `[60,120,240,480,1440]` | Auto-mute durations per escalation tier |
| `welcome_message` | `"Welcome to …"` | Join message. `{player}` is replaced with the player name. |
| `dashboard.enabled` | `true` | Enable the dashboard HTTP server |
| `dashboard.port` | `8125` | Port the dashboard listens on |
| `dashboard.admin_enabled` | `false` | Enable the admin section (set via `/smp admin setpassword`) |
| `dashboard.server_name` | `""` | Server name shown in the dashboard header |
| `bluemap_show_homes` | `true` | Show homes on BlueMap |
| `bluemap_show_claims` | `true` | Show claim outlines on BlueMap |
| `bluemap_claim_color` | `00FFFF` | Hex color for normal claims |
| `bluemap_op_claim_color` | `FFD700` | Hex color for OP claims |
| `bluemap_vip_claim_color` | `8A2BE2` | Hex color for VIP claims |
| `bluemap_show_worldborder` | `true` | Show world border on BlueMap |
| `bluemap_worldborder_color` | `FF3C3C` | Hex color for world border |
| `antixray_enabled` | `true` | Enable server-side ore obfuscation against x-ray clients |
| `kits_enabled` | `true` | Enable the kit claim system |
| `kits.cooldownSeconds` | `86400` | Seconds between kit claims (default 24 hours) |
| `kits.kits` | see JSON | Kit definitions: name, displayName, minTier, armor, items |
| `hardcore_enabled` | `false` | Enable the hardcore session system |
| `team_auto_assign` | `{}` | Map of team name to list of dimension IDs for auto-assignment |
| `hardcore_death_percent` | `50` | Deaths as % of peak players to end a session |
| `skills.xp_exponent` | `1.5` | Exponential factor for skill leveling |
| `skills.ability_unlock_levels` | see JSON | Minimum level per skill to unlock active ability |
| `skills.cooldowns` | see JSON | Base cooldowns in seconds per ability |
| `skills.caps` | see JSON | Max passive bonus values at Level 100 |

**Caps (max bonus at Level 100):**
- `industrial_speed` (0.5) → +50% movement speed
- `nature_health` (10.0) → +10 hearts
- `combat_damage` (1.0) → +100% attack damage
- `knowledge_xp` (1.0) → +100% XP orb gain
- `double_drop` (0.5) → max 50% double drop chance
- `defense_armor` (10.0) → max +10 armor points
- `safe_landing` (1.0) → max 100% fall damage absorbed

</details>

---

## License

Copyright (c) 2025 QuackedSMP. All Rights Reserved.

You may use this mod on your Minecraft server. You may not redistribute, repackage, or publish it as your own.
