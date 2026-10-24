# 0015. JitPack from 0.x, version-matched docs shipped as an artifact

- Status: Proposed
- Date: 2026-10-25
- Amends: 0011

## Context
The owner wants products (IA 2.0) and other developers to consume the lib without `publishToMavenLocal`, and wants
Next.js-style docs: the docs an agent reads must match the exact version installed (Next ships them in
`node_modules/next/dist/docs/`). Java has no node_modules: dependency jars live in the Gradle cache, which agents do not
browse.

## Decision
- **JitPack now (0.x), not only at 1.0.0.** One git tag builds the whole multi-module repo once; every published module
  becomes `com.github.Deware-PK.SynthetixLib:synthetixlib-<module>:<tag>`. Modules are not released one by one.
  `jitpack.yml` pins JDK 21 and runs `./gradlew publishToMavenLocal -x test -x smokeTest` (tests already ran in the ship
  script; MySQL / Paper servers are not available on JitPack).
- **Docs artifact:** new module `synthetixlib-docs` (published, no code) packs `skills/synthetixlib/**` into a jar at
  `META-INF/synthetixlib/skill/`. The consumer template applies a tiny Gradle task `syncLibDocs` (runs before
  `compileJava`) that unpacks the docs of the resolved version into `.synthetixlib/docs/` (git-ignored). The template's
  `AGENTS.md` / `CLAUDE.md` say: "Before writing code, read `.synthetixlib/docs/SKILL.md`; it matches the installed version."
- The repo copy (`skills/`, marketplace, `llms.txt`) stays for agents that start before a project exists.

## Consequences
- Tags must stay `v<x.y.z>` (ship script); consumers write the tag as the version (`v0.6.0`).
- groupId differs between mavenLocal (`com.github.dewarepk`) and JitPack (`com.github.Deware-PK.SynthetixLib`); the
  template uses JitPack, docs say which to use for local snapshots.
- API may still break before 1.0.0; the version line in SKILL.md and per-version eval results make that visible.
