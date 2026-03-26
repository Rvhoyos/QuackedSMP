[![Release](https://img.shields.io/github/v/release/Rvhoyos/QuackedSMP)](https://github.com/Rvhoyos/QuackedSMP/releases)
![GitHub Downloads](https://img.shields.io/github/downloads/Rvhoyos/QuackedSMP/total)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1360431?label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/mc-mods/quackedsmp)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/quackedsmp?label=Modrinth%20downloads)](https://modrinth.com/mod/quackedsmp)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-red)](LICENSE)


# QuackedSMP

QuackedSMP is a server-side mod for **Minecraft 1.21.11** (Fabric + NeoForge) built around a **built-in browser admin panel**. Drop the JAR, start the server, open a browser. No external tools, no plugins, no config file editing required.

Every feature is modular and toggleable from the panel. Use all of it, or just the parts you want. Claims, skills, chat filter, custom dimensions, Discord webhooks, BlueMap, Votifier: each one can be switched on or off independently without touching a file.

---

## Admin Panel

The admin panel is a password-protected browser UI served directly by the mod on a configurable port (default **8125**).

### Setup

1. Drop the JAR into your server's `mods/` folder.
2. Start the server.
3. In-game as OP, run:
   ```
   /smp admin setpassword <your-password>
   ```
   This enables the panel and sets the password in one step. Open `http://your-server-ip:8125` in any browser.

> The public dashboard (metrics, leaderboard, event feed) is always accessible. Only the admin section requires a password.

### Admin Panel Tabs

| Tab | What you can do |
| :--- | :--- |
| **Players** | See all online players, their dimension, OP status. Grant/revoke OP directly. |
| **Commands** | Run any server command from the browser. Full server-level permissions. |
| **Dimensions** | Create, delete, and configure custom dimensions. Wire portal frame blocks. |
| **Skills** | Browse every player's skill levels. Set or adjust XP and levels per skill. |
| **Claims** | View all active claims on the server. Force-unclaim any chunk. |
| **Chat Filter** | Add/remove blocked words. View active mutes, unmute players. |
| **Config** | Edit every configurable value live. No file editing, no restart for most settings. |
| **Showcase** | Overview of all features with toggle controls. |

### Public Dashboard

Accessible to anyone who can reach the port. No login required:

- **Live metrics**: TPS, CPU usage, MSPT, RAM, disk, uptime
- **Player count**: Live online count
- **Event feed**: Join/leave events and chat in real time (WebSocket)
- **Skills leaderboard**: Top players per skill and overall

### Discord Integration

Set a Discord webhook URL in the Config tab to mirror join/leave events and chat messages to a channel automatically.

---

## Features

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
- Water only flows into a claim from the same owner's source
- Armor Stands, Item Frames, Paintings, and Villagers protected from damage and theft
- PvP immunity inside own claims

**Commands:**

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/claim` | Claim current chunk | Everyone |
| `/unclaim` | Unclaim current chunk | Everyone |
| `/claims` | Show owned count and current chunk owner | Everyone |
| `/claim info` | Show owned count vs. your claim limit | Everyone |
| `/claim map` | Visualize nearby chunks in chat | Everyone |
| `/claim name <name>` | Name a claim (used by BlueMap) | VIP / OP |
| `/claim transfer <player>` | Transfer all claims to another player | Everyone |
| `/trust <player>` | Trust a player in all your claims | Everyone |
| `/untrust <player>` | Revoke trust | Everyone |
| `/trustlist` | List all trusted players | Everyone |
| `/sos` | Eject all untrusted players from current claim | Everyone |

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

Create and manage runtime dimensions without datapacks or restarts.

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

```
# Classic superflat
/dim create quacksmp:flatworld overworld flat minecraft:bedrock:1 minecraft:dirt:2 minecraft:grass_block:1

# Floating jungle islands
/dim create quacksmp:sky_jungle ether biomes minecraft:jungle
```

**Portal system:**
Wire any block type as a portal frame for a custom dimension:
```
/dim setportal <dim_id> <block_id>
```
Build the frame (same shape as nether portal, 2–21 wide, 3–21 tall), activate with a water bucket. Travel is bidirectional. Return portals are placed automatically on first entry.

**Commands:**

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

### BlueMap Integration

Optional integration with [BlueMap](https://modrinth.com/plugin/bluemap). When BlueMap is installed, QuackedSMP automatically syncs:

- Claim region outlines (color-coded by normal / VIP / OP)
- Player home markers
- World border outline

All toggleable and color-configurable from the admin panel.

---

### Simple Voice Chat Integration

Optional age-gate for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat). When enabled:

- Players receive a clickable prompt on first join to confirm they are 18+
- Unverified players cannot speak or hear others
- Re-prompted every 5 minutes until they respond
- Players can verify at any time with `/verify confirm`

---

### End Reset

- `/smp end reset dragon`: respawn the Ender Dragon live without resetting the dimension
- `/smp end reset world`: queue a full End dimension reset for next server restart

---

## Full Command Reference

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
| `/claim` | Claim current chunk | Everyone |
| `/unclaim` | Unclaim current chunk | Everyone |
| `/claims` | Show owned chunk count and current chunk owner | Everyone |
| `/claim info` | Show owned count vs. limit | Everyone |
| `/claim map` | Visualize nearby chunks in chat | Everyone |
| `/claim name <name>` | Name a claim | VIP / OP |
| `/claim transfer <player>` | Transfer all claims | Everyone |
| `/trust <player>` | Trust a player globally | Everyone |
| `/untrust <player>` | Revoke trust | Everyone |
| `/trustlist` | List trusted players | Everyone |
| `/sos` | Eject untrusted players from claim | Everyone |
| `/home` | Teleport to bed/spawn | Everyone |
| `/spawn` | Teleport to world spawn | Everyone |
| `/tpr <player>` | Request teleport | Everyone |
| `/tpa <player>` | Accept or deny teleport | Everyone |
| `/smp end reset dragon` | Reset Ender Dragon fight | OP |
| `/smp end reset world` | Queue End dimension reset | OP |
| `/vip add <player>` | Grant VIP status | OP |
| `/vip remove <player>` | Revoke VIP status | OP |
| `/vip list` | List all VIPs | OP |
| `/punish <player>` | Wipe player inventory and claim items | OP |
| `/skills` | Skill overview | Everyone |
| `/skills <skill>` | Detailed skill info | Everyone |
| `/skills view <player>` | View another player's skills | Everyone |
| `/skills top` | Overall leaderboard | Everyone |
| `/skills top <skill>` | Per-skill leaderboard | Everyone |
| `/skills admin givexp <player> <skill> <amount>` | Give skill XP | OP |
| `/skills admin setlevel <player> <skill> <level>` | Set skill level | OP |
| `/keepinv` | Show keep inventory status | Everyone |
| `/keepinv on` | Keep items and XP on death | Everyone |
| `/keepinv off` | Drop on death (vanilla) | Everyone |
| `/verify confirm` | Confirm 18+ for voice chat | Everyone |
| `/verify deny` | Decline voice chat | Everyone |
| `/dim create <id> <type> [sub-params]` | Create a custom dimension | OP |
| `/dim delete <id>` | Delete a custom dimension | OP |
| `/dim list` | List all active dimensions | Everyone |
| `/dim tp <id>` | Teleport self to dimension | OP |
| `/dim tp <player> <id>` | Teleport player to dimension | OP |
| `/dim setportal <dim_id> <block_id>` | Wire portal frame block to dimension | OP |

---

## Configuration Reference

Config lives at `config/quackedsmp.json`. Most settings are editable live from the admin panel without touching the file. Hot-reloadable via `/smp reload`.

| Key | Default | Description |
| :--- | :--- | :--- |
| `max_claims` | `50` | Maximum chunks a normal player can claim |
| `vip_bonus_claims` | `20` | Extra claims for VIP players |
| `vips` | `[]` | List of VIP usernames |
| `tp_warmup` | `5` | Seconds to stand still before teleporting |
| `message_interval` | `300` | Seconds between periodic tip broadcasts |
| `allow_lava_wilderness` | `false` | Allow lava placement outside claims |
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

---

## License

Copyright (c) 2025 QuackedSMP. All Rights Reserved.

You may use this mod on your Minecraft server. You may not redistribute, repackage, or publish it as your own.
