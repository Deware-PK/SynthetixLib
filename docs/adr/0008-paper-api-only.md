# 0008. Paper API only: no NMS, no reflection; packets via PacketEvents

> Amended by 0016: no NPC module, the library uses no packets at all.

- Status: Accepted
- Date: 2026-10-14

## Context
NMS/reflection breaks on every Minecraft update and makes 1.21.11 + 26.x support expensive.

## Decision
Library code uses Paper API only. Where packets are unavoidable (player NPCs), use PacketEvents as a
consumer-provided dependency, isolated in `synthetixlib-npc`.

## Consequences
ArchUnit forbids `net.minecraft..`, `org.bukkit.craftbukkit..`, `java.lang.reflect..`, `Class.forName`.
