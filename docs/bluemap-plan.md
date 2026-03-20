# BlueMap Integration — Feature Plan

All BlueMap code lives in `common/src/main/java/mc/smpessentials/bluemap/`.

---

## Current State

- **Homes**: POI markers with a house SVG, reads online + offline `.dat` files, cached by `lastModified`.
- **Claims**: Connected chunks (same owner, BFS) drawn as `ShapeMarker` fills. Named regions get an `HtmlMarker` text label at the centroid.
- **Naming**: Names stored per-chunk on `ClaimData.name()`. `drawRegion` does a majority vote across chunks to pick the display name — this is the fragility being fixed below.

---

## Feature 1 — Bulletproof Region Naming

### Problem with current system

`/claim name <name>` (in `ClaimCommands.java`) names **only the single chunk** the player stands on. `drawRegion` then majority-votes across all chunks in the connected region to guess the name. This breaks when chunks have mixed names, and silently drops names on region merges.

### New system

**One named chunk per region** — the name is stored on exactly one chunk (the one the player stands on when they run `/claim name`). The BFS at render time scans all chunks in the region and picks the first one with a name set. No mass-writes, no voting.

### `/claim name <name>` behaviour change

1. BFS from the player's current chunk across all adjacent same-owner chunks (reuse/move `findConnectedRegions` from `BlueMapMarkerManager` into `ClaimService`)
2. Clear `name` from any chunk in that region that currently has one set
3. Set `name` only on the current chunk

Net result: always exactly one named chunk per region, no drift.

### Merge conflict rules (enforced in `ClaimService.claim`)

When a new claim bridges two of the same owner's previously disconnected regions, check for named chunks in both sides:

| Scenario | Result |
|---|---|
| Both unnamed | No action |
| One named, one unnamed | No action — named chunk persists, BFS finds it |
| Both named, same name | Clear the duplicate, keep one |
| Both named, different names | **Largest region (by chunk count) wins.** Clear the named chunk from the smaller region. Notify player in chat: `"Your regions 'X' and 'Y' merged — 'X' kept."` |

### Unclaim

No special handling needed. If the unclaimed chunk happened to be the named one, the region becomes unnamed. The player can re-run `/claim name` to rename. This is acceptable behaviour.

---

## Feature 2 — Named Region Icon Markers

When a VIP or OP names a claim region, the name determines which SVG icon appears at the centroid. The icon **replaces** the text label entirely — no text + icon combo, just the icon.

### Behaviour

- Unnamed region → shape fill only, no marker (same as today)
- Named region → one `POIMarker` at centroid, icon chosen by name, label = region name (shown on hover/click only)

### Name → Icon mapping

The name is matched case-insensitively against a predefined keyword set:

| Name keyword(s) | Icon |
|---|---|
| `shop`, `store`, `market` | Chest / coin bag |
| `arena`, `pvp`, `pit` | Crossed swords |
| `farm`, `field`, `garden` | Wheat stalk |
| `base`, `home`, `house` | House (reuse existing `quacksmp_house.svg`) |
| *(anything else)* | Generic flag/banner |

### Implementation delta in `BlueMapMarkerManager`

- Add SVG constants for each icon type
- Upload all SVGs to asset storage in `updateAll()`
- In `drawRegion`: when `bestName != null`, resolve the icon by keyword match, place one `POIMarker` at centroid — remove the existing `HtmlMarker` logic entirely

> **TODO**: Design the SVGs. All should be readable at small sizes (~32–64px). The flag fallback should be visually distinct from the house icon already used for homes.

---

## Feature 3 — World Structure & Rare Biome Markers

Automatically mark hard-to-find structures and rare biomes on the map as the world gets explored. Markers appear as chunks load — no world scan needed.

### Structures to mark

| Structure | Icon idea | Notes |
|---|---|---|
| Ancient City | Skull / dark lantern | Deep Dark only, Y-60+, rarest underground |
| Trial Chambers | Shield / vault door | New in 1.21, moderately rare |
| Woodland Mansion | Castle tower | Extremely rare overworld |
| Ocean Monument | Prismarine crystal | Only sponge/elder guardian source |
| Stronghold | Eye of Ender | End progression gate |
| Nether Fortress | Blaze / fire tower | Critical progression |
| Bastion Remnant | Piglin / gold pile | Only netherite upgrade source |
| End City | Elytra wings | Only elytra source |

### Rare biomes to mark

| Biome | Icon idea | Notes |
|---|---|---|
| Mushroom Fields | Mooshroom / mushroom | No hostile spawns, always isolated |
| Pale Garden | Pale oak leaf / ghost | Explicitly the rarest biome in the game |
| Ice Spikes | Ice spike / snowflake | Rare snowy variant, packed ice |
| Cherry Grove | Cherry blossom | Popular build biome, fairly uncommon |

### Config toggles

Each structure and biome group gets its own boolean in `SmpConfig` / `quackedsmp.json`:

```json
"bluemap_markers": {
  "ancient_city": true,
  "trial_chambers": true,
  "woodland_mansion": true,
  "ocean_monument": true,
  "stronghold": true,
  "nether_fortress": true,
  "bastion_remnant": true,
  "end_city": true,
  "mushroom_fields": true,
  "pale_garden": true,
  "ice_spikes": true,
  "cherry_grove": true
}
```

### Technical approach

Hook into the chunk load event. For each loaded chunk:
- Check `StructureManager` for structure starts of the tracked types
- Check chunk biome data for rare biome IDs
- If found and not already marked, add a `POIMarker` to the relevant `MarkerSet`

Each category gets its own `MarkerSet` (e.g. `quacksmp_structures`, `quacksmp_biomes`) so players can toggle them independently in the BlueMap UI.

Discovered locations are persisted in a new `DiscoveredMarkersData` SavedData (overworld DataStorage) — a `Map<String, Set<Long>>` keyed by structure/biome type to chunk position longs. On chunk load, check against this map before doing any work. On `updateAll()`, render from the persisted map directly — no re-scan needed after restart.

> **TODO**: Design SVG icons for each entry. All should be ~32–64px and readable at map zoom levels.

---

## Feature 4 — YouTube Content Markers

VIPs and OPs can pin a YouTube marker at their current location linking to a video they made about a build. The icon makes it immediately obvious it's a video link without embedding any preview.

### Behaviour

- Marker appears as a YouTube play-button SVG (`quacksmp_youtube.svg`) — red circle with a white triangle
- Clicking the marker opens a popup with the label + a styled `▶ Watch on YouTube` anchor tag that opens the URL in a new tab
- No video embedding, no thumbnail — just a clean link

### Command

```
/youtube add <url> <label>   — pins a marker at your current position
/youtube remove              — removes your nearest YouTube marker
/youtube list                — lists your own YouTube markers
```

Access: VIP and OP only (same gate as `/claim name`).

### Storage

New `SavedData`: `YoutubeMarkerData` stored in overworld `DataStorage`. Persists a list of records:

```java
record YoutubeEntry(UUID owner, String playerName, String label, String url,
                    double x, double y, double z, ResourceKey<Level> dimension)
```

URL is stored as-is. No validation beyond checking it starts with `https://` to prevent obviously bad input.

### BlueMap integration

- New `MarkerSet`: `quacksmp_youtube`, label `"Video Content"`, default hidden `false`
- Each `YoutubeEntry` becomes a `POIMarker` with the YouTube SVG icon
- Detail HTML:
  ```html
  <b>{label}</b><br>
  <a href="{url}" target="_blank" style="color:#FF0000;">▶ Watch on YouTube</a>
  ```
- Handled in `updateYoutube()` called from `updateAll()`
- Toggled by `bluemap_show_youtube` boolean in `SmpConfig`

### Config

```json
"bluemap_show_youtube": true
```

---

## Feature 5 — Claim Age Tinting ⚠️ INCOMPLETE

`ClaimData.createdAtMillis` is already stored on every chunk — no schema changes needed.

At render time, find the oldest `createdAtMillis` across all chunks in the region and use it to determine a visual tier. The exact tinting values and the reward design below are **not finalized**.

### Age tiers (thresholds TBD)

| Tier | Age | Visual idea |
|---|---|---|
| Fresh | < 7 days | Standard fill |
| Established | 7–30 days | Slightly richer color |
| Veteran | 30–90 days | Noticeably distinct |
| Ancient | 90+ days | Something extravagant — TBD |

### ⚠️ Reward design is unfinished

The owner wants the Ancient tier in particular to feel **rewarding and prestigious** on the map — not just a color fade. Ideas to explore:
- Animated or pulsing border effect (if BlueMap supports it via CSS in HtmlMarker)
- Gold/special color reserved only for ancient claims
- A unique SVG crown or seal icon placed at the region centroid
- Combination: distinct fill color + crown icon + special label styling

> **Owner note**: "I want an extravagant and enticing way of rewarding older claims." Final visual treatment needs a decision before implementing.

---

## Feature 6 — Server Event Markers 🕐 LOW PRIORITY

OP-pinned temporary markers for announced events (e.g. "Dragon fight tonight at 8PM"). Auto-expire after a configurable duration.

> **Owner note**: Not needed right now. Implement after higher priority features are done.

---

## Files to Create / Modify

| File | Change |
|---|---|
| `BlueMapMarkerManager.java` | Add all SVG constants, upload to asset storage, replace `HtmlMarker` with keyword-matched `POIMarker`, add `updateStructures()`, `updateBiomes()`, `updateYoutube()`, update `cleanup()` for new MarkerSets |
| `bluemap/DiscoveredMarkersData.java` | New — SavedData persisting discovered structure/biome chunk positions across restarts |
| `ClaimService.java` | Move BFS helper here, update `setName` to name whole region, add merge conflict detection in `claim` |
| `ClaimCommands.java` | No structural change, `/claim name` delegates to updated `ClaimService.setName` |
| `config/SmpConfig.java` | Add `Map<String, Boolean> BLUEMAP_MARKERS` and `BLUEMAP_SHOW_YOUTUBE` |
| `config/ConfigIO.java` | Serialize/deserialize `bluemap_markers` block and `bluemap_show_youtube` |
| `bluemap/YoutubeMarkerData.java` | New — SavedData persisting YouTube marker entries |
| `commands/YoutubeMarkerCommand.java` | New — `/youtube add/remove/list` |
| `commands/CommandRegistrar.java` | Register `YoutubeMarkerCommand` |
