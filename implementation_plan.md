# Enhanced Claim Protection - Implementation Plan

## Goal
Implement robust claim protection that adheres to the **"Wilderness Danger, Home Safety"** philosophy. This ensures players are safe from griefing (explosions, fire, theft) within their claims, while preserving vanilla mechanics in the wilderness.

## User Review Required
> [!IMPORTANT]
> **Explosion Handling**: 
> Current plan is to cancel the *entire* explosion event if *any* block within the blast radius is claimed. This is the safest approach to prevent "edge griefing" where an explosion just outside the border damages blocks inside.
> *Alternative*: Filter block-by-block. This is more complex and potentially performance-heavy for large explosions.
> **Decision**: Proceed with full cancellation for safety and simplicity.

## Proposed Changes

### Claims Module
#### [MODIFY] [ClaimProtection.java](file:///Users/raul/Documents/Projects/QuackedSMP/QuackedSMP/common/src/main/java/mc/smpessentials/claims/ClaimProtection.java)
- **Explosion Events**: Register listeners for `ExplosionEvent.DETONATE` (or Architectury equivalent).
    - Check if any affected block is inside a claim.
    - If yes, cancel the block damage.
- **Entity Interaction**: Register listeners for `InteractionEvent.INTERACT_ENTITY` & `AttackEntityEvent`.
    - Prevent non-trusted players from interacting with or hurting:
        - Animals (Sheep, Cows, etc.)
        - Armor Stands
        - Item Frames / Paintings
        - Villagers (trading is fine, hurting is not)
- **Fire/Fluid Spread**: Register `BlockEvent.PLACE` or specific fluid/fire tick listeners (depending on availabilty in Architectury).
    - Prevent fire from spreading *into* a claim from outside.
    - Prevent lava/water buckets from being placed by non-trusted players (already covered by `BlockEvent.PLACE`, verify bucket interact).

#### [MODIFY] [ClaimAccess.java](file:///Users/raul/Documents/Projects/QuackedSMP/QuackedSMP/common/src/main/java/mc/smpessentials/claims/ClaimAccess.java)
- Ensure `canModify` util handles non-player sources correctly (e.g. TNT primed by a player, or a random creeper).
- *Note*: For environmental damage like random fire spread or a wandering creeper, there is no "player" to check. The check becomes: "Is this chunk claimed? If yes, block it."

## Verification Plan

### Automated Tests
- None (Minecraft modding relies heavily on integration/manual testing).

### Manual Verification
1.  **Explosions**:
    - Place TNT inside a claim -> Should fail to break blocks.
    - Place TNT outside but near a claim -> Explosion damage should stop at the border OR be fully cancelled if it touches.
    - Lure a Creeper into a claim -> Let it explode -> No block damage.
2.  **Fire**:
    - Flint & Steel inside claim (non-trusted) -> Denied.
    - Light fire outside spread to inside -> Fire should not spawn in claim.
3.  **Entities**:
    - Try to kill a sheep in a claim (non-trusted) -> Damage cancelled.
    - Try to break an Item Frame -> Cancelled.
    - Armor Stand interaction -> Cancelled.
