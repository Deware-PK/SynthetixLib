# Reviewing an eval result

Run in a **second, fresh Claude Code session** (Sonnet or stronger) opened in the same folder the builder worked in
(for example `D:\Minecraft\Evals\auction-lite`), after the builder session ended. Replace `<eval>` and paste:

```text
You are a strict reviewer. In this folder an AI agent built a Paper plugin, using only the skill in .claude/skills/synthetixlib.
Do not change the plugin's code. You may run commands to inspect and build it.

1. Read the task the agent was given and the scoring checklist:
   D:\Minecraft\Plugins\SynthetixLib\skills\synthetixlib\evals\<eval>.md  (the "Prompt" and "Checklist" sections).
2. Run `gradlew build` (add `-PsynthetixLocal=true -PsynthetixLocalVersion=<version in gradle.properties>` if the agent's
   build file needs it) and unzip the jar from build/libs to verify relocation. Read all source files, the YAML files and paper-plugin.yml.
3. Score the 10 checklist items 1 / 0 (N/A = 1 with a reason, except where the checklist forbids N/A). For each 0 quote
   the file and line and say what is wrong. Do not give credit for intent: only what the code does.
4. Check the task-specific bullets and say which fail.
5. List doc gaps: for every 0 (and every workaround you see, like the agent reading library jars, guessing an API or
   fighting the build), find which skill file should have prevented it (.claude/skills/synthetixlib/SKILL.md,
   references/*.md, rules/*.md, templates) and say what is missing or misleading. If the skill was right and the agent
   ignored it, say so. If the API itself forced the mistake, say "API" and why.

Output exactly:

## Review: <eval>
Builds: yes/no · Score: N/10
| # | Check | 1/0 | Evidence |
(10 rows)
Task-specific: ...
### Doc gaps
- <skill file>: <what to add or fix> (fixes check #N)
### API problems
- <issue> (or "none")
```

Paste the output into `RESULTS.md`.
