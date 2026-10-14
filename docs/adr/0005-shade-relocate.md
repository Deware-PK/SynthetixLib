# 0005. Consumers shade + relocate SynthetixLib

- Status: Accepted
- Date: 2026-10-14

## Context
Buyers on BuiltByBit expect to drop in one jar. A separate "lib plugin" adds install friction and
version conflicts between products.

## Decision
Each product shades SynthetixLib with the Shadow plugin and relocates
`com.github.dewarepk.synthetixlib` → `<product package>.libs.synthetixlib`
(`synthetix.paper-plugin` convention, `synthetixPlugin.libsPackage`).

## Consequences
- No static/global state (ADR 0004), no reflection/class names in strings, resources namespaced per plugin.
- Consumer packages must not start with `com.github.dewarepk.synthetixlib`.
- Registry-level things (Brigadier commands, Dialogs registered at bootstrap) must be registered under the
  consumer's own namespace; the library offers hooks to call from the consumer's `PluginBootstrap`.
- `verifyRelocation` (check) and self-test `core.relocated` (smoke) guard it.
- Keep core small; consumers shade only the modules they use.
