# P2-18 Command wrap-up: help, demo, docs, publish 0.3.0
Status: done · Approved: yes · Model: sonnet · Effort: low · Ship: yes (command 0.3.0)
Module: synthetixlib-command, synthetixlib-example · Depends on: P2-17a · Design: command.md §6, §7

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/command.md and docs/plan/tasks/P2-18-command-wrapup.md, then implement the task.
Run `gradlew.bat check smokeTest publishToMavenLocal`, tick the boxes, fill in "Result", set Status: review,
then ship it: `powershell -ExecutionPolicy Bypass -File scripts/ship-module.ps1 -Module command -Version 0.3.0`.

## Spec
- Help (§7): `helpSub()` on the root adds `/<cmd> help`; lists the subcommands the sender may use (permission
  checked), one line each from `usage` + `description`; non-executable literal with `helpSub()` on → help for that
  branch. Messages `helpHeader`, `helpLine` added to `CommandMessages` (with comments).
- Carry-over fixes from the P2-17a review (planner spec errors, each with a unit test):
  - **Start the cooldown before releasing the reservation** (today: release, then `finished()`). In between, neither
    `busy` nor the cooldown applies, so a second run can slip in. Order: `finished()` (cooldown) -> release. Both
    present for a moment is harmless: `Executor` checks the cooldown first.
  - **A quit must not drop an in-flight reservation.** Quit + rejoin while the handler still runs would allow a second
    run (dupe risk for bid/sell). `clear(uuid)` on quit removes cooldowns only; reservations are released only by
    their own `finally` (the token stays, so a stale release still cannot drop a newer run). `clearAll()` on close
    unchanged. Update command.md §5 and the test `quit drops it`.
- Example plugin demo (outside self-test mode), one class per subcommand: `/example` (alias `/ex`) with
  `airdrop create <id>`, `airdrop list`, `airdrop delete <id>` (id suggestions from the collection),
  `give <player> <material> [amount]`, `reload` (permission `example.admin`, console allowed), cooldown on `give`,
  `helpSub()`. All messages from `lang/*.yml` (add `commands:` section with `CommandMessages`).
- Javadoc with snippet on every public type, package-info everywhere; command.md Status → "Implemented (0.3.0)" +
  deviations section; ROADMAP row 3 done; module AGENTS.md final.
- Version → `0.3.0-SNAPSHOT`; `publishToMavenLocal`.

## Acceptance criteria
- [~] `runServer`: demo commands work in game, tab-complete shows suggestions (paste the lang `commands:` section in Result)
- [x] `gradlew.bat check smokeTest publishToMavenLocal` green

## Verify
gradlew.bat check smokeTest publishToMavenLocal

## Result
Done: `helpSub()` + `Help` handler (`CommandMessages.helpHeader/helpLine`, `CommandHelpTest`); cooldown now starts before the
reservation is released and a quit keeps in-flight reservations (tests in `CommandInFlightTest`); `/example` demo in
`synthetixlib-example` (`demo/command/*`, one class per subcommand); command.md §5/§10, ROADMAP, module AGENTS.md; version 0.3.0-SNAPSHOT.

Verified: `gradlew.bat check smokeTest publishToMavenLocal` green (smoke 1.21.11 + 26.x PASS).
**Not done:** the in-game `runServer` check (I cannot connect a client): ticked `[~]`, please try `/example help`, `/ex airdrop delete <TAB>`,
`/ex give <player> <material>` (cooldown 10 s) yourself.

`lang/*.yml` `commands:` section (defaults, English):
```yaml
commands:
  player-only: <red>Only players can use this.
  cooldown: <red>Wait <time> before using /<label> again.
  busy: <red>Please wait, /<label> is still running.
  player-not-found: '<red>No player found: <input>'
  invalid-value: '<red><input> is not valid: <reason>'
  error: <red>Something went wrong, see the console.
  help-header: '<yellow>Commands of /<command>:'
  help-line: <yellow>/<command> <usage> <gray>- <description>
  disabled: ''
```


## Review (planner, 2026-10-19)
Approved. Both carry-over fixes correct (`finished()` before release in the async `finally`; `clear(uuid)` keeps
`inFlight`). Smoke 60/60 on 1.21.11 + 26.3. Owner does the in-game check, then runs ship.
Follow-up (gui phase, low): help lists playerOnly subcommands to the console; no self-test covers `help`.
