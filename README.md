# SynthetixLib

A library for [Paper](https://papermc.io) plugins (1.21.11 to 26.x, Java 21, Folia-safe): typed YAML config and language
files, native Brigadier commands, YAML-defined inventory menus, native dialogs and async SQL (SQLite/MySQL).
You shade and relocate it into your plugin, so your buyers install one jar.

## Use it with an AI coding agent

The repo ships an agent skill ([`skills/synthetixlib`](skills/synthetixlib/SKILL.md)) with the API references, the
rules and a consumer template that builds as is.

```
npx skills add Deware-PK/SynthetixLib          # Claude Code, Codex, Cursor, ...
```

Claude Code plugin: `/plugin marketplace add Deware-PK/SynthetixLib`, then `/plugin install synthetixlib@synthetixlib`.
Other agents: point them at [`llms.txt`](llms.txt).

## Dependency (JitPack)

```kotlin
repositories { maven("https://jitpack.io") }
dependencies {
    implementation("com.github.Deware-PK.SynthetixLib:synthetixlib-core:v0.7.0")
    // also: synthetixlib-config, -command, -gui, -dialog, -database
}
```

Setup (shade + relocate, `paper-plugin.yml` loader) is in [`references/setup.md`](skills/synthetixlib/references/setup.md).
Start at [`SKILL.md`](skills/synthetixlib/SKILL.md); release notes are in [`CHANGELOG.md`](skills/synthetixlib/CHANGELOG.md).

## Working on the library

See [`AGENTS.md`](AGENTS.md). `./gradlew check` (unit tests, ArchUnit, snippets), `./gradlew smokeTest` (boots Paper 1.21.11 and 26.x).
