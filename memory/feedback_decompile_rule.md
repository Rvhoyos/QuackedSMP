---
name: Always ask user to decompile — never search for sources
description: Must ask user to decompile vanilla/NeoForge/Fabric classes instead of searching Gradle caches or filesystem
type: feedback
---

When implementing features that require knowledge of vanilla Minecraft, NeoForge, or Fabric internals, ALWAYS ask the user to decompile the relevant class first. NEVER attempt to find sources by searching Gradle caches, ~/.gradle, or any other filesystem location — the user has a decompiler and that is the only valid source.

Frequently decompiled classes may be cached in `.claude-decompiled/` at the repo root — check there first before asking the user.

The user was frustrated by wasted time when Claude searched Gradle caches instead of immediately asking for decompilation. This is explicitly documented in CLAUDE.md under "Working with Minecraft/NeoForge Internals".
