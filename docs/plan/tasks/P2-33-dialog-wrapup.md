# P2-33 Dialog wrap-up: demo, docs, Geyser check, publish 0.6.0
Status: todo · Approved: yes · Model: sonnet · Effort: medium · Ship: yes (dialog 0.6.0)
Module: synthetixlib-dialog, synthetixlib-example · Depends on: P2-32 · Design: dialog.md

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/dialog.md and docs/plan/tasks/P2-33-dialog-wrapup.md, then implement the task.
Run `gradlew.bat check smokeTest publishToMavenLocal`, tick the boxes, fill in "Result", set Status: review,
then ship it: `powershell -ExecutionPolicy Bypass -File scripts/ship-module.ps1 -Module dialog -Version 0.6.0`.
If the in-game / Geyser checks cannot be done by you, mark them [~] and still ship only if everything else is green.

## Spec
- Demo in the example plugin, texts `<lang:key>` th_TH + en_US:
  - `/example dialog sell` (playerOnly): `dialogs/sell.yml` like dialog.md §1 (number price, option duration, text note,
    bool) with the item in hand as context → typed `SellForm` → message with the values; invalid price re-shows with error.
  - `/example dialog confirm`: confirmation; `/example dialog menu`: multi-action with `menu:demo` (gui link) and
    `open:sell`; demo gui menu gets a `dialog:sell` button.
  - Quick-actions `help` dialog from P2-32.
- Javadoc with snippet on every public type, package-info everywhere; dialog.md Status "Implemented (0.6.0)" +
  §13 deviations + §9 Bedrock table (filled by owner); ROADMAP row 6 done; module AGENTS.md final; version 0.6.0-SNAPSHOT.

## Owner manual test (in game)
1. `runServer`, Java client: each demo command; sell with price 0 and 2000000 → error re-show keeps typed values;
   valid sell → message; double-click the confirm button fast → handled once; Esc on confirm; quick actions (pause → help);
   note the callback thread from the debug log line (P2-31) into dialog.md §5.
2. Geyser: put Geyser-Spigot in `synthetixlib-example/run/1.21.11/plugins`, `auth-type: offline` in Geyser config,
   start Bedrock for Windows, add server LAN IP (not 127.0.0.1 if it fails) port 19132; repeat step 1, write per type
   (notice / confirmation / multi-action / list / links, each input kind) works / partly / no in dialog.md §9.

## Acceptance criteria
- [ ] `runServer`: owner test step 1 passed
- [ ] Geyser table filled (owner, step 2)
- [ ] `gradlew.bat check smokeTest publishToMavenLocal` green

## Verify
gradlew.bat check smokeTest publishToMavenLocal

## Result
(agent fills in)
