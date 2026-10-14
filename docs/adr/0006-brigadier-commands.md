# 0006. Native Brigadier commands

- Status: Accepted
- Date: 2026-10-14

## Context
Paper exposes Brigadier via the lifecycle `COMMANDS` event. `BasicCommand` loses client-side
suggestions/validation. SnapPlug's command system had shared mutable state and hard-coded messages.

## Decision
`synthetixlib-command` wraps native Brigadier in one fluent API with typed argument types, permission,
cooldowns (per instance, thread-safe) and locale-driven messages.

## Consequences
Registration happens in the lifecycle event (from `onEnable` or bootstrap). No `plugin.yml` commands.
