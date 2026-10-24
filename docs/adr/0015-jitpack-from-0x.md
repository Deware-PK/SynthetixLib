# 0015. JitPack from 0.x

- Status: Accepted (owner 2026-10-25)
- Date: 2026-10-25
- Amends: 0011

## Context
Products (IA 2.0) and other developers should consume the lib without `publishToMavenLocal`. Docs are distributed as
an Agent Skill from this repo (docs/design/docs-skill.md §6), not inside an artifact.

## Decision
- JitPack now (0.x), not only at 1.0.0. One git tag builds the whole multi-module repo once; every published module
  becomes `com.github.Deware-PK.SynthetixLib:synthetixlib-<module>:<tag>`. Modules are never released one by one.
- `jitpack.yml` pins JDK 21 and runs `./gradlew publishToMavenLocal -x test` (tests already ran in the ship script; MySQL
  and Paper servers are not available on JitPack).

## Consequences
- Tags stay `v<x.y.z>` (ship script); consumers write the tag as the version (`v0.7.0`).
- groupId differs between mavenLocal (`com.github.dewarepk`) and JitPack (`com.github.Deware-PK.SynthetixLib`); the
  template uses JitPack.
- API may still break before 1.0.0; skill CHANGELOG + version line make it visible.
