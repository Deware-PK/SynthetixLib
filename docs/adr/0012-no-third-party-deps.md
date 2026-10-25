# 0012. No third-party runtime dependencies (except PacketEvents)

> Amended by 0016: the PacketEvents exception is removed (no NPC module). Only HikariCP remains, runtime-loaded (0014).

- Status: Accepted
- Date: 2026-10-14

## Context
Every external library adds shading/relocation work, version conflicts between products, and code we
do not control. Paper already ships what most plugins need (Adventure/MiniMessage, SnakeYAML via
Bukkit's `YamlConfiguration`, Brigadier, Gson, Guava, SLF4J-free logging via plugin logger).

## Decision
SynthetixLib modules depend only on paper-api (and what the Paper server itself ships).
Single allowed exception: **PacketEvents**, only in `synthetixlib-npc`, provided by the server/consumer.
Config, locale, commands, GUI etc. are written in-house.

## Consequences
- Config uses Bukkit `YamlConfiguration` (SnakeYAML inside Paper) — not our own YAML parser.
- `synthetixlib-database` uses HikariCP loaded by the consumer's PluginLoader (not shaded) — decided in ADR 0014.
- Test-only dependencies (JUnit, ArchUnit) are fine; they never reach a jar.
