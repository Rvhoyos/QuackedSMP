[![Release](https://img.shields.io/github/v/release/Rvhoyos/QuackedSMP)](https://github.com/Rvhoyos/QuackedSMP/releases)
![GitHub Downloads](https://img.shields.io/github/downloads/Rvhoyos/QuackedSMP/total)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1360431?label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/mc-mods/quackedsmp)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/quackedsmp?label=Modrinth%20downloads)](https://modrinth.com/mod/quackedsmp)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)


# QuackedSMP Essentials

**QuackedSMP** is a robust server-side utility mod designed for Fabric and NeoForge. It bridges the gap between vanilla gameplay and managed multiplayer environments by providing essential quality-of-life features, land protection, and community management tools.

## Core Philosophy
- **Wilderness Danger:** Outside of claims, anything goes. PvP and griefing are enabled.
- **Home Safety:** Claims provide a safe haven where players can build without fear.
- **Fair Play:** Teleportation warmups prevent combat logging and instant escapes.

---

## 🔥 Features

### 🛡️ Land Claims & Protection
Protect your base from griefers with a simple chunk-based claiming system.
- **`/claim`**: Secure the chunk you are standing in.
- **`/claim map`**: Visualize nearby claims in chat (7x7 grid).
- **`/claim info`**: View your claim usage and limits.
- **`/trust <player>`**: Allow friends to build in your claims.
- **Protection:** Prevents block breaking/placing, explosions, fire spread, and container access by non-trusted players.
- **OP Bypass:** Server Operators automatically bypass all claim restrictions.

### 🏠 Teleportation & Homes
Navigate the world easily but fairly.
- **`/home`**: Teleport to your bed or respawn anchor.
- **`/spawn`**: Return to the world spawn.
- **`/tpa <player>`**: Request to teleport to another player.
- **Warmups:** Configurable delay (default 5s) before teleporting. Moving or taking damage cancels the teleport!

### 💬 Chat & Community
- **Chat Filter:** Blocks harmful language using an efficient, cached pattern matcher.
- **Welcome Message:** Customizable join message with color support.
- **Auto-Broadcast:** Periodic tips or announcements displayed to all players.
- **Formatting:** Supports `&` color codes in config (e.g., `&aGreen`, `&cRed`, `&lBold`).
- **`/rules`**: Display server rules (configurable).

### ⚙️ Configuration
The mod is highly configurable via `config/quackedsmp.json`. Changes can be applied instantly with **`/smp reload`**.

#### Example Config:
```json
{
  "max_claims": 50,
  "tp_warmup": 5,
  "welcome_message": "&6Welcome to QuackedSMP, {player}!",
  "message_interval": 300,
  "rules": [
    "&e1. Be respectful.",
    "&e2. No griefing inside claims.",
    "&e3. Wilderness is dangerous."
  ],
  "periodic_messages": [
    "&b[Tip] &fUse &a/claim &fto protect your land!",
    "&b[Tip] &fDon't forget to set your &a/home&f!"
  ]
}
```

#### Supported Formatting Codes
| Code | Color/Style | Code | Color/Style |
| :--- | :--- | :--- | :--- |
| `&0` | Black | `&9` | Blue |
| `&1` | Dark Blue | `&a` | Green |
| `&2` | Dark Green | `&b` | Aqua |
| `&3` | Dark Aqua | `&c` | Red |
| `&4` | Dark Red | `&d` | Light Purple |
| `&5` | Dark Purple | `&e` | Yellow |
| `&6` | Gold | `&f` | White |
| `&7` | Gray | `&l` | **Bold** |
| `&8` | Dark Gray | `&o` | *Italic* |

---

## 📜 Commands
| Command | Description | Permission |
| :--- | :--- | :--- |
| `/smp help` | List all user commands. | Everyone |
| `/smp reload` | Reload configuration. | OP |
| `/claim` | Claim current chunk. | Everyone |
| `/unclaim` | Unclaim current chunk. | Everyone |
| `/claim map` | Show claim map. | Everyone |
| `/claim info` | Show claim limits/usage. | Everyone |
| `/trust <player>` | Trust a player globally. | Everyone |
| `/untrust <player>` | Revoke trust. | Everyone |
| `/home` | Teleport to home. | Everyone |
| `/spawn` | Teleport to spawn. | Everyone |
| `/tpa <player>` | Request teleport. | Everyone |
| `/tpaccept` | Accept teleport request. | Everyone |
| `/tpdeny` | Deny teleport request. | Everyone |
| `/rules` | View server rules. | Everyone |
| `/chatfilter list` | View blocked words. | OP |
| `/chatfilter add` | Add word to filter. | OP |
| `/chatfilter remove`| Remove word from filter.| OP |

---

## License
Licensed under the Apache License, Version 2.0.
