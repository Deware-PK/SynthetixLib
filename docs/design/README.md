# API design docs

One file per module (`core.md`, `config.md`, …), written before implementation (Phase 1).
Order: user-side examples → public types → behaviour/edge cases → testing plan → open questions.
Approved designs become the source for Phase 2 tasks and later for the public docs / llms.txt.

Modules without a separate design file: `synthetixlib-selftest` (0.9.0) is designed in its task, `docs/plan/tasks/P4-04-selftest-kit.md`
(in-server self-tests for products, `SelfTests` / `SelfTest` / `Checks`, template `runServer` / `smokeTest`); reference: `skills/synthetixlib/references/selftest.md`.
