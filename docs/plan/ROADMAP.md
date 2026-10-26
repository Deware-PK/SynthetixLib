# Roadmap

Status: ☐ todo · ◐ in progress · ☑ done. Each phase is broken into task files in `tasks/`.

## Phase 0 — Harness ☑
Multi-module build, build-logic, version catalog, ArchUnit rules, smoke tests on 1.21.11 + 26.x,
CI, AGENTS.md/CLAUDE.md, ADRs. → `tasks/P0-01-harness.md`

## Phase 1 — API design (no code) ☑ core..dialog approved (npc dropped, ADR 0016)
One design doc per module in `docs/design/<module>.md`, written as **user-side code examples first**,
reviewed by the owner before implementation. Start with core. → `tasks/P1-01-core-api-design.md`

## Phase 2 — Modules (in order) ☑ core + config + command + gui + database + dialog done (P2-01..P2-36, 0.8.0)
| # | Module | Scope | Key "never again" |
|---|---|---|---|
| 1 ✅ | core | init, scheduler (global/region/entity/async), text (MiniMessage), ItemBuilder (data components, item_model), PDC utils | static state, getHolder(false) |
| 2 ✅ | config | codecs (records, sealed types, JSON), YAML files, per-object collections, locale, migrations | bug 6 |
| 3 ✅ | command | native Brigadier fluent API, arg types, cooldowns, locale messages → tasks P2-15..P2-18 | bug 7 |
| 4 ✅ | gui | GUI per viewer, layout from config (char pattern, custom-font title), dupe-proof container → tasks P2-19..P2-23 | bugs 2, 3, 5 + go-mcbots E2E |
| 5 ✅ | database | async DB, graceful shutdown; HikariCP loaded by consumer PluginLoader, Paper's drivers (ADR 0014) → tasks P2-24..P2-28 | bug 1 |
| 6 ✅ | dialog | Paper Dialog API wrapper: YAML dialogs, typed submit + server validation, registry dialogs at bootstrap, Geyser test → tasks P2-29..P2-33 | dupe-safe one-use buttons |
| ~~7~~ | ~~npc~~ | dropped (ADR 0016): packets/PacketEvents stay in the product (Farm Workers) | — |

## Phase 3 — Docs ☑ AI-first skill (docs/design/docs-skill.md, ADR 0015 JitPack), P3-01..P3-06, v0.7.0 on JitPack · evals 3× 10/10
0.8.0: eval gaps closed (number-field, inputRange, validate, reloadable cooldowns), upgrade fixes; evals 3× 10/10.

## Phase 4 — Road to 1.0.0 ☐ (draft 2026-10-25, owner to approve)
1.0.0 = a promise: no breaking change until 2.0 (SemVer), every module used by a real product, upgrade-safe for buyers.

| Version | Theme | Exit criteria |
|---|---|---|
| 0.9.0 | **Product gaps** (found while planning InteractiveAuction 2.0, shipped without waiting for it — owner 2026-10-26): P4-01 gui `actionAsync`, P4-02 number-field `decimals`, P4-03 `Database.standalone`, P4-04 `synthetixlib-selftest` + template `runServer`/`smokeTest`, P4-05 release | the four APIs tested (unit + smoke on 1.21.11 + 26.x), skill updated, JitPack `v0.9.0` green |
| 0.9.x | **Real use** — InteractiveAuction 2.0 built on the released lib (separate repo, uses the skill) | IA 2.0 playable on a test server; every further lib gap it hits fixed in the lib as a 0.9.x patch/minor (not worked around); `validateAsync`; Geyser table (P2-33) filled |
| 0.10.0 | **Hardening** — API audit + freeze | every public type reviewed (rename/remove mistakes now: last chance to break), `@ApiStatus.Experimental` only on dialog (Paper experimental), Javadoc on 100 % of public API, MySQL in CI (service container), load test with go-mcbots (100 bots: menus, dialogs, commands, DB), dupe/exploit review of gui + dialog + database, upgrade tests 0.7 → current |
| 1.0.0-rc.1 | **Freeze** | API baseline recorded, binary-compat check in `check` (build-time plugin, not shaded), 6 evals (3 current + upgrade, Folia, IA-style) ≥ 9/10, versioning/deprecation policy + 0.x → 1.0 migration notes in the skill; IA 2.0 runs on the RC for 1–2 weeks on a live server |
| 1.0.0 | **Release** | no blocker from the RC, GitHub release + JitPack, skill version line 1.0.0 |

After 1.0: deprecate one minor before removing; new Paper version → smoke matrix updated within one minor.

## Products using the lib
- InteractiveAuction 2.0 — needs core, config, command, gui, dialog, database, selftest. No 1.0 data migration (breaking change). Its 3D showcase uses PacketEvents in the product (ADR 0016), Vault + PlaceholderAPI as product dependencies. Plan: `InteractiveAuction2/docs/v2/IA2-PLAN.md`.
- Farm Workers — needs core, config, command, gui, database; NPCs with PacketEvents in its own code.
