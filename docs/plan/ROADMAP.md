# Roadmap

Status: ☐ todo · ◐ in progress · ☑ done. Each phase is broken into task files in `tasks/`.

## Phase 0 — Harness ☑
Multi-module build, build-logic, version catalog, ArchUnit rules, smoke tests on 1.21.11 + 26.x,
CI, AGENTS.md/CLAUDE.md, ADRs. → `tasks/P0-01-harness.md`

## Phase 1 — API design (no code) ◐ core + config + command approved; gui approved; next: dialog (after gui is built)
One design doc per module in `docs/design/<module>.md`, written as **user-side code examples first**,
reviewed by the owner before implementation. Start with core. → `tasks/P1-01-core-api-design.md`

## Phase 2 — Modules (in order) ◐ core + config + command + gui done (P2-01..P2-23) · next: database
| # | Module | Scope | Key "never again" |
|---|---|---|---|
| 1 ✅ | core | init, scheduler (global/region/entity/async), text (MiniMessage), ItemBuilder (data components, item_model), PDC utils | static state, getHolder(false) |
| 2 ✅ | config | codecs (records, sealed types, JSON), YAML files, per-object collections, locale, migrations | bug 6 |
| 3 ✅ | command | native Brigadier fluent API, arg types, cooldowns, locale messages → tasks P2-15..P2-18 | bug 7 |
| 4 ✅ | gui | GUI per viewer, layout from config (char pattern, custom-font title), dupe-proof container → tasks P2-19..P2-23 | bugs 2, 3, 5 + go-mcbots E2E |
| 5 | database | async DB, graceful shutdown; HikariCP loaded by consumer PluginLoader (ADR 0014) | bug 1 |
| 6 | dialog | Paper Dialog API wrapper; registry dialogs at bootstrap; Geyser test | — |
| 7 | npc | PacketEvents player NPC (starts with Farm Workers prototype) | — |

## Phase 3 — Docs ☐
`docs/` site-style guides + `llms.txt` + runnable examples from `synthetixlib-example`, written after the API is stable.

## Products using the lib
- InteractiveAuction 2.0 — needs core, config, command, gui, database (+ migration from 1.0 data).
- Farm Workers — needs core, config, command, gui, database, npc.
