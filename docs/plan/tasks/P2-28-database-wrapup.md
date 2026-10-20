# P2-28 Database wrap-up: demo, MySQL suite, docs, publish 0.5.0
Status: review · Approved: yes · Model: sonnet · Effort: medium · Ship: yes (database 0.5.0)
Module: synthetixlib-database, synthetixlib-example · Depends on: P2-27 · Design: database.md

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/database.md and docs/plan/tasks/P2-28-database-wrapup.md, then implement the task.
Run `gradlew.bat check smokeTest publishToMavenLocal`, tick the boxes, fill in "Result", set Status: review,
then ship it: `powershell -ExecutionPolicy Bypass -File scripts/ship-module.ps1 -Module database -Version 0.5.0 -NoPush`.
If the in-game check cannot be done by you, mark them [~] and still ship only if everything else is green.

## Spec
- Example demo: `database:` section in the example settings (SQLite default); airdrop claims stored in the DB
  (`/example airdrop claim <id>` writes a row, `/example airdrop stats <id>` reads count + last claimers, shown via the
  demo menu list too). `/example db copy-to-mysql` (permission `example.admin`) uses `Copy` to the MySQL settings.
- MySQL suite (built in P2-24..P2-27): make sure every database test runs on MySQL when the properties exist; document
  the Docker setup and the three properties in the module AGENTS.md.
- Javadoc with snippet on every public type, package-info everywhere; database.md Status → "Implemented (0.5.0)" +
  deviations; ROADMAP row 5 done; module AGENTS.md final. Version → `0.5.0-SNAPSHOT`.

## Acceptance criteria
- [~] `runServer`: claim/stats work in game; restart keeps the data (agent: headless boot only, see Result)
- [x] MySQL suite green against the Docker MySQL (run by the agent with the owner's properties)
- [x] `gradlew.bat check smokeTest publishToMavenLocal` green

## Verify
gradlew.bat check smokeTest publishToMavenLocal

## Result
Done 2026-10-20.
- Demo: `database:` in settings.yml (SQLite `example.db`, prefix `ex_`), `ClaimStore` (migrations in `db/demo`, `.mysql.sql` override),
  `/example airdrop claim|stats <id>`, `/example db copy-to-mysql` (async, `Database.open` + `Copy`), claim count in the demo menu icons.
- `gradlew.bat check smokeTest publishToMavenLocal`: BUILD SUCCESSFUL. MySQL suite ran against the Docker MySQL (0 skipped, 0 failures:
  Database 5, Extension 3, Migration 17, Query 20, Shutdown 7, copy 9+9+9). Smoke 1.21.11 and 26.3: 75/75 PASS.
- Headless `runServer` (1.21.11) from the console: plugin enabled with Hikari + SQLite, `/example airdrop stats example` answered
  ("0 claim(s)"), `/example db copy-to-mysql` hit the failure path cleanly (MySQL defaults in settings.yml), clean shutdown.
- **[~] Not done by the agent:** `claim` needs a player and the restart check needs a client. Owner: `/example airdrop claim example`
  twice, `/example airdrop stats example`, restart, stats again (count must stay); fill in the `database.mysql` section for copy-to-mysql.
- Docs: database.md Status + §12, ROADMAP row 5, module AGENTS.md (Docker setup + properties), version 0.5.0-SNAPSHOT.
