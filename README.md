# SynthetixLib

**A Paper plugin library built for AI coding agents.**

Tell your agent what the plugin should do in plain language. With SynthetixLib installed as a skill, it builds a
plugin that compiles, keeps to the rules, and lets server owners change every text, menu and window without touching code.

```
npx skills add Deware-PK/SynthetixLib
```

> "Make me an auction plugin: /sell asks for a price in a window, /auction shows a paged menu of lots, money in SQLite,
> English and Thai texts, nothing can be duplicated even if two players click at once."

That prompt is one of our evals. A fresh Claude Code session (Sonnet, medium effort) with only the skill produced a
working plugin that scored **10/10** on the review checklist ([results](skills/synthetixlib/evals/RESULTS.md)).

---

## Why "AI-driven"

Most libraries are written for humans, and agents guess their API from names. SynthetixLib is designed for the agent
first:

| | How it works |
|---|---|
| **Ships its own skill** | [`skills/synthetixlib`](skills/synthetixlib/SKILL.md): a short entry file, one reference per module, 37 rules (each with ❌ incorrect / ✅ correct code), recipes, a troubleshooting table and a template project that builds. Works with Claude Code, Codex, Cursor and others. |
| **Docs cannot drift** | Every code sample in the skill is copied from real, compiled code (`// @snippet` markers). `gradlew check` fails if a sample and its source differ. |
| **Measured, not hoped** | An [eval suite](skills/synthetixlib/evals) gives a fresh agent a real plugin task and scores the result. Every gap it finds becomes a doc fix or an API change. |
| **Mistakes fail early** | Locked rules (no static state, Folia-safe scheduling, MiniMessage only, no NMS/reflection, …) are checked by ArchUnit in the library and by the skill's rules. Wrong config, unknown actions and bad inputs are reported at load with file and key. |
| **Code for behaviour, YAML for everything else** | Menus and dialogs are YAML files the server owner edits; code only registers what they refer to by name. An agent writes small, focused classes, and buyers customise the plugin without asking for code changes. |
| **Safe by default** | Dupe-proof menus (deny-by-default clicks, atomic input slots), dialogs re-validated on the server with one-use buttons, database writes that finish before shutdown. The agent gets safety without having to know every Minecraft exploit. |

The library itself is built the same way: an AI planner writes the design docs, ADRs and task files, an AI executor
implements them, and every module ships only after unit tests, architecture rules and an in-server smoke test pass on
Paper 1.21.11 and 26.x. See [`docs/`](docs) and [`AGENTS.md`](AGENTS.md).

## What's inside

| Module | For |
|---|---|
| `synthetixlib-core` | `init` / `close`, Folia-safe scheduler (global, region, entity, async), MiniMessage text, `ItemBuilder` (data components, `item_model`), typed PDC keys |
| `synthetixlib-config` | Typed YAML config from records, one-file-per-object collections, per-player language files, comments kept, hot reload, migrations |
| `synthetixlib-command` | Native Brigadier commands with a fluent API: subcommands, typed arguments, cooldowns, async handlers, locale messages |
| `synthetixlib-gui` | Inventory menus defined in YAML: pages, lists, states, input slots, one view per viewer, dupe-proof clicks |
| `synthetixlib-dialog` | Native Paper dialogs defined in YAML: forms with typed, server-validated submit, confirmations, pause-menu dialogs |
| `synthetixlib-database` | Async SQLite / MySQL, migrations, transactions, copy between databases, shutdown that never loses a write |

Paper only, 1.21.11 to 26.x, Java 21, Folia-safe. No third-party runtime dependencies (HikariCP is loaded by your
plugin's `PluginLoader`, never shaded).

## Use it

**With an agent (recommended).** Install the skill in your project folder and describe the plugin:

```
npx skills add Deware-PK/SynthetixLib
```

Claude Code also works as a plugin: `/plugin marketplace add Deware-PK/SynthetixLib`, then
`/plugin install synthetixlib@synthetixlib`. Other agents can read [`llms.txt`](llms.txt).

**By hand.** Start from the template with
[`scripts/new-plugin`](skills/synthetixlib/scripts/README.md), then read [`SKILL.md`](skills/synthetixlib/SKILL.md)
and the reference of each module you use.

**Dependency** (JitPack, one tag releases every module):

```kotlin
repositories { maven("https://jitpack.io") }
dependencies {
    implementation("com.github.Deware-PK.SynthetixLib:synthetixlib-core:v0.8.0")
    // add the modules you use: synthetixlib-config, -command, -gui, -dialog, -database
}
```

You shade and relocate the library into your plugin, so your users install one jar. Setup details:
[`references/setup.md`](skills/synthetixlib/references/setup.md).

## Status

`0.8.0`. Every module is tested on Paper 1.21.11 and 26.x, but the API may still change before `1.0.0`. Breaking changes
are listed in the [CHANGELOG](skills/synthetixlib/CHANGELOG.md), and the skill tells the agent to check it when the
project uses an older version. Planned: an NPC module.

## Working on the library

Read [`AGENTS.md`](AGENTS.md) first. `./gradlew check` runs unit tests, architecture rules and the snippet check;
`./gradlew smokeTest` boots Paper 1.21.11 and 26.x headless and runs the in-server self-tests. Decisions are in
[`docs/adr`](docs/adr), module designs in [`docs/design`](docs/design), work in progress in [`docs/plan`](docs/plan).

## License

[MIT](LICENSE) © 2026 Phakin Kussayopsuwan (Deware-PK). You may use SynthetixLib in free and paid plugins, closed
source included. Keep the copyright and license notice with the copy you ship (for example the `LICENSE` text in your jar
or your plugin's credits).
