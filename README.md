[![Release](https://img.shields.io/github/v/release/Rvhoyos/QuackedSMP)](https://github.com/Rvhoyos/QuackedSMP/releases)
![GitHub Downloads](https://img.shields.io/github/downloads/Rvhoyos/QuackedSMP/total)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1360431?label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/mc-mods/quackedsmp)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/quackedsmp?label=Modrinth%20downloads)](https://modrinth.com/mod/quackedsmp)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)


# QuackedSMP Essentials

QuackedSMP is a server-side utility mod for Fabric and NeoForge. It provides land claiming, teleportation commands, and chat management tools for multiplayer servers.

## Core Features

- **Land Claims:** Chunk-based claiming system to protect builds. Server operators bypass restrictions.
- **Teleportation:** Commands for home setting, spawn warping, and player-to-player teleport requests (TPA) with configurable warmups.
- **Chat Management:** Simple chat filter, automated broadcasts, and welcome messages.
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
      "word1",
      "word2"
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
| `/home` | Teleport to your respawn point (bed/anchor) | Everyone |
| `/spawn` | Teleport to world spawn | Everyone |
| `/tpa <player>` | Request to teleport to another player | Everyone |
| `/tpaccept` | Accept a teleport request | Everyone |
| `/tpdeny` | Deny a teleport request | Everyone |
| `/rules` | View server rules | Everyone |
| `/chatfilter list` | View blocked words | OP |
| `/chatfilter add` | Add a word to the filter | OP |
| `/chatfilter remove`| Remove a word from the filter | OP |

## License
Licensed under the Apache License, Version 2.0.
