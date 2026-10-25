# Phase 3 — AI-first docs (Agent Skill) — design

Status: **Approved** 2026-10-25 (all §8 defaults) + owner change 2026-10-25: skill-only distribution like vercel-labs/agent-skills (§6), no docs artifact; JitPack now (ADR 0015, Accepted) · Task: P1-06 · Date: 2026-10-25

Goal: an AI coding agent (Claude Code, Codex, Cursor…) that has never seen SynthetixLib can build a correct Paper plugin
with it on the first try — right API, right threads, no rule broken (AGENTS.md locked decisions / never-again list).
Humans read the same files. The docs are **tested like code**: snippets compile, and an eval suite checks that an agent
using only the skill produces a plugin that builds and passes a checklist.

---

## 1. Layout (in this repo)

```
skills/synthetixlib/
  SKILL.md                    entry: what the lib is, setup, the 12 rules, module map, "which reference to read when"
  references/
    setup.md                  consumer build (shade + relocate), paper-plugin.yml, PluginLoader, bootstrap, init/close
    core.md  config.md  command.md  gui.md  dialog.md  database.md
    recipes.md                end-to-end: shop menu + buy dialog + SQLite; per-player cooldown command; reloadable config
    troubleshooting.md        error message / symptom → cause → fix (from our own exceptions and log lines)
  rules/                      one file per rule, vercel-react-best-practices style: <area>-<name>.md with
                              frontmatter (title, impact CRITICAL|HIGH|MEDIUM, tags), why, ❌ incorrect, ✅ correct
                              e.g. threads-no-bukkit-scheduler.md, state-no-static-maps.md, gui-getholder-false.md
  templates/consumer-plugin/  minimal compiling consumer project (Gradle, shadow relocate, loader, plugin class)
  scripts/new-plugin.(ps1|sh) copy the template, rename package/plugin id
llms.txt                      repo root: short index pointing at SKILL.md + references (llmstxt.org format)
.claude-plugin/marketplace.json  makes the repo installable: /plugin marketplace add Deware-PK/SynthetixLib
```
Progressive disclosure: SKILL.md stays short (< ~300 lines); an agent reads one reference per module it uses.

## 2. SKILL.md content

- Frontmatter `name: synthetixlib`, `description:` triggers ("Paper plugin", "SynthetixLib", "Minecraft plugin with
  menus/dialogs/config/commands/database").
- 30-second setup + a 20-line "hello plugin" (init, one config, one command, one menu).
- **Rules for the agent** (do / never), from AGENTS.md: Paper only, Java 21, no static state, no NMS/reflection,
  schedule only through `lib.scheduler()`, MiniMessage only, `getHolder(false)`, typed config never string paths,
  all player text through locale, consumer package must not start with the lib package, close() in onDisable.
- Module map: problem → module → reference file ("text input from a player → dialog, not anvil").
- Version line: "written for SynthetixLib 0.6.0".

## 3. Reference file shape (every module the same)

1. When to use / when not. 2. Minimal example. 3. YAML the buyer edits (if any). 4. API table (type → what for).
5. Threads. 6. Pitfalls (each a "wrong → right" pair). 7. Limits (Minecraft / Paper). 8. Link to the design doc.
Written from the approved design + its "Deviations" section (as built), not from the original plan.

## 4. Snippets that cannot rot

- Every code snippet in `skills/` is copied from real code: regions in `synthetixlib-example` (and the template)
  marked `// @snippet:<name>` … `// @end`. A Gradle task `syncSnippets` rewrites the fenced blocks tagged
  ` ```java snippet=<name>`; `checkSnippets` (part of `check`) fails if a block differs from its source or a name is
  missing. So every snippet compiles and the smoke test runs it.
- YAML examples come from the bundled resource files the same way (`# @snippet:` markers).

## 5. AI-driven loop (evals)

- `skills/synthetixlib/evals/*.md`: task prompts + checklist, e.g. "auction-lite: /sell opens a dialog for price,
  lots in SQLite, a paginated menu to buy, reloadable messages in th_TH + en_US".
- Run: a fresh Claude Code (Sonnet) session in an empty folder with **only** the skill installed builds it
  (`gradlew build` against `publishToMavenLocal`), then a reviewer pass scores the checklist (builds, rules kept,
  correct thread use, no string config paths, locale used…). Results logged in `evals/RESULTS.md` per lib version.
- Every failure becomes either a doc fix (pitfall / troubleshooting line) or an API fix task. Re-run until green.
- Module wrap-up tasks (`Ship: yes`) get a new step: update the module's reference + version line, `checkSnippets` green.

## 6. Distribution (skill only, like vercel-labs/agent-skills)

- The skill lives in this repo under `skills/`. Install with the open skills CLI:
  `npx skills add Deware-PK/SynthetixLib` (Claude Code, Codex, Cursor, …). Claude Code also via
  `.claude-plugin/marketplace.json`. Other agents: `llms.txt`.
- No docs artifact in Maven/JitPack (owner decision 2026-10-25). Version safety instead:
  - SKILL.md line "written for SynthetixLib <x.y.z>" + each API in references marked `since` / `changed in`.
  - SKILL.md tells the agent: read the lib version from the project's build file; if older than the skill, check the
    `changed in` notes before using an API.
  - The ship script updates the version line; release notes (`skills/synthetixlib/CHANGELOG.md`) list API breaks.
- The consumer template's AGENTS.md / CLAUDE.md say "use the synthetixlib skill".

## 7. Not in this phase

Website / docs site, Thai translation, Javadoc site hosting (Javadoc stays in code).

## 8. Decisions for owner (defaults chosen, change if you disagree)

1. **Audience:** AI agents first (your own Claude Code for IA 2.0 / Farm Workers, and any dev's agent), humans second (chosen).
2. **Format:** Agent Skill (`SKILL.md` + references) + `llms.txt` + Claude plugin marketplace in this repo (chosen) vs
   only a docs folder.
3. **English only** (chosen; cheaper for agents, matches tasks).
4. **Snippets synced from compiled example code + `checkSnippets` in `check`** (chosen) vs hand-written snippets.
5. **Consumer template + `new-plugin` script** in the skill (chosen) — the agent starts from a building project.
6. **Eval suite with 3 tasks** (auction-lite, shop, cooldown-command) run by Claude Code, results per version (chosen).
7. **Docs update is part of every future module wrap-up** (chosen).

## 9. Tasks (after approval)
P3-01 skill skeleton + SKILL.md + setup.md + template + script · P3-02 snippet sync/check task · P3-03 references
core/config/command · P3-04 references gui/dialog/database + recipes + troubleshooting · P3-05 rules/ files + llms.txt + marketplace + `npx skills add` check + `jitpack.yml` ·
P3-06 eval run + fixes.
