# 0014. Database pool: HikariCP loaded by the consumer's PluginLoader

- Status: Accepted
- Date: 2026-10-19
- Resolves: the open point in ADR 0012 (database pool)

## Context
ADR 0012 bans third-party runtime deps but left the database pool open: write a small pool in-house,
or use HikariCP loaded at runtime (ADR 0009). A correct pool (leak detection, validation, timeouts,
graceful shutdown) is a lot of tricky code for no product benefit; HikariCP is the de-facto standard.

## Decision
`synthetixlib-database` uses **HikariCP**, declared `compileOnly` (never shaded, never relocated).
The consumer plugin loads it, plus its JDBC driver, through its Paper `PluginLoader` +
`MavenLibraryResolver` (ADR 0009). SynthetixLib provides a helper so the loader adds the database
module's libs (HikariCP + chosen driver) in one call.

## Consequences
- Second allowed exception to ADR 0012 (after PacketEvents): HikariCP, database module only, runtime-loaded.
- Products using the database module must be Paper plugins (`paper-plugin.yml` + loader); first start downloads libs.
- Versions of HikariCP/drivers live in the version catalog and in the loader helper; keep them in sync.
- No in-house pool to write or maintain.
