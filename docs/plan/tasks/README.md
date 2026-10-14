# Tasks

One file per task: `P<phase>-<nn>-<slug>.md`, small enough for one agent session.
Tasks are written by the planner (owner + Claude in Cowork) and executed by Claude Code.

## Header fields
- `Status`: todo | in-progress | review | done | blocked
- `Approved`: yes → agent implements without proposing first (see AGENTS.md)
- `Model / Effort`: what to run the task with in Claude Code
  - **low**: mechanical, fully specified (rename, docs, boilerplate, small tests)
  - **medium**: normal feature work with a clear spec
  - **high**: concurrency, security/dupe logic, tricky APIs, or many interacting parts
- `Depends on`: tasks that must be done first

## Agent loop
1. Read AGENTS.md + the task (+ linked design doc). 2. Implement. 3. Run the Verify commands.
4. Tick acceptance boxes, set `Status: review`, write a short "Result" section (what changed, anything
   surprising). 5. Do not commit.

## Template
```md
# P2-03 Title
Status: todo · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-core · Depends on: P2-01 · Design: docs/design/core.md §3

## Prompt (paste into Claude Code)
Read AGENTS.md and docs/plan/tasks/P2-03-....md, then implement the task. ...

## Goal
## Spec
## Acceptance criteria
- [ ] ...
## Verify
gradlew.bat check smokeTest
## Result
(agent fills in)
```
