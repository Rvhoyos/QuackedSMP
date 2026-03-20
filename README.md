[![Release](https://img.shields.io/github/v/release/Rvhoyos/QuackedSMP)](https://github.com/Rvhoyos/QuackedSMP/releases)
![GitHub Downloads](https://img.shields.io/github/downloads/Rvhoyos/QuackedSMP/total)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1360431?label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/mc-mods/quackedsmp)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/quackedsmp?label=Modrinth%20downloads)](https://modrinth.com/mod/quackedsmp)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)


# QuackedSMP

QuackedSMP is a server-side utility mod for **Minecraft 1.21.11**, supporting both **Fabric** and **NeoForge**. It provides a land claiming system, an RPG-style skill progression system, teleportation commands, and chat management.

## Core Features
> do '/smp help' in game
- **RPG Skills**: 12 skills with active abilities, passive perks, and attribute buffs.
- **Land Claims**: Chunk-based protection.
- **Teleportation**: `/home`, `/spawn`, and `/tpa` requests with configurable warmups.
- **Chat Management**: Anti-evasion filters, whitelists, and automated announcements.
- **Custom Dimensions**: Create, delete, and manage runtime dimensions with custom terrain and biomes. Includes a custom portal system.
- **End Reset**: Reset the Dragon fight live, or queue a full dimension reset for the next restart.
- **BlueMap Support**: Optional, seamless sync of Claims and Player Homes to the web map.
- **Simple Voice Chat Integration**: Optional 18+ age-gate for Simple Voice Chat. Unverified players cannot speak or hear others until they confirm their age.
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
- **Scout Zoom**: Sneak + F (swap offhand key) with **both hands empty**. Press F again to cancel early.

> [!NOTE] 
> **Simultaneous Activation**: If you hold a damaged tool (e.g., Pickaxe) and use **Sneak + Drop**, both the tool ability (Super Breaker) and the repair ability (Arcane Infusion) can trigger at the same time!

| Skill | Ability | Effect | Cooldown (Lv 10 -> Lv 100) |
| :--- | :--- | :--- | :--- |
| **Mining** | Super Breaker | Haste V | 3m -> 1m |
| **Excavation** | Giga Drill | Haste V | 3m 45s -> 1m 15s |
| **Woodcutting** | Tree Feller | Chain-breaks logs (limit 64) | 3m 45s -> 1m 15s |
| **Farming** | Green Terra | Bonemeals crops in 5-block radius | 2m 15s -> 45s |
| **Fishing** | Master Angler | Luck V for 30s | 3m 45s -> 1m 15s |
| **Melee** | Berzerk | Strength II + Speed II | 3m 45s -> 1m 15s |
| **Archery** | Sniper | Slow Falling + Night Vision | 2m 15s -> 45s |
| **Archery** | Scout Zoom | Spyglass zoom + Night Vision + Slow Falling + mob glow in cone. Night Vision II at Lv.67+. Glow range scales 30/60/100 blocks by tier | 30s (configurable) |
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
- **Leaf Blower**: Woodcutting clears surrounding leaves automatically.
- **Auto Replant**: Farming automatically replants crops on harvest.
- **Safe Landing**: Agility absorbs fall damage proportionally to level (linear scale up to cap at Level 100).


---



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
- `/claim transfer <player>`: Transfer all your claims to another player.
- `/trust <player>`: Give player permissions in all claims.
- `/untrust <player>`: Revoke permissions.
- `/sos`: Eject all untrusted players from claim.
- `/claims`: View claim count.

---

## Teleportation

Commands with configurable warmups.

- **/home**: Teleport to respawn point (Bed).
- **/spawn**: Teleport to world spawn.
- **/tpr <player>**: Request teleport to player.
- **/tpa accept**: Accept request.
- **/tpa deny**: Deny request.

> **Warmup**: Default 5-second warmup. Movement cancels teleport. Configurable in config..

---

## Chat Management

- **Filter**: Blocks configured words.
- **Anti-Evasion**: Detects "leet speak", separators, and repeated characters.
- **Whitelist**: Prevents false positives.
- **Periodic Messages**: Broadcast automated announcements.
- **Progressive Mutes**: Repeated violations trigger auto-mutes.
  - 3 strikes -> 1m mute (default).
  - Subsequent bans increase in duration (e.g., 2h, 4h, 8h, 24h).
  - Resets after 24h of good behavior.

**Commands (OP Only):**
- `/mute <player> <minutes>`: Temporarily mute a player.
- `/unmute <player>`: Unmute a player.
- `/chatfilter add <word>`: Add word to blocklist.
- `/chatfilter whitelist add <word>`: Whitelist safe word.
- `/chatfilter test <msg>`: Test message filter.

---

## Configuration

Located at `config/quackedsmp.json`. Hot-reloadable via `/smp reload`.

### In-Game GUI (OP Only)

You can manage the server configuration in-game using a visual interface:
-   **Open Menu**: `/smp config`
-   **Features**: Adjust claim limits, teleport warmups, message intervals, and skill leveling curves via a GUI.
-   **Factory Reset**: Shift-Click the red powder in the config menu or use `/smp config reset` to restore all default settings.

### Configuration Reference

| Key | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `max_claims` | Integer | `50` | Maximum chunks a normal player can claim. |
| `vip_bonus_claims` | Integer | `20` | Additional claims for `vips`. |
| `vips` | List | `[]` | List of usernames with bonus claims. |
| `tp_warmup` | Integer | `5` | Seconds to stand still before teleporting. |
| `message_interval` | Integer | `300` | Seconds between periodic announcements. |
| `allow_lava_wilderness`| Boolean | `false` | If true, allows placing lava outside claims. |
| `voicechat_enable` | Boolean | `true` | Enable the Simple Voice Chat age-gate integration. Requires Simple Voice Chat to be installed. |
| `bluemap_show_homes` | Boolean | `true` | Show player beds on map. |
| `bluemap_show_claims`| Boolean | `true` | Show claimed regions on map. |
| `bluemap_claim_color`| String | `00FFFF` | Hex color of claims. |
| `bluemap_op_claim_color`| String | `FFD700` | Hex color of OP claims. |
| `bluemap_vip_claim_color`| String| `8A2BE2` | Hex color of VIP claims. |
| `mute_levels_minutes` | List | `[60,...]` | Mute durations (mins) for progressive bans (Tier 1-5). |
| `welcome_message` | String | *See JSON* | Join message. |
| `periodic_messages` | List | *See JSON* | Periodic broadcast messages. |
| `skills.xp_exponent` | Double | `1.5` | Exponential factor for skill leveling. |
| `skills.ability_unlock_levels` | Map | `10*` | Map of skill names to level required for ability unlock. Defaults: Trading (1), Defense (5), Agility (3), `archery_zoom` (5), others (10). |
| `skills.cooldowns` | Map | *Varies* | Base cooldowns (seconds) for abilities. |
| `skills.caps` | Map | *See JSON* | Maximum values at Level 100 for parent buffs and per-skill passives. |

### Caps Explanation

The `caps` section defines the maximum bonus a player receives when a parent category reaches **Level 100**. Buffs scale linearly from Level 0 to 100.

- **`industrial_speed` (0.5)**: +50% Movement Speed at Level 100.
- **`nature_health` (10.0)**: +10 Hearts (+20 HP) at Level 100.
- **`combat_damage` (1.0)**: +100% Attack Damage at Level 100.
- **`knowledge_xp` (1.0)**: +100% XP Orb gain at Level 100.
- **`double_drop` (0.5)**: Max 50% double drop chance (Mining/Woodcutting) at Industrial Level 100.
- **`defense_armor` (10.0)**: Max +10 Armor Points at Defense Level 100.
- **`safe_landing` (1.0)**: Max fraction of fall damage absorbed at Agility Level 100 (1.0 = full negation, scales linearly).

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
  "bluemap_show_homes": true,
  "bluemap_show_claims": true,
  "bluemap_claim_color": "00FFFF",
  "bluemap_op_claim_color": "FFD700",
  "bluemap_vip_claim_color": "8A2BE2",
  "mute_levels_minutes": [
    60,
    120,
    240,
    480,
    1440
  ],
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
      "archery_zoom": 30,
      "defense": 600,
      "enchanting": 1200,
      "alchemy": 600,
      "trading": 1200
    },
    "ability_unlock_levels": {
      "trading": 1,
      "defense": 5,
      "agility": 3,
      "archery_zoom": 5
    },
    "caps": {
      "industrial_speed": 0.5,
      "nature_health": 10.0,
      "combat_damage": 1.0,
      "knowledge_xp": 1.0,
      "double_drop": 0.5,
      "defense_armor": 10.0,
      "safe_landing": 1.0
    }
  }
}
```

---

## Custom Dimensions (`/dim`)

Create, manage, and teleport to runtime dimensions without datapacks or restarts. All custom dimensions persist across server restarts automatically.

### Generator Types

| Type | Terrain | Default Biomes |
| :--- | :--- | :--- |
| `overworld` | Standard overworld noise | Full vanilla overworld biome set |
| `ether` | Floating islands (fall through the void to return to the overworld). All portals share one central spawn island with a single return portal. | Full vanilla overworld biome set |
| `nether` | Vanilla nether | Vanilla nether biomes |
| `end` | Vanilla end | Vanilla end biomes |

### Sub-Parameters

`overworld` and `ether` accept optional sub-parameters to customise biome placement and terrain.

#### `biomes` — Custom biome list

```
/dim create <id> overworld biomes <biome1[:weight]> [biome2[:weight]] ...
/dim create <id> ether    biomes <biome1[:weight]> [biome2[:weight]] ...
```

Specifies which biomes appear in the dimension and how much of the world each one covers.

- **Weight** controls coverage share — only the ratio between weights matters. `plains:1 desert:1` and `plains:7 desert:7` produce identical 50/50 distributions.
- **Single biome** — the entire dimension uses that one biome exclusively.
- **No biomes specified** — uses the full vanilla biome set for that type.

Biomes are placed using the world's noise-based climate system. The weights translate directly to how much area each biome occupies — higher weight means more of the world. Biome content (mob spawns, structures, weather, surface decoration) is completely vanilla once placed.

**Examples:**
```
# All desert, no other biomes
/dim create quacksmp:desert_world overworld biomes minecraft:desert

# Mostly plains, some forest, rare mushroom fields (roughly 50% / 40% / 10%)
/dim create quacksmp:mixed overworld biomes minecraft:plains:5 minecraft:forest:4 minecraft:mushroom_fields:1

# Floating jungle islands
/dim create quacksmp:sky_jungle ether biomes minecraft:jungle
```

#### `flat` — Flat terrain (`overworld` only)

```
/dim create <id> overworld flat <block:height> [block:height] ...
```

Generates flat terrain. Layers are listed **bottom to top**. Each layer is a block ID followed by `:height` (number of layers of that block).

**Examples:**
```
# Classic superflat: 1 bedrock, 2 dirt, 1 grass
/dim create quacksmp:flatworld overworld flat minecraft:bedrock:1 minecraft:dirt:2 minecraft:grass_block:1

# Stone platform with a grass top
/dim create quacksmp:arena overworld flat minecraft:stone:10 minecraft:grass_block:1
```

### Portal System

Custom portals can be wired to a dimension using any block as the frame material instead of obsidian.

1. **Wire a block to a dimension:**
   ```
   /dim setportal <dim_id> <block_id>
   ```
   The first custom dim created is automatically assigned `minecraft:glowstone` as its frame block (if glowstone isn't already claimed by another dim). All other dims must be assigned manually.
2. **Build a portal frame** using that block (same shape as a nether portal, 2–21 wide, 3–21 tall) in any vanilla dimension.
3. **Activate it** by right-clicking the frame with a water bucket — the portal fills with nether portal blocks.
4. **Travel is bidirectional** — stepping through from the destination returns you to the overworld.

**Return portals are placed automatically.** On first entry through a portal, a matching return portal is generated inside the destination dimension at the same XZ coordinates. Each overworld portal links to its own return portal — two portals at different locations will each have a dedicated return portal near where they land. Ether dimensions are an exception: all portals lead to one shared floating island with a single return portal.

One block type can only be wired to one dimension at a time. Reassigning it will automatically unwire the previous dimension.

### Commands

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/dim create <id> overworld [biomes [...] \| flat <layers>]` | Create overworld dimension | OP |
| `/dim create <id> ether [biomes [...]]` | Create floating-islands dimension | OP |
| `/dim create <id> nether` | Create nether dimension | OP |
| `/dim create <id> end` | Create end dimension | OP |
| `/dim delete <id>` | Delete a custom dimension (evicts players first) | OP |
| `/dim list` | List all active dimensions | Everyone |
| `/dim tp <id>` | Teleport yourself to a dimension | OP |
| `/dim tp <player> <id>` | Teleport a player to a dimension | OP |
| `/dim setportal <dim_id> <block_id>` | Wire a block type as portal frame for a dimension | OP |

> **Note:** Vanilla dimensions (`minecraft:overworld`, `minecraft:the_nether`, `minecraft:the_end`) cannot be deleted.

> **Datapack dimensions:** `/dim delete` also works on dimensions registered via datapacks — not just ones created with `/dim create`. It will remove the dimension from the active session, delete its chunk data, and delete the datapack JSON file so it does not re-register on restart. Modded biomes and entity spawns from other mods are fully supported — biomes are resolved from the live registry at runtime.

---

## Command Reference

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/smp help` | List top-level commands | Everyone |
| `/smp reload` | Reload configuration | OP |
| `/smp config` | Open configuration GUI | OP |
| `/smp config reset` | Factory reset config | OP |
| `/mute <player> <mins>` | Mute a player | OP |
| `/unmute <player>` | Unmute a player | OP |
| `/rules` | View server rules | Everyone |
| `/claim` | Claim current chunk | Everyone |
| `/unclaim` | Unclaim current chunk | Everyone |
| `/claims` | Show owned chunk count and current chunk owner | Everyone |
| `/claim info` | Show owned count vs. your claim limit | Everyone |
| `/claim map` | Visualize nearby chunks in chat | Everyone |
| `/claim name <name>` | Set a display name for the current claim (used by BlueMap integration) | VIP / OP |
| `/claim transfer <player>` | Transfer all your claims to another player | Everyone |
| `/trust <player>` | Trust a player globally | Everyone |
| `/untrust <player>` | Revoke trust | Everyone |
| `/trustlist` | List all players you currently trust | Everyone |
| `/sos` | Eject strangers from claim | Everyone |
| `/home` | Teleport to bed/spawn | Everyone |
| `/spawn` | Teleport to world spawn | Everyone |
| `/tpr <player>` | Request teleport | Everyone |
| `/tpa <player>` | Accept or deny teleport | Everyone |
| `/smp end reset dragon` | Reset Ender Dragon fight | OP |
| `/smp end reset world` | Queue End dimension reset for next restart | OP |
| `/vip add <player>` | Grant VIP status to a player | OP |
| `/vip remove <player>` | Revoke VIP status from a player | OP |
| `/vip list` | List all current VIPs | OP |
| `/punish <player>` | Wipe player inventory and all items in their claims | OP |
| `/skills` | Concise overview of all your skill levels | Everyone |
| `/skills <skillname>` | Detailed info for one skill | Everyone |
| `/skills view <player>` | View another player's skills | Everyone |
| `/skills top` | Overall leaderboard | Everyone |
| `/skills top <skillname>` | Per-skill leaderboard | Everyone |
| `/skills admin givexp <player> <skill> <amount>` | Give skill XP | OP |
| `/skills admin setlevel <player> <skill> <level>` | Set skill level | OP |
| `/keepinv` | Show your keep inventory status | Everyone |
| `/keepinv on` | Keep items and XP on death | Everyone |
| `/keepinv off` | Drop items and XP on death (vanilla behavior) | Everyone |
| `/verify confirm` | Confirm you are 18+ and enable Simple Voice Chat | Everyone |
| `/verify deny` | Decline; voice chat remains disabled | Everyone |
| `/dim create <id> <type> [sub-params]` | Create a custom dimension | OP |
| `/dim delete <id>` | Delete a custom dimension | OP |
| `/dim list` | List all active dimensions | Everyone |
| `/dim tp <id>` | Teleport self to dimension | OP |
| `/dim tp <player> <id>` | Teleport player to dimension | OP |
| `/dim setportal <dim_id> <block_id>` | Wire a portal frame block to a dimension | OP |


---

## Simple Voice Chat Integration

Requires [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) to be installed on the server. Clients must also have the mod installed to use voice chat, but it is **not required** to join the server.

When enabled (`voicechat_enable: true`), all voice chat audio is age-gated:

- On first join, players receive a clickable prompt to confirm or deny their age.
- Players who **confirm** (18+) can speak and hear others normally.
- Players who **deny** or ignore the prompt cannot speak or be heard until they verify.
- Unverified players are re-prompted every **5 minutes** until they respond.
- Denied players see a one-time reminder on each login and can verify at any time with `/verify confirm`.

> **Note:** This is a trust-based system. Lying about your age may result in a ban at server admin discretion.

---

## License

Licensed under the Apache License, Version 2.0.
