[![Release](https://img.shields.io/github/v/release/Rvhoyos/QuackedSMP)](https://github.com/Rvhoyos/QuackedSMP/releases)
![GitHub Downloads](https://img.shields.io/github/downloads/Rvhoyos/QuackedSMP/total)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1360431?label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/mc-mods/quackedsmp)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/quackedsmp?label=Modrinth%20downloads)](https://modrinth.com/mod/quackedsmp)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)


# QuackedSMP Essentials

QuackedSMP is a server-side utility mod for **Fabric** and **NeoForge**. It provides a land claiming system, an RPG-style skill progression system, teleportation commands, and chat management.

## Core Features

- **RPG Skills**: 12 skills with active abilities, passive perks, and attribute buffs.
- **Land Claims**: Chunk-based protection.
- **Teleportation**: `/home`, `/spawn`, and `/tpa` requests with configurable warmups.
- **Chat Management**: Anti-evasion filters, whitelists, and automated announcements.
- **Configurable**: Features are toggleable and tunable via JSON config.

---

## Skill System

Players earn XP by performing in-game actions, unlocking passive buffs and active abilities.

### Categories and Skills

4 main categories, each containing 3 skills.

| Category | Skills | XP Sources |
| :--- | :--- | :--- |
| **Industrial** | **Mining**, **Excavation**, **Woodcutting** | Breaking ores, digging dirt/gravel/sand, chopping logs |
| **Nature** | **Farming**, **Fishing**, **Agility** | Harvesting crops, catching fish, sprinting & swimming |
| **Combat** | **Melee**, **Archery**, **Defense** | Dealing damage to mobs, bow kills, taking damage |
| **Knowledge** | **Enchanting**, **Alchemy**, **Trading** | Enchanting items, brewing potions, trading with villagers |

### Leveling

- **Levels 1-10**: Fast progression (Flat 50 XP per level).
- **Levels 11-100**: Standard progression (Exponential curve).
- **Global Multiplier**: Configurable via `xp_exponent` (default 1.5).
- **Parent Buffs**: Passive attribute buffs based on the average level of category skills.
    - **Industrial**: +Movement Speed (max +50%)
    - **Nature**: +Max Health (max +10 Hearts)
    - **Combat**: +Attack Damage (max +100%)
    - **Knowledge**: +XP Orb Gain (max +100%)

### Active Abilities

Abilities unlock at **Level 10** by default (configurable per skill).

**Triggering Abilities:**
- **Tool Abilities**: Hold tool + Sneak + Drop Item (Q). Item drop is cancelled.
- **Dash**: Sprint + Jump + Sneak (Tap Shift) while moving.

| Skill | Ability | Effect | Cooldown (Lv 10 -> Lv 100) |
| :--- | :--- | :--- | :--- |
| **Mining** | Super Breaker | Haste V | 3m -> 1m |
| **Excavation** | Giga Drill | Haste V | 3m 45s -> 1m 15s |
| **Woodcutting** | Tree Feller | Chain-breaks logs (limit 64) | 3m 45s -> 1m 15s |
| **Farming** | Green Terra | Bonemeals crops in 5-block radius | 2m 15s -> 45s |
| **Fishing** | Master Angler | Luck V for 30s | 3m 45s -> 1m 15s |
| **Melee** | Berzerk | Strength II + Speed II | 3m 45s -> 1m 15s |
| **Archery** | Sniper | Slow Falling + Night Vision | 2m 15s -> 45s |
| **Defense** | Juggernaut | Resistance IV + Slowness IV | 7m 30s -> 2m 30s |
| **Enchanting** | Arcane Infusion | Repairs held item by 10% | 15m -> 5m |
| **Alchemy** | Philosopher's Touch | Silk Touch Spawner (Sneak+Q on Spawner) | 7m 30s -> 2m 30s |
| **Trading** | Tycoon's Charm | Hero of the Village (Sneak+Q w/ Emerald) | 15m -> 5m |
| **Agility** | Dash | Velocity boost in look direction | 7.5s -> 2.5s |

> [!NOTE]
> Cooldowns decrease as you level up.

### Passive Perks
- **Double Drops**: Chance to get double items from mining/logging/harvesting.
- **Bleed**: Chance to apply bleed damage on melee hits.
- **Treasure Hunter**: Chance to find rare items in dirt/sand.
- **Arrow Recovery**: Chance to retrieve arrows from killed mobs.
- **Damage Reduction**: Flat damage mitigation from Defense level.

---

## Land Claiming

Chunk-based claiming system.

### How to Claim
1. Stand in the chunk to protect.
2. Type `/claim`.
3. Type `/claim map` to see nearby claims.

### Anti-Grief Measures
- **Explosions**: Blocked if they damage claims.
- **Fire & Lava**: Spread and flow prevented inside claims.
- **Smart Water**: Water only flows into a claim if the source is from the same claim owner.
- **Entity Protection**: Armor Stands, Item Frames, Paintings, and Villagers are protected from damage and theft.
- **PvP Safety**: Immune to player damage inside own claims.

### Commands
- `/claim`: Claim current chunk.
- `/unclaim`: Unclaim current chunk.
- `/claim map`: Visualize chunks in chat.
- `/trust <player>`: Give player permissions in all claims.
- `/untrust <player>`: Revoke permissions.
- `/sos`: Eject all untrusted players from claim.
- `/claims`: View claim count.

---

## Teleportation

Commands with configurable warmups.

- **/home**: Teleport to respawn point.
- **/spawn**: Teleport to world spawn.
- **/tpa <player>**: Request teleport to player.
- **/tpaccept**: Accept request.
- **/tpdeny**: Deny request.

> **Warmup**: Default 5-second warmup. Movement cancels teleport.

---

## Chat Management

- **Filter**: Blocks configured words.
- **Anti-Evasion**: Detects "leet speak", separators, and repeated characters.
- **Whitelist**: Prevents false positives.
- **Periodic Messages**: Broadcast automated announcements.

**Commands (OP Only):**
- `/chatfilter add <word>`: Add word to blocklist.
- `/chatfilter whitelist add <word>`: Whitelist safe word.
- `/chatfilter test <msg>`: Test message filter.

---

## Configuration

Located at `config/quackedsmp.json`. Hot-reloadable via `/smp reload`.

### Configuration Reference

| Key | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `max_claims` | Integer | `50` | Maximum chunks a normal player can claim. |
| `vip_bonus_claims` | Integer | `20` | Additional claims for `vips`. |
| `vips` | List | `[]` | List of usernames with bonus claims. |
| `tp_warmup` | Integer | `5` | Seconds to stand still before teleporting. |
| `message_interval` | Integer | `300` | Seconds between periodic announcements. |
| `allow_lava_wilderness`| Boolean | `false` | If true, allows placing lava outside claims. |
| `welcome_message` | String | *See JSON* | Join message. |
| `periodic_messages` | List | *See JSON* | Periodic broadcast messages. |
| `skills.xp_exponent` | Double | `1.5` | Exponential factor for skill leveling. |
| `skills.ability_unlock_levels` | Map | `10*` | Map of skill names to level required for ability unlock. Defaults: Trading (1), Defense (5), Agility (3), others (10). |
| `skills.cooldowns` | Map | *Varies* | Base cooldowns (seconds) for abilities. |
| `skills.caps` | Map | *See JSON* | Maximum value for parent attribute buffs at Level 100. |

### Caps Explanation

The `caps` section defines the maximum bonus a player receives when a parent category reaches **Level 100**. Buffs scale linearly from Level 0 to 100.

- **`industrial_speed` (0.5)**: +50% Movement Speed at Level 100.
- **`nature_health` (10.0)**: +10 Hearts (+20 HP) at Level 100.
- **`combat_damage` (1.0)**: +100% Attack Damage at Level 100.
- **`knowledge_xp` (1.0)**: +100% XP Orb gain at Level 100.

### Complete JSON Example

```json
{
  "max_claims": 50,
  "vip_bonus_claims": 20,
  "vips": [
    "Notch",
    "Jeb_"
  ],
  "tp_warmup": 5,
  "message_interval": 300,
  "allow_lava_wilderness": false,
  "welcome_message": "&6Welcome to QuackedSMP, {player}!",
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
  "skills": {
    "xp_exponent": 1.5,
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
      "trading": 1,
      "defense": 5,
      "agility": 3
    },
    "caps": {
      "industrial_speed": 0.5,
      "nature_health": 10.0,
      "combat_damage": 1.0,
      "knowledge_xp": 1.0
    }
  }
}
```

---

## Command Reference

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/smp help` | List top-level commands | Everyone |
| `/smp reload` | Reload configuration | OP |
| `/rules` | View server rules | Everyone |
| `/claim` | Claim current chunk | Everyone |
| `/unclaim` | Unclaim current chunk | Everyone |
| `/trust <player>` | Trust a player globally | Everyone |
| `/untrust <player>` | Revoke trust | Everyone |
| `/sos` | Eject strangers from claim | Everyone |
| `/home` | Teleport to bed/spawn | Everyone |
| `/spawn` | Teleport to world spawn | Everyone |
| `/tpa <player>` | Request teleport | Everyone |
| `/skills` | Open skills GUI | Everyone |
| `/chatfilter list` | View blocked words | OP |
| `/chatfilter strikes` | View collision history | OP |

---

## License

Licensed under the Apache License, Version 2.0.
