# P3-02 SKILL.md, setup reference, consumer template, new-plugin script, jitpack.yml
Status: todo · Approved: yes · Model: sonnet · Effort: high
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
- [ ] `templates/consumer-plugin` builds with `-PsynthetixLocal=true` against `publishToMavenLocal`; shaded jar has
      the lib relocated (list 3 class paths in Result)
- [ ] `new-plugin` makes a project that builds (both scripts tried or the .sh reviewed)
- [ ] `gradlew.bat check` green (snippets in sync)

## Verify
gradlew.bat check publishToMavenLocal
cd skills/synthetixlib/templates/consumer-plugin && gradlew.bat build -PsynthetixLocal=true

## Result
(agent fills in)
