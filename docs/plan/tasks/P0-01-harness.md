# P0-01 Harness
Status: done
Module: (build)

## Goal
An agent can build, check rules and prove behaviour on real Paper 1.21.11 + 26.x with one command each.

## Acceptance criteria
- [x] settings: modules core / example / archtest; repos centralised (PaperMC + Central)
- [x] `build-logic` conventions: `synthetix.java-base`, `synthetix.library`, `synthetix.paper-plugin`
- [x] `gradle/libs.versions.toml` holds every version
- [x] Java 21 toolchain, daemon on 21 (`gradle/gradle-daemon-jvm.properties`), local JDK paths in gradle.properties
- [x] ArchUnit rules (`synthetixlib-archtest`) + `checkForbiddenPatterns`
- [x] example: shaded + relocated, `verifyRelocation`, self-test runner (`core.init`, `core.relocated`)
- [x] run-paper: `runServer` (1.21.11/Java 21), `runServer26` (26.x/Java 25), `smokeTest`
- [x] AGENTS.md + CLAUDE.md (root + per module), ADR 0001–0011, roadmap
- [x] GitHub Actions CI (check + smoke)
- [x] **owner:** delete empty root `src/` folder left from the IntelliJ template
- [x] **owner/agent:** `gradlew.bat check` green on Windows (first build; was written without a local Gradle run)
- [x] **owner/agent:** `gradlew.bat smokeTest` green (downloads Paper 1.21.11 + 26.3 on first run)
- [x] **owner:** IntelliJ → Settings → Build Tools → Gradle → Gradle JVM = 21 (or "Project SDK" 21), then reload
- [x] fix anything the first real build reports, then Status: done (2026-10-14: check + smokeTest PASS on 1.21.11 and 26.3)

## Verify
gradlew.bat check
gradlew.bat smokeTest

## Notes
- Gradle wrapper 9.0.0 → 9.8.1: Shadow 9.6.1 is built with Kotlin 2.4 metadata, Gradle 9.0 embeds Kotlin 2.2
  (`build-logic:compilePluginsBlocks` failed). Shadow ≥9.5 also needs Gradle ≥9.2. Bump Gradle and Shadow together.
- Unverified assumptions to watch on first build: run-paper 3.1.0 API (`disablePluginJarDetection`,
  `runDirectory`, `pluginJars`), Shadow 9.6.1 `ShadowJar` package, Paper 26.3 availability in run-paper.
- go-mcbots E2E comes with the GUI module (P2-gui).
