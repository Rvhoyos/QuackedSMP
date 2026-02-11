[![Release](https://img.shields.io/github/v/release/Rvhoyos/QuackedSMP)](https://github.com/Rvhoyos/QuackedSMP/releases)
![GitHub Downloads](https://img.shields.io/github/downloads/Rvhoyos/QuackedSMP/total)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1360431?label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/mc-mods/quackedsmp)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/quackedsmp?label=Modrinth%20downloads)](https://modrinth.com/mod/quackedsmp)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)


# QuackedSMP Essentials

QuackedSMP is a server-side utility mod for Fabric and NeoForge. It provides land claiming, teleportation commands, chat management, and an RPG-style skill system.

## Core Features

- **Land Claims:** Chunk-based claiming system to protect builds. Server operators bypass restrictions.
- **Teleportation:** Commands for home setting, spawn warping, and player-to-player teleport requests (TPA) with configurable warmups.
### Leveling
- **Levels 1-10:** Fast progression (Flat 50 XP per level).
- **Levels 11-100:** Standard progression (Exponential curve).
- **Global XP Multiplier:** Configurable via `xp_exponent` (default 1.5).
- **Chat Management:** Advanced chat filter with anti-evasion detection (leet-speak, separators, repeated characters), whitelist support, sign/book/anvil filtering, automated broadcasts, and welcome messages.
- **Skill System:** 12 skills across 4 categories with XP progression, active abilities, and passive buffs.
- **Configuration:** Hot-reloadable JSON configuration.

## Configuration

The configuration file is located at `config/quackedsmp.json`. You can reload changes in-game using `/smp reload`.

### Default Configuration

```json
{
  "max_claims": 50,
  "tp_warmup": 5,
  "welcome_message": "&6Welcome to QuackedSMP, {player}!",
  "message_interval": 300,
  "vip_bonus_claims": 20,
  "allow_lava_wilderness": false,
  "vips": [],
  "rules": [
    "&e1. Be respectful.",
    "&e2. No griefing inside claims.",
    "&e3. Wilderness is dangerous (PvP enabled).",
    "&e4. No cheating."
  ],
  "periodic_messages": [
    "&b[Tip] &fUse &a/claim &fto protect your land!",
    "&b[Tip] &fSet your home by sleeping in a bed!",
    "&b[Tip] &fType &a/smp help &ffor commands!",
    "&b[Reminder] &fPlease respect the &6/rules&f!",
    "&b[Tip] &fVisit Spawn Shops for blocks & gear! Trade items for Emeralds!"
  ],
  "chatfilter": {
    "contents": [
      "badword1",
      "badword2"
    ],
    "whitelist": [
      "assassin",
      "classic"
    ]
  },
  "messages": {
    "claim.success": "Chunk claimed.",
    "claim.already_claimed": "This claim is already protected.",
    "claim.limit_reached": "You reached the claim limit ({count}).",
    "claim.spawn_protected": "You can’t claim inside spawn protection.",
    "unclaim.success": "Chunk unclaimed.",
    "unclaim.fail_ownership": "You don’t control this claim.",
    "claim.info.owned": "You own {count} chunk(s) in this dimension.",
    "claim.info.protected_by_you": "This chunk is protected by you.",
    "claim.info.protected": "This chunk is protected.",
    "claim.info.unclaimed": "Current chunk is unclaimed.",
    "tpr.sent": "Teleport request sent to {player}",
    "tpr.received": "{player} requested to teleport to you. Use /tpa accept or /tpa deny.",
    "tpr.self": "Cannot request teleport to self.",
    "tpr.cooldown": "A request was sent recently. Please wait.",
    "tpr.already_pending": "A request is already pending.",
    "tpr.queue_full": "That player has too many pending requests.",
    "tpa.no_pending": "No pending teleport requests.",
    "tpa.requester_offline": "Requester is no longer online.",
    "tpa.requester_busy": "Requester cannot be teleported right now.",
    "tpa.no_location": "No valid teleport location.",
    "tpa.teleporting_requester": "Teleporting {player} in 5 seconds...",
    "tpa.teleporting_to": "Teleported to {player}",
    "tpa.denied": "Teleport request denied by {player}",
    "tpa.denied_confirm": "Denied the oldest pending request."
  }
}
```

### Formatting Codes
Text fields support standard Minecraft legacy color codes (e.g., `&a` for green, `&c` for red, `&l` for bold).

## Commands

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/smp help` | List user commands | Everyone |
| `/smp reload` | Reload configuration | OP |
| `/claim` | Claim the chunk you are standing in | Everyone |
| `/unclaim` | Unclaim the current chunk | Everyone |
| `/claim map` | Display a map of claims around you | Everyone |
| `/claim info` | Check claim status and limits | Everyone |
| `/trust <player>` | Allow a player to interact with your claims | Everyone |
| `/untrust <player>` | Revoke trust from a player | Everyone |
| `/sos` | Eject all untrusted players from your claims | Everyone |
| `/home` | Teleport to your respawn point (bed/anchor) | Everyone |
| `/spawn` | Teleport to world spawn | Everyone |
| `/tpa <player>` | Request to teleport to another player | Everyone |
| `/tpaccept` | Accept a teleport request | Everyone |
| `/tpdeny` | Deny a teleport request | Everyone |
| `/rules` | View server rules | Everyone |
| `/chatfilter list` | View blocked words | OP |
| `/chatfilter add <word or phrase>` | Add a word/phrase to the filter | OP |
| `/chatfilter remove <word or phrase>` | Remove a word/phrase from the filter | OP |
| `/chatfilter test <message>` | Dry-run a message through the filter | OP |
| `/chatfilter whitelist add <word>` | Whitelist a word (prevents false positives) | OP |
| `/chatfilter whitelist remove <word>` | Remove a word from the whitelist | OP |
| `/chatfilter whitelist list` | View whitelisted words | OP |
| `/chatfilter strikes [player]` | View filter violation strikes | OP |
| `/skills` | Open the skills book GUI | Everyone |
| `/skills <skill>` | View details for a specific skill | Everyone |
| `/skills admin givexp <player> <skill> <amount>` | Award XP to a player | OP |
| `/skills admin setlevel <player> <skill> <level>` | Set a player's skill level (0-100) | OP |

---

## Skill System

### Overview

Players earn XP by performing in-game actions. Skills level up to 100 using an exponential XP curve. Each category has a parent level (average of its sub-skills) that provides passive attribute buffs.

### Categories & Skills

| Category | Skills | XP Sources |
| :--- | :--- | :--- |
| ⛏ **Industrial** | Mining, Excavation, Woodcutting | Breaking ores, dirt/gravel/sand, logs |
| 🌿 **Nature** | Farming, Fishing, Agility | Harvesting crops, catching fish, sprinting/swimming |
| ⚔ **Combat** | Melee, Archery, Defense | Hitting mobs, bow kills, taking damage |
| 📖 **Knowledge** | Enchanting, Alchemy, Trading | Enchanting items, brewing potions, villager trades |

### Active Abilities

Abilities unlock at **level 10** and are triggered by **sneaking + pressing Q** (drop key) while holding the matching tool. The item drop is cancelled — the item stays in your hand and the ability activates. Each ability has a configurable cooldown.

| Trigger | Ability | Effect |
| :--- | :--- | :--- |
| Sneak + Q with **pickaxe** | Super Breaker | Haste V (duration scales with level) |
| Sneak + Q with **shovel** | Giga Drill | Haste V |
| Sneak + Q with **axe** | Tree Feller | Chain-breaks connected logs (max 64) |
| Sneak + Q with **hoe** | Green Terra | Bonemeals crops in 5-block radius |
| Sneak + Q with **fishing rod** | Master Angler | Luck V for 30 seconds |
| Sneak + Q with **sword** | Berzerk | Strength II + Speed II |
| Sneak + Q with **bow/crossbow** | Sniper | Slow Falling + Night Vision |
| Sneak + Q with **shield** | Juggernaut | Resistance IV + Slowness IV |
| Sneak + Q with **damaged non-tool item** | Arcane Infusion | Repairs item by 10% durability |
| Sneak + Q with **book** (look at spawner) | Philosopher's Touch | Silk Touch Spawner (drops as item) |
| Sneak + Q with **emerald** | Tycoon's Charm | Hero of the Village (discounts) |
| **Sprint + Jump + Sneak** (Tap Shift) | Dash | Velocity boost in look direction |

> [!IMPORTANT]
> **Two trigger systems:**
> - **Sneak + Q** — used by all tool-based abilities (9 of 10). Avoids conflicts with vanilla tool actions like tilling, stripping, and drawing bows. The item drop is cancelled — nothing leaves your inventory.
> - **Sprint + right-click empty hand** — used only by **Dash**. Since you can't "drop" an empty hand, Dash uses right-click instead. Empty-hand right-click has no vanilla action, so there are zero conflicts.
>
> If any ability is on cooldown, the input is still consumed and you'll see the remaining cooldown time.


### Parent Buffs

Parent buffs are passive attribute modifiers that scale with the average level of sub-skills in each category. They are applied automatically on level-up and player join.

| Category | Buff | Default Cap |
| :--- | :--- | :--- |
| Industrial | +Movement Speed (multiplier) | +50% at max |
| Nature | +Max Health (flat HP) | +10 hearts at max |
| Combat | +Attack Damage (multiplier) | +100% at max |
| Knowledge | +XP Gain (all skills) | +100% at max |

### Passive Perks

- **Double Drops** — chance to double block drops (Industrial/Nature)
- **Bleed** — chance to apply bleed damage on melee hits
- **Treasure Hunter** — chance to find bonus items from blocks
- **Arrow Recovery** — chance to recover arrows after bow kills
- **Damage Reduction** — flat damage reduction from Defense level

### Skill Configuration

The `skills` section is auto-generated in `config/quackedsmp.json` on first load:

```json
{
  "skills": {
    "xp_exponent": 1.5,
    "cap_industrial_speed": 0.5,
    "cap_nature_health": 10.0,
    "cap_combat_damage": 1.0,
    "cap_knowledge_xp": 1.0,
    "cooldowns": {
      "mining": 240,
      "excavation": 300,
      "woodcutting": 300,
      "farming": 180,
      "fishing": 300,
      "agility": 10,
      "melee": 300,
      "archery": 180,
      "defense": 600,
      "enchanting": 1200,
      "alchemy": 600,
      "trading": 1200
    },
    "ability_unlock_levels": {
      "mining": 10,
      "excavation": 10,
      "woodcutting": 10,
      "farming": 10,
      "fishing": 10,
      "agility": 10,
      "melee": 10,
      "archery": 10,
      "defense": 10,
      "enchanting": 10,
      "alchemy": 10,
      "trading": 10
    }
  }
}
```

| Key | Description | Default |
| :--- | :--- | :--- |
| `xp_exponent` | Controls the XP curve steepness | `1.5` |
| `cap_*` | Maximum buff value at level 100 | Varies |
| `cooldowns.*` | Base cooldown in seconds (see below) | Varies |
| `ability_unlock_levels.*` | Minimum level to unlock active ability | `10` |

### Rebalanced Cooldowns & Scaling

Cooldowns now follow a **piecewise reduction curve** based on skill level:
- **Lv 0-10:** Drops quickly to **75%** of base time.
- **Lv 10-100:** Drops steadily to **25%** of base time.

| Ability | Base Cooldown (Lv 0) | Min Cooldown (Lv 100) |
| :--- | :--- | :--- |
| **Mining** | 240s (4m) | 60s (1m) |
| **Excavation** | 300s (5m) | 75s (1m 15s) |
| **Woodcutting** | 300s (5m) | 75s (1m 15s) |
| **Farming** | 180s (3m) | 45s |
| **Fishing** | 300s (5m) | 75s (1m 15s) |
| **Combat** | 300s (5m) | 75s (1m 15s) |
| **Archery** | 180s (3m) | 45s |
| **Defense** | 600s (10m) | 150s (2.5m) |
| **Trading** | 1200s (20m) | 300s (5m) |
| **Enchanting** | 1200s (20m) | 300s (5m) |
| **Alchemy** | 600s (10m) | 150s (2.5m) |

Durations have been **doubled**: Base 10s + 0.2s/level (e.g., 30s at Lv 100).



---

## License
Licensed under the Apache License, Version 2.0.
