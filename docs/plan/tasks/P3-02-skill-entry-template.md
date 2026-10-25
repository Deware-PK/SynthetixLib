# P3-02 SKILL.md, setup reference, consumer template, new-plugin script, jitpack.yml
Status: done · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-skill (docs, not a Gradle module) · Depends on: P3-01 · Design: docs-skill.md §1, §2, §6 · ADR 0015

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/docs-skill.md, docs/adr/0015-jitpack-from-0x.md and docs/plan/tasks/P3-02-skill-entry-template.md,
then implement the task. Run `gradlew.bat check publishToMavenLocal` and the template build below, tick the boxes,
fill in "Result", set Status: review. Do not commit.

## Spec
- `skills/synthetixlib/templates/consumer-plugin/`: smallest plugin that uses core + config + command + gui + dialog
  (database optional, commented): Gradle Kotlin DSL + wrapper, Java 21, shadow with relocate of
  `com.github.dewarepk.synthetixlib` → `<package>.lib.synthetixlib`, paper-plugin.yml (`loader`, `bootstrapper`
  optional), PluginLoader class, plugin class (`init` in onEnable, `close` in onDisable), one typed config, one
  command, one menu file, one dialog file, lang en_US. Package `com.example.myplugin`. Repositories: jitpack +
  papermc; a Gradle property `synthetixLocal=true` switches to mavenLocal() + groupId `com.github.dewarepk`
  (lib snapshots). Template AGENTS.md + CLAUDE.md (`@AGENTS.md`): "use the synthetixlib skill", the rules list.
  Snippet markers in the template for the reference files.
- `scripts/new-plugin.ps1` + `.sh`: copy the template to a target folder, replace package, plugin name, id; refuse a
  package starting with `com.github.dewarepk.synthetixlib`. Strips `@snippet:` / `@end` marker lines from the copy
  (carry-over from P3-01 review).
- `SKILL.md` per docs-skill.md §2 (frontmatter name/description, setup, rules, module map, "read which reference when",
  version line `0.7.0`, "check the lib version in the build file; if older, read CHANGELOG.md"). < 300 lines.
- `references/setup.md`: JitPack coordinates `com.github.Deware-PK.SynthetixLib:synthetixlib-<module>:v<x.y.z>`,
  mavenLocal for snapshots, shade + relocate, PluginLoader (HikariCP), bootstrap, init/close, folia-supported.
- `jitpack.yml` at repo root (ADR 0015): `jdk: [openjdk21]`, install `./gradlew publishToMavenLocal -x test`.
  Make sure the build does not need the local JDK paths or MySQL (CI already runs `check` without them).
- Ship script: after the Gradle verify, build the template once with `-PsynthetixLocal=true` (fail = abort), and
  rewrite the SKILL.md version line to the shipped version.

## Acceptance criteria
- [x] `templates/consumer-plugin` builds with `-PsynthetixLocal=true` against `publishToMavenLocal`; shaded jar has
      the lib relocated (list 3 class paths in Result)
- [x] `new-plugin` makes a project that builds (both scripts tried or the .sh reviewed)
- [x] `gradlew.bat check` green (snippets in sync)

## Verify
gradlew.bat check publishToMavenLocal
cd skills/synthetixlib/templates/consumer-plugin && gradlew.bat build -PsynthetixLocal=true

## Result
Template `skills/synthetixlib/templates/consumer-plugin/` (core+config+command+gui+dialog; database commented): Kotlin DSL + wrapper, `release 21`, shadow 9.6.1 relocating to `<group>.lib.synthetixlib`, `verifyRelocation` in `check`, `paper-plugin.yml` with `loader` (bootstrapper commented), typed `Settings`/`Messages` (en_US), `/myplugin menu|greet|reload`, `menus/main.yml`, `dialogs/greet.yml`, AGENTS.md + CLAUDE.md. `-PsynthetixLocal=true` -> mavenLocal + `com.github.dewarepk` + `synthetixLocalVersion`; otherwise JitPack `com.github.Deware-PK.SynthetixLib` + `synthetixVersion=v0.7.0`. Snippet markers: `template-*` (used in `references/setup.md` and `SKILL.md`).
Shaded jar (3 of 323 relocated classes): `com/example/myplugin/lib/synthetixlib/core/SynthetixLib.class`, `.../config/Configs.class`, `.../gui/GuiService.class`; 0 entries under `com/github/dewarepk`. Booted the template jar on Paper 1.21.11 (dropped into `run/smoke-1.21.11/plugins`, removed after): enables cleanly, writes config.yml, lang/en_US.yml, menus/main.yml, dialogs/greet.yml, no errors.
`new-plugin.ps1` and `.sh`: both tried (`com.acme.auction`/Auction, `org.shop.mine`/ShopMine/shop); both projects build with `-PsynthetixLocal=true`; both refuse `com.github.dewarepk.synthetixlib.*`; no `@snippet`/`@end`/`myplugin` left in the copies.
`SKILL.md` (~110 lines, version line `Written for SynthetixLib 0.7.0.`), `references/setup.md`, `scripts/README.md`, `jitpack.yml` (`openjdk21`, `chmod +x gradlew`, `publishToMavenLocal -x test`; verified with the local JDK paths overridden). `scripts/ship-module.ps1` step 3b: rewrites the SKILL.md version line and `synthetixVersion=v<x.y.z>` in the template, then builds the template with `-PsynthetixLocal=true -PsynthetixLocalVersion=<root version>` (abort on failure); the regex was tested, the full ship run was not (no ship now).
Extra (needed): `Snippets.kt` accepts ` ```kotlin snippet=` fences (build files are `.kts`) and skips `build/`, `.gradle/`, `.kotlin/` dirs (the template's own build output held copies of the markers -> duplicate error); 2 new unit tests; AGENTS.md "Docs snippets" mentions `kotlin`.
Ran: `gradlew.bat check publishToMavenLocal` green, template `gradlew.bat build -PsynthetixLocal=true` green, `gradlew.bat check smokeTest` green (84/84 on 1.21.11 and 26.3). Not committed.
Note: `synthetixLocalVersion=0.6.0-SNAPSHOT` in the template's `gradle.properties` must match the root `version=` when a manual local build is done (the ship script passes it explicitly); bump it with the root version.

## Review (planner, 2026-10-25)
Approved. Template builds, jar relocated (no com/github/dewarepk left), enabled on Paper 1.21.11; both new-plugin scripts
build and strip markers. SKILL.md checked against code (escape, opener/link, locale builder exist; HikariCP 7.1.0 = catalog).
Accepted extras: kotlin snippet fences, build/.gradle/.kotlin skipped, template .gitignore/README/AGENTS/CLAUDE.
Note for P3-06: set root version 0.7.0-SNAPSHOT and template synthetixLocalVersion together; ship-script step untested
until that ship.
