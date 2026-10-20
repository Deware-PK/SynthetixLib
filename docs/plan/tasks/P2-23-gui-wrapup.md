# P2-23 GUI wrap-up: demo, docs, help carry-over, publish 0.4.0
Status: review · Approved: yes · Model: sonnet · Effort: medium · Ship: yes (gui 0.4.0)
Module: synthetixlib-gui, synthetixlib-example · Depends on: P2-22 · Design: gui.md

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/gui.md and docs/plan/tasks/P2-23-gui-wrapup.md, then implement the task.
Run `gradlew.bat check smokeTest publishToMavenLocal`, tick the boxes, fill in "Result", set Status: review,
then ship it: `powershell -ExecutionPolicy Bypass -File scripts/ship-module.ps1 -Module gui -Version 0.4.0`.
If the in-game check cannot be done by you, mark it [~] and still ship only if everything else is green.

## Spec
- Example plugin demo: `/example menu` opens `menus/demo.yml` (bundled): paginated list of airdrops from the
  collection (`onListClick` opens `menus/airdrop.yml` with the airdrop as context), toggle state button, balance-style
  placeholder, an input slot "drop an item here" with a confirm button using `takeInputs()`, close/back buttons,
  `update-every` clock item. Texts via `<lang:key>` for th_TH + en_US.
- Carry-over from P2-18 review (command module): `help` hides playerOnly subcommands from non-players; self-test
  `command.help`.
- Carry-over from P2-22 review (gui `Renderer`, unit test + `gui.lang` self-test case):
  `<lang:key[:arg...]>` with a key **not** in the language files → `Component.translatable(key, args as text)` (MiniMessage's
  standard behaviour, so vanilla keys like `item.minecraft.diamond` still work). Warn once only when the key does not
  start with a vanilla prefix (`block. item. entity. effect. enchantment. biome. container. gui. key. options.
  attribute. advancements. stat.`). Document in gui.md §6.
- Javadoc with snippet on every public type, package-info everywhere; gui.md Status → "Implemented (0.4.0)" +
  deviations section; ROADMAP row 4 done; module AGENTS.md final. Version → `0.4.0-SNAPSHOT`.

## Acceptance criteria
- [~] `runServer`: demo menu works in game (pages, toggle, input + confirm, back/close), no item can be taken out — NOT done by the agent (needs a client); owner to check `/example menu`
- [x] `gradlew.bat check smokeTest publishToMavenLocal` green

## Verify
gradlew.bat check smokeTest publishToMavenLocal

## Result
`gradlew.bat check smokeTest publishToMavenLocal` green; smoke 68/68 on Paper 1.21.11 and 26.3 (new: `command.help`; `gui.lang` extended).

- Demo: `/example menu` (playerOnly) opens `menus/demo.yml`: airdrop list with pages, click opens `menus/airdrop.yml` with
  the airdrop as context (back/close), notify toggle (state, stored in the player's PDC), XP-level placeholder, `update-every: 20t`
  clock, input slot `S` + confirm (`takeInputs('S')`, items go back to the player), close button. Code: `DemoMenus`, `MenuCommand`;
  texts are `<lang:key>` from the `gui` map of `Messages` (en_US default, bundled th_TH).
- Help: playerOnly subcommands are hidden from non-players (`Help`); self-test `command.help` (capture sender).
- `<lang:key>` with a key in no language file -> `Component.translatable(key, args)`; warns once per renderer unless the key
  has a vanilla prefix. Unit tests (`LangAndPassthroughTest`) + `gui.lang`. Documented in gui.md §6.
- gui.md Status "Implemented (0.4.0)" + §11 deviations, ROADMAP row 4 done, gui AGENTS.md final, version 0.4.0-SNAPSHOT.
- Note: ArchUnit forbids static collections, so the vanilla prefixes are a `String[]` (not weakened).
- Not done: in-game play-test of the demo menu (no client available to the agent).
