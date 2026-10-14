# synthetixlib-archtest

Not published. ArchUnit rules over ALL library modules (`ArchitectureTest`). Runs in `./gradlew check`.

- When you add a library module, add `testImplementation(project(":synthetixlib-<m>"))` to build.gradle.kts.
- Never weaken or delete a rule to get green. Fix the code. If a rule is truly wrong, propose an ADR first.
- String-level checks (e.g. legacy `§`) live in the `checkForbiddenPatterns` Gradle task (build-logic).
