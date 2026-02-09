# QuackedSMP Audit & Assessment

## Core Philosophy: "Wilderness Danger, Home Safety"
The guiding principle for all protection logic in QuackedSMP is to **preserve the vanilla survival experience in the wild while guaranteeing absolute safety within claims.**

*   **Global Rules**: We do NOT use global gamerules (e.g., `mobGriefing=false`) because they sanitize the game too much. We want creepers to blow up holes in the wild. We want forest fires. We want TNT mining to work.
*   **Claim Rules**: Inside a claim, these dangers must be strictly blocked to prevent griefing.
    *   *Example*: A creeper explosion should damage blocks in the wilderness, but if the explosion radius touches a claim, the *entire* explosion should be cancelled (or at least the damage to the claim).
    *   *Example*: Fire should spread in a forest, but stop exactly at the border of a player's wooden house claim.

## Overview
This document serves as an internal reference for the QuackedSMP project state, functionality gaps, and future improvements.

### 1. Functionality Gaps (Critical)
The current claim protection implementation (`ClaimProtection.java`) is incomplete for a standard SMP experience where players want **safe homes** but a **dangerous wilderness**.

*   **Explosions & Griefing**:
    *   *Vanilla Gamerules*: `mobGriefing=false` and `tntExplodes=false` are **global**. They disable creeper farms, villager breeders, and TNT mining for everyone, everywhere.
    *   *Proposed Fix*: Block these events **only inside claimed chunks**. This allows players to use TNT for mining or have creeper farms in the wild, but prevents them from griefing someone's base.
*   **Fire Spread**:
    *   *Vanilla*: `doFireTick=false` stops all fire mechanics globally.
    *   *Proposed Fix*: Allow fire to burn in the wild (forest fires, clearing land) but extinguish it immediately if it attempts to spread into or start within a claim.
*   **Entity Interactions**:
    *   *Gap*: Players can currently kill your animals, trample crops, or steal from item frames inside your claim.
    *   *Proposed Fix*: Cancel damage/interaction events on entities (Animals, Armor Stands, Item Frames) if the source is a player not trusted in that claim.
*   **Unsafe Teleportation**:
    *   `SafeTeleport` moves players directly to target coordinates without safety checks (lava, suffocation, void).
*   **Visual Feedback**:
    *   No way to visualize claim boundaries without client-side mods.
    *   *Solution*: Implement a `/claim map` chat command to grid out nearby chunks (e.g. `[+]` for yours, `[-]` for wild, `[!]` for others).

### 2. Code Quality & Performance
*   **Claim Storage (`ClaimedSavedData`)**:
    *   Every claim/unclaim operation rewrites the *entire list* of claims to NBT disk storage.
    *   *Risk*: High latency with >1000 claims.
    *   *Recommendation*: Shard data by region files if scaling becomes an issue (post-MVP).
*   **Chat Filter Regex**:
    *   `maskPhrases` recompiles the regex pattern inside the main loop for every message. This is highly inefficient.
    *   *Fix*: Pre-compile patterns or refactor the filtering logic.
*   **Hardcoded Limits**:
    *   Max claims per player is hardcoded to `50` in `ClaimService.java`. Should be configurable via `quackedsmp.json`.

### 3. Design Decisions & Notes
*   **Permissions**: System is binary (Trusted vs Untrusted). No granular permissions (chests, doors) or single-chunk trust planned.
*   **Teleport Requests**: Stored in-memory. Loss on server restart is acceptable behavior.
*   **Chat Filter Persistence**:
    *   `quackedsmp.json`: Bulk initialization source.
    *   `SavedData` (Level specific): Active runtime persistence.
    *   *Flow*: JSON -> Memory/SavedData on start. Commands edit Memory/SavedData only. Intentional split.

### 4. Technical Stack
*   **Architecture**: Architectury API (Fabric + NeoForge targets).
*   **Java Version**: 21
*   **Minecraft Version**: 1.21.8

### 5. Next Steps
1.  Implement explosion and entity protection listeners.
2.  Add a `/claim map` command.
3.  Refactor Chat Filter regex compilation.
4.  Move hardcoded limits to config.
