# Wilderness Block Log — Feature Plan

Track who places blocks in unclaimed chunks so staff can identify griefers even after the chunk has been reset with WorldEdit.

---

## Problem

Chunk resets are trivial (`//chunk` + `//regen`), but they destroy the evidence. Staff need to know *who* placed the lava, dumped dirt, built the eyesore, or grief-logged a forest **before** hitting reset — or after, since the log is separate from the world.

---

## Scope

- **Only wilderness** (unclaimed chunks). Claimed chunks are already protected; owners handle their own land.
- **All block placements** by players. No block-type whitelist/blacklist — logging everything keeps it simple and catches vectors we haven't thought of.
- **Read-only for staff.** No automatic punishment — just identification. Staff use existing tools (`/punish`, bans) after identifying the griefer.

---

## Log Entry

```java
record LogEntry(
    UUID playerUuid,
    String playerName,       // snapshot at time of placement
    BlockPos pos,
    ResourceKey<Level> dim,
    String block,            // Registry name, e.g. "minecraft:lava"
    long timestamp           // System.currentTimeMillis()
) {}
```

---

## Storage — `WildernessLogData extends SavedData`

Stored in overworld `DataStorage` (`quacksmp_wilderness_log`).

- Backing structure: `ArrayList<LogEntry>` serialized via Codec.
- **Pruning on age**: entries older than `wilderness_log_days` (default 30) are pruned on server start and on `/smp reload`. Keeps the file from growing unbounded.
- **Pruning on claim**: when a chunk is successfully claimed (`ClaimService.claim` returns `SUCCESS`), all log entries for that chunk + dimension are deleted. A player who builds in the wild and then claims the land is doing nothing wrong — their history is irrelevant and should not show up in staff queries.
- No per-chunk index needed — staff queries are infrequent enough that a linear scan over 30 days of log is fine. Add an index later if it becomes slow.

---

## Event Hook

Subscribe to the block place event on both platforms:

- **NeoForge**: `BlockEvent.EntityPlaceEvent`
- **Fabric**: `PlayerBlockBreakEvents` equivalent for placement — the block place callback in `UseBlockCallback` or the multiblock place event

Before logging, check `ClaimService.getOwner(level, new ChunkPos(pos))` — if the chunk is claimed, skip. If unclaimed, append a `LogEntry` and mark dirty.

---

## Staff Query — Inspect Mode

### Toggle command

```
/smp inspect
```

OP-only. Toggles inspect mode on/off for the executing player. Chat confirms: `"Inspect mode ON — right-click any block to look up its log."` / `"Inspect mode OFF."`. Active players tracked in a server-side `Set<UUID>` (in-memory only, not persisted).

### Interaction

While inspect mode is active, right-clicking any block **cancels the normal interaction** and instead queries `WildernessLogData` for the most recent entries at that position + dimension. Results printed to the querying staff member only (not broadcast).

Output format (chat, most recent first):

```
[Wilderness Log] minecraft:lava at (123, 64, -456) — Overworld
  » PlayerName  2026-03-15 21:04:17
  » PlayerName  2026-03-14 09:11:02
  (2 entries)
```

If no entries: `"No log entries for this position."`

If the chunk is claimed: `"This chunk is claimed — log only covers wilderness."`

### Hook

- **NeoForge**: `PlayerInteractEvent.RightClickBlock` — check if player is in inspect set, cancel event, run query.
- **Fabric**: `UseBlockCallback` — same logic.

---

## Configuration

```json
"wilderness_log_days": 30
```

Added to `SmpConfig` and `ConfigIO`. Hot-reloadable (triggers a prune pass on reload).

---

## Open Questions

1. **Query depth**: Show all entries for a position, or cap at last N (e.g. 10)? Probably cap at 10 to avoid chat spam on heavily-placed blocks.
2. **Item in hand restriction**: Should inspect mode only trigger when holding a specific item (e.g. a named stick), or always when the mode is toggled? Toggle-only is simpler.
3. **Dimension coverage**: Log all dimensions (Overworld, Nether, End) or Overworld only? All dimensions makes sense — Nether wilderness grief (lava oceans, withers) is real.

---

## Files to Create / Modify

| File | Change |
|---|---|
| `wilderness/WildernessLogData.java` | New — SavedData with LogEntry list, pruning logic, position query |
| `wilderness/WildernessLogListener.java` | New — block place event handler, inspect-mode right-click handler |
| `config/SmpConfig.java` | Add `WILDERNESS_LOG_DAYS` |
| `config/ConfigIO.java` | Serialize/deserialize `wilderness_log_days` |
| `commands/CommandRegistrar.java` | Register `/smp inspect` subcommand |
| `fabric/SmpUtilsModFabric.java` | Register block place + right-click callbacks |
| `neoforge/SmpEvents.java` | Subscribe to `BlockEvent.EntityPlaceEvent` + `PlayerInteractEvent.RightClickBlock` |
