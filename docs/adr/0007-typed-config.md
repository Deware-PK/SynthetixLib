# 0007. Typed config

- Status: Accepted
- Date: 2026-10-14

## Context
String-path config (`getString("a.b")`) is untyped and was buggy in SnapPlug (comments lost, re-save on reload).

## Decision
Config is declared as records/classes and mapped to/from YAML (Okaeri-style) with comments,
defaults, validation and migration. Locale files use the same mechanism. Library choice is made in
the config design doc (docs/design/config.md).

## Consequences
Comments preserved; save only on change; schema versioning enables migrations (e.g. InteractiveAuction 1.0 → 2.0).
