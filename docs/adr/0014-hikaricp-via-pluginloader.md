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
The consumer plugin loads it through its Paper `PluginLoader` +
`MavenLibraryResolver` (ADR 0009). SynthetixLib provides a helper so the loader adds it in one call.

**JDBC drivers are not downloaded** (amended 2026-10-20): Paper ships `org.xerial:sqlite-jdbc` (3.49.1.0) and
`com.mysql:mysql-connector-j` (9.2.0) on the server classpath (seen in `libraries/` of 1.21.11 and 26.3). The database
module uses those and checks at startup with `DriverManager.getDriver(url)` (no reflection); a missing driver is a
clear error naming the jar to add. HikariCP is **not** shipped by Paper, so it is the only library the loader fetches.

## Consequences
- Second allowed exception to ADR 0012 (after PacketEvents): HikariCP, database module only, runtime-loaded.
- Products using the database module must be Paper plugins (`paper-plugin.yml` + loader); first start downloads libs.
- HikariCP version lives only in the version catalog; the build generates the constant the loader helper uses.
- Driver versions follow the Paper build; if Paper ever drops them, the loader helper gets an opt-in to fetch them.
- No in-house pool to write or maintain.
