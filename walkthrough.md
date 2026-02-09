# Claim Protection Implementation Walkthrough

## Completed Features

### 1. Explosion Protection
- **Mechanism:** `ExplosionEvent.PRE`.
- **Logic:** Radius Check. **ALL** explosions overlapping a claim are cancelled.
- **Safety:** Prevents TNT cannons, Wither skulls on borders.

### 2. Entity Protection & PvP
- **PvP Logic (Victim-Based Sanctuary):**
    - **Trusted/Owner:** CANNOT be hurt inside the claim (Safe).
    - **Stranger/Intruder:** CAN be hurt inside the claim (Free Game).
    - *Result:* 
        - Owners are safe in their base.
        - Intruders can be hunted down by owners.
        - Intruders can fight each other (Arenas).

- **PvE / Entity Protection:**
    - **Protected:** Animals, Armor Stands, Frames, Villagers.
    - **Logic:** Attacker must be trusted to interact/hurt these.

### 3. Block Protection
- **Mechanism:** Strict permission check for players.

### 4. Smart Environment & Mob Rules
- **Blocked (Griefing):**
    - Lava Flow (Environment placement).
    - Fire Spread (`BaseFireBlock`).
    - Mob Griefing (Endermen, Wither Body, Ravager Trample).
    - Snow/Ice Formation.
- **Allowed (Functionality):**
    - **Friendly Mobs:** 
        - `Sheep` (Grazing - Grass to Dirt)
        - `Villager` (Farming - Breaking/Placing crops)
        - `Fox` (Berry Harvesting)
        - `Bee` (Crop Pollination)
        - `Turtle` (Egg Laying)
    - **Gravity:** Sand, Gravel, Anvils.
    - **Crops:** `BonemealableBlock`, Sugar Cane, Cactus, Bamboo.
    - **Water Flow:** ONLY if source is in the same claim (Safe Plumbing).

### 5. Chat-Based Claim Map
- **Command:** `/claim map` (Alias: `/claim map`)
- **Features:**
    - Displays a 7x7 grid of chunks.
    - **Symbols:** `+` (You), `█` (Claim), `-` (Wild).
    - **Colors:** 
        - **Green:** Your Claim.
        - **Red:** Enemy Claim.
        - **Gray:** Wilderness/Unowned.
    - **Interactive:** Hovering over any cell shows coordinates and ownership status.

## Verification Results
- Command: `./gradlew build`
- Result: **SUCCESS** (Verified `HoverEvent` 1.21.8 implementation).

## Conclusion
The protection system is now robust, safe, and player-friendly. It allows for "Wilderness Danger" while ensuring "Home Safety" and functional farms. The map provides excellent visual feedback for players managing their territory.
