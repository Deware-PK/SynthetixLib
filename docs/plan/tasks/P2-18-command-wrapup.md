# P2-18 Command wrap-up: help, demo, docs, publish 0.3.0
Status: todo · Approved: yes · Model: sonnet · Effort: low
Module: synthetixlib-command, synthetixlib-example · Depends on: P2-16, P2-17 · Design: command.md §6, §7

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/command.md and docs/plan/tasks/P2-18-command-wrapup.md, then implement the task.
Run `gradlew.bat check smokeTest publishToMavenLocal`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- Help (§7): `helpSub()` on the root adds `/<cmd> help`; lists the subcommands the sender may use (permission
  checked), one line each from `usage` + `description`; non-executable literal with `helpSub()` on → help for that
  branch. Messages `helpHeader`, `helpLine` added to `CommandMessages` (with comments).
- Example plugin demo (outside self-test mode), one class per subcommand: `/example` (alias `/ex`) with
  `airdrop create <id>`, `airdrop list`, `airdrop delete <id>` (id suggestions from the collection),
  `give <player> <material> [amount]`, `reload` (permission `example.admin`, console allowed), cooldown on `give`,
  `helpSub()`. All messages from `lang/*.yml` (add `commands:` section with `CommandMessages`).
- Javadoc with snippet on every public type, package-info everywhere; command.md Status → "Implemented (0.3.0)" +
  deviations section; ROADMAP row 3 done; module AGENTS.md final.
- Version → `0.3.0-SNAPSHOT`; `publishToMavenLocal`.

## Acceptance criteria
- [ ] `runServer`: demo commands work in game, tab-complete shows suggestions (paste the lang `commands:` section in Result)
- [ ] `gradlew.bat check smokeTest publishToMavenLocal` green

## Verify
gradlew.bat check smokeTest publishToMavenLocal

## Result
