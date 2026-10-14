# synthetixlib-core

Package: `com.github.dewarepk.synthetixlib.core` (only this module may use it).

Scope (planned, see docs/plan/ROADMAP.md): `SynthetixLib.init(plugin)`, scheduler facade
(global / region / entity / async, Folia-safe), MiniMessage text helpers, ItemBuilder on data
components (`item_model`, no custom model data int), small utils.

Rules specific to core
- Core must stay small and dependency-free (only paper-api). Anything needing HikariCP, PacketEvents,
  etc. belongs in its own module.
- The scheduler facade is the ONLY place allowed to touch `org.bukkit.scheduler` (ArchUnit enforces).
- Every new public type gets a self-test in synthetixlib-example if it touches the server.
