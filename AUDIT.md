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
The current claim protection implementation (`ClaimProtection.java`) has a solid foundation for block breaking/placing, but lacks some polish to fully satisfy the core philosophy.

*   **Explosions & Griefing**:
    *   *Status*: **Implemented but overly aggressive**. The current `shouldCancelExplosion` checks a large bounding box (radius + 2) and cancels the entire explosion if *any* chunk in that box is claimed.
    *   *Issue*: A creeper exploding 15 blocks away from a claim border in the wilderness might be cancelled if the bounding box grazes the claim chunk, even if no claim blocks would be damaged.
    *   *Proposed Fix*: Refine the cancellation logic to be more precise. Perhaps filter the affected blocks list directly rather than cancelling the entire explosion preemptively based on a loose chunk boundary.
*   **Entity Interactions**:
    *   *Status*: **Implemented but incomplete**. `isProtectedEntity` protects animals, armor stands, and hanging entities.
    *   *Issue*: `canInteractEntity` correctly checks claim modify access, but the `onLivingHurt` logic currently allows *monsters* to bypass protection (which is expected) but then completely skips PvP protection if the attacker is *not* a player (e.g. a dispenser firing an arrow).
*   **Unsafe Teleportation**:
    *   *Status*: **Vulnerable**. `SafeTeleport` moves players directly to target coordinates without any safety checks (lava, suffocation, void).
    *   *Proposed Fix*: Implement safe-spot finding algorithms (scanning up/down for a solid block with air above it) before completing the `/tpr` teleport.
*   **Visual Feedback**:
    *   *Status*: **Missing**. No way to visualize claim boundaries without client-side mods.
    *   *Proposed Fix*: Implement a `/claim map` chat command to grid out nearby chunks (e.g. `[+]` for yours, `[-]` for wild, `[!]` for others).

### 2. Code Quality & Performance
*   **Claim Storage (`ClaimedSavedData`)**:
    *   *Status*: **Inefficient**. The `CODEC` serializes a flat `List<ClaimData>`. More importantly, `unclaim` and `claim` call `setDirty()`, meaning the *entire list* of claims is rewritten to NBT disk storage on every single change.
    *   *Risk*: High I/O latency spikes on servers with thousands of claims.
    *   *Recommendation*: Data needs to be chunked or sharded, perhaps using Region files or a SQLite database, if the server scales up.
*   **Chat Filter Regex**:
    *   *Status*: **Resolved**. The previous audit claimed regex was recompiled on the fly. Review of `ChatFilterConfig` and `ChatFilterSavedData` shows this is false. `getPhrasePatterns()` caches the compiled `Pattern` list. However, the normalization pipeline (`normalizeLeet`, `squash`) does heavy string allocation per message token.

### 3. Design Decisions & Notes
*   **Permissions**: System is binary (Trusted vs Untrusted). No granular permissions (chests, doors) or single-chunk trust planned.
*   **Teleport Requests**: Stored in-memory. Loss on server restart is acceptable behavior.
*   **Hardcoded Limits**:
    *   *Status*: **Resolved**. Claim limits are now correctly pulled from `SmpConfig`.

### 4. Technical Stack
*   **Architecture**: Common-code split via subprojects (Fabric/NeoForge wrappers).
*   **Java Version**: 21
*   **Minecraft Version**: 1.21.8

### 5. Next Steps
1.  **Refine Explosion Protection**: Move away from chunk-based bounding box cancellation to precise block-list filtering to avoid saving chunks outside the blast radius.
2.  **Harden `SafeTeleport`**: Add Y-axis scanning to ensure `/tpa` landing spots are safe from lava/suffocation.
3.  **Implement `/claim map`**: Add visual ascii grid to help players navigate claims without client-side minimap mods.
