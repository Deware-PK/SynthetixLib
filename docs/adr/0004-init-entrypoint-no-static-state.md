# 0004. `SynthetixLib.init(plugin)` entry point, no static state

- Status: Accepted
- Date: 2026-10-14

## Context
SnapPlug used a base class + static instance; that forces inheritance and breaks when several plugins
on one server each ship their own (relocated) copy (ADR 0005). Static maps also caused leaks/races.

## Decision
Plugins extend `JavaPlugin` normally and call `SynthetixLib.init(this)`, keeping the returned
instance. All services (scheduler, config, commands, GUIs…) are obtained from / owned by that
instance and released by `close()`. No static mutable fields, no static singletons.

## Consequences
Enforced by ArchUnit (`noStaticMutableState`). Tests can create isolated instances.
