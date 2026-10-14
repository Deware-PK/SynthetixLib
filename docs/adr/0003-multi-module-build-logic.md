# 0003. Gradle multi-module + build-logic + version catalog

- Status: Accepted
- Date: 2026-10-14

## Context
Consumers should pull only what they use (e.g. no HikariCP glue in a plugin without a DB).
SnapPlug was one module and grew tangled.

## Decision
One Gradle module per feature (`synthetixlib-<m>`). Shared build config as convention plugins in
`build-logic/` (`synthetix.java-base`, `synthetix.library`, `synthetix.paper-plugin`); all versions in
`gradle/libs.versions.toml`. No `subprojects {}` / `allprojects {}` blocks.

## Consequences
New module = small `build.gradle.kts` applying a convention (checklist in AGENTS.md).
Module dependency direction: everything may depend on core; no cycles; core depends only on paper-api.
