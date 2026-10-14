# synthetixlib-core

Package: `com.github.dewarepk.synthetixlib.core` (only this module may use it).

Packages (all under the package above)
- `core` — `SynthetixLib` (init/close, exposes the services)
- `core.scheduler` — Folia-safe facade: global / region / entity / async
- `core.text` — MiniMessage `TextService`
- `core.item` — `Items` / `ItemBuilder` (item meta, `item_model`, no int custom model data)
- `core.pdc` — `PdcKeys` / `PdcKey` typed, plugin-namespaced keys

Rules specific to core
- Core must stay small and dependency-free (only paper-api). Anything needing HikariCP, PacketEvents,
  etc. belongs in its own module.
- The scheduler facade is the ONLY place allowed to touch `org.bukkit.scheduler` (ArchUnit enforces).
- Every new public type gets a self-test in synthetixlib-example if it touches the server.
