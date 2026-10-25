# 0016. No NPC module: packets stay out of the library

- Status: Accepted (owner 2026-10-25)
- Date: 2026-10-25
- Amends: 0008, 0012

## Context
The planned `synthetixlib-npc` needed packets, i.e. PacketEvents, the only exception left in ADR 0012. The owner wants
the library to depend on nothing outside Paper (HikariCP stays a runtime-loaded exception, ADR 0014).

## Decision
- No `synthetixlib-npc`. The library uses Paper API only, **no packets**, no PacketEvents.
- Products that need packet NPCs (Farm Workers) use PacketEvents themselves, in their own code. The library can still
  help them through Paper API (scheduler `entity(...)`, PDC, config, gui, dialog).
- Roadmap Phase 2 is complete with six modules: core, config, command, gui, database, dialog.

## Consequences
- ADR 0008: "packets via PacketEvents" no longer applies to the library. ADR 0012: no PacketEvents exception.
- AGENTS.md locked decisions, ROADMAP, README, HANDOFF updated. If a future module ever needs packets, it needs a new ADR.
