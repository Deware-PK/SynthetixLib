# P2-32 Registry (bootstrap) dialogs, quick actions, gui ↔ dialog links
Status: todo · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-dialog, synthetixlib-core, synthetixlib-gui, synthetixlib-example · Depends on: P2-31 · Design: dialog.md §7, §8

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/dialog.md and docs/plan/tasks/P2-32-dialog-registry-and-links.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- `SynthetixDialogs.bootstrap(BootstrapContext)` (no static state: returns an object the consumer keeps and passes on
  through its plugin, ADR 0004): reads `dialogs/*.yml` from `ctx.getDataDirectory()` (copy bundled defaults first),
  `.register(fileId, "ns:key")` in `RegistryEvents.DIALOG` (compose handler), `.quickActions(ids…)` /
  `.pauseScreen(ids…)` add them to the dialog tags (`DialogTagKeys`). Static only: placeholders → error at bootstrap,
  `action:`/`submit` buttons → `DialogAction.customClick(Key "ns:action/<name>", payload)`.
- `DialogService.registered("ns:key")` → builder for actions/submits of a registry dialog; one `PlayerCustomClickEvent`
  listener per service routes by key to the handler (same validation + response mapping as P2-31, player from the
  connection; configuration-phase connections ignored for now, §12.7). Unknown key in the plugin's namespace → debug log.
- Link without module dependency (§12.4): core gets a public functional interface `ScreenOpener` (`boolean open(Player,
  String id)`), `GuiService#opener()` and `DialogService#opener()`; `dialogs.link("menu", gui.opener())` enables
  `menu:<id>`; `gui.link("dialog", dialogs.opener())` enables a new gui action `dialog:<id>` (gui ClickAction gets a
  generic `linked:<prefix>:<id>` form parsed from `<prefix>:<id>` for linked prefixes; unlinked prefix → config warning).
  This is a public API addition to core + gui: keep it minimal, Javadoc it, note it in gui.md §11.
- Example: add a `ExampleBootstrap` (paper-plugin.yml `bootstrapper:`), register `dialogs/help.yml` as
  `synthetixexample:help` in quick actions.

## Tests
Unit: static-only checks, key routing, link parsing in both modules. Self-tests `dialog.registry` (registered dialog
exists in the registry and in the quick-actions tag on both versions) and `dialog.link` (gui `dialog:` + dialog `menu:`).

## Acceptance criteria
- [ ] registry dialog visible on 1.21.11 + 26.3 (self-test)
- [ ] core/gui API additions minimal and documented
- [ ] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
(agent fills in)
