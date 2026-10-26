# SynthetixLib — agent guide

Owner: Phakin (Deware, github.com/Deware-PK). **Talk to the owner in Thai, concise.**
SynthetixLib is a Paper plugin library (rewrite of the old SnapPlug) used to build plugins sold/free on
BuiltByBit, SpigotMC and Hangar (first products: InteractiveAuction 2.0, Farm Workers).

## Working agreement (read first)
1. **Task files with `Approved: yes` are pre-approved:** implement them directly, exactly as specified.
   Stop and ask only if the spec is ambiguous, contradicts a rule, or needs a public API change.
   Anything without an approved task: propose plan + affected files first and wait for "go".
2. **Never commit / push** unless explicitly told — **except** a task with `Ship: yes` in its header
   (the module's wrap-up task): when its Verify is green and its boxes are ticked, set `Status: review`,
   then run `powershell -ExecutionPolicy Bypass -File scripts/ship-module.ps1 -Module <m> -Version <x.y.z>`
   (values from the task header). It re-verifies, commits everything, tags `v<x.y.z>` and pushes `main`.
   If it aborts, do not commit by hand: report the reason and stop. Never use `--no-verify` / force-push.
3. Work from a task file in `docs/plan/tasks/`. If none fits, propose one (template in that folder).
   Approved design docs in `docs/design/` are the source of truth for public API.
4. A decision that changes a locked rule below → write an ADR in `docs/adr/` and get approval first.
5. Done = verification below passes + task acceptance criteria ticked. Report what you ran.
6. **Module wrap-up (`Ship: yes`) also updates the docs skill:** the module's reference in `skills/synthetixlib/references/`, the
  rules in `skills/synthetixlib/rules/`, and `skills/synthetixlib/CHANGELOG.md` (API breaks marked **Breaking**); `checkSnippets` stays green.

## Verify (run before saying "done")
| Command | What | When |
|---|---|---|
| `./gradlew check` | compile, unit tests, ArchUnit rules, forbidden patterns, shaded-jar relocation | always |
| `./gradlew smokeTest` | boots Paper 1.21.11 (Java 21) **and** 26.x (Java 25) headless, runs in-server self-tests | anything that touches server behaviour |
| `./gradlew publishToMavenLocal` | installs `0.x-SNAPSHOT` for local consumers | when a product needs it |

Manual play-testing: `./gradlew runServer` (1.21.11, port 25565) / `runServer26` (26.x, port 25566), connect to localhost.
Servers live in `run/` (git-ignored, safe to delete). Versions: `gradle/libs.versions.toml`.
Windows: use `gradlew.bat`.

## Locked decisions (details + reasons in docs/adr)
- **Paper only** (no Spigot). Min **Paper 1.21.11**, must run on **26.x**. Compile against `paper-api:1.21.11-R0.1-SNAPSHOT` only.
- **Java 21** toolchain + bytecode (26.x runs on Java 25 and loads 21 bytecode).
- **Distribution:** consumers **shade + relocate** SynthetixLib (one jar for buyers). Consequences:
  - no static/global state, no singletons — everything hangs off the instance from `SynthetixLib.init(plugin)`
  - no `Class.forName` / class names in strings / reflection
  - third-party runtime libs (HikariCP, drivers…) are NOT shaded: consumers load them with Paper `PluginLoader`
    (`paper-plugin.yml`); SynthetixLib will ship a helper for that
  - consumer packages must not start with `com.github.dewarepk.synthetixlib`
- **Entry:** plugin `extends JavaPlugin`, calls `SynthetixLib.init(this)` in onEnable, `close()` in onDisable. No base class.
- **Commands:** native **Brigadier** (Paper lifecycle `COMMANDS` event), one fluent API. Not `BasicCommand`.
- **Config:** typed (record/class ⇄ YAML, Okaeri-style), never string paths.
- **No NMS / CraftBukkit / reflection, no packets** (no PacketEvents; ADR 0016).
- **Folia-safe:** schedule only through the core scheduler facade (global / region / entity / async).
- **Text:** MiniMessage / Adventure only. No `ChatColor`, no `§`, no legacy serializer.
- **Items:** data components + `item_model`. No int custom model data. **PDC** for item/entity data.
- **Inventories:** `getHolder(false)` only.
- Package `com.github.dewarepk.synthetixlib.<module>`; artifact `synthetixlib-<module>`.

## Never again (SnapPlug bugs — each needs a test when the module is built)
1. DB shutdown must `awaitTermination` pending writes before closing the pool.
2. GUI: block `COLLECT_TO_CURSOR` (double-click) from pulling items out of locked slots.
3. GUI: shift-click must target only editable slots (Bukkit picks the first empty slot otherwise).
4. No static per-player maps; per-player state is cleaned on quit and thread-safe.
5. One GUI definition → one view **per viewer** (no single shared viewer field).
6. Config: keep comments on default copy; do not re-save on every reload.
7. Commands: no shared mutable state (Folia races); report the alias actually used; all messages via locale.
8. `getHolder(false)`, no `setCustomModelData(int)`.

Rules marked enforceable are checked by `synthetixlib-archtest` (ArchUnit) and `checkForbiddenPatterns`.
**Never weaken a rule to get green.**

## Layout
```
build-logic/              convention plugins: synthetix.java-base | synthetix.library | synthetix.paper-plugin
gradle/libs.versions.toml all versions
synthetixlib-core/        init, scheduler, text, ItemBuilder, utils         (published)
synthetixlib-example/     example plugin = smoke-test bed + docs examples   (not published)
synthetixlib-selftest/   in-server self-test kit for products              (published)
synthetixlib-archtest/    ArchUnit rules over every library module           (not published)
docs/adr/                 decisions        docs/plan/  roadmap + tasks        docs/design/  API design per module
```
Modules: core, config, command, gui, database, dialog (complete; no npc module, ADR 0016), selftest (0.9.0). See docs/plan/ROADMAP.md.

## Adding a library module (checklist)
1. `synthetixlib-<m>/build.gradle.kts` with `id("synthetix.library")`; `include(...)` in settings.gradle.kts.
2. Package `com.github.dewarepk.synthetixlib.<m>`; module `AGENTS.md` + `CLAUDE.md` (`@AGENTS.md`).
3. Add it to `synthetixlib-archtest` and `synthetixlib-example` dependencies.
4. Add `<M>SelfTests` in the example and register it in `SelfTestTrigger`.
5. `./gradlew check smokeTest` green.

## Style
- Java 21: records, sealed types, `var` sparingly, `final` params/locals in library code.
- Public API: Javadoc with a usage snippet; nullness explicit; no checked exceptions leaking from fluent APIs.
- Small, focused classes; package-private by default; only intended API is `public`.
- Logging through the plugin logger; never `System.out`.

## Docs snippets
Code in `skills/` is copied from real sources, never hand-written. Mark a region in `synthetixlib-example/src/**` or
`skills/synthetixlib/templates/**`: `// @snippet:<name>` … `// @end` (Java/Kotlin) or `# @snippet:<name>` … `# @end` (YAML,
properties). Names `[a-z0-9-]+`, unique repo-wide, no nesting; markers are comments, the block is the lines between,
de-indented. In markdown under `skills/` use a fence ` ```java snippet=<name> ` (or `kotlin`, `yaml`). `gradlew syncSnippets`
rewrites the blocks; `checkSnippets` (part of `check`) fails on a stale, unknown, duplicate or unclosed one.
