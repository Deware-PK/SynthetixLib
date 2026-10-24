# 0011. Publishing: mavenLocal until 1.0.0, then JitPack

- Status: Accepted, amended by 0015 (JitPack from 0.x)
- Date: 2026-10-14

## Decision
Versions `0.x.y-SNAPSHOT` are published only with `./gradlew publishToMavenLocal`. From 1.0.0 the
GitHub repo is consumed via JitPack (tags). API may break freely before 1.0.0.

## Consequences
Products use `mavenLocal()` during 0.x. A `jitpack.yml` (JDK 21) is added at 1.0.0.
