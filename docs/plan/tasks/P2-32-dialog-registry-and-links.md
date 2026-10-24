# P2-32 Registry (bootstrap) dialogs, quick actions, gui ↔ dialog links
Status: done · Approved: yes · Model: sonnet · Effort: high
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

- Carry-over from P2-31 review: `DialogSettings` gets `debug` (default false). When true, the once-per-service
  "first callback thread" line and the stale/wrong-audience ignores are logged at INFO instead of FINE. The example's
  settings.yml sets `debug: true` so the owner sees the thread name in P2-33.

## Tests
Unit: static-only checks, key routing, link parsing in both modules. Self-tests `dialog.registry` (registered dialog
exists in the registry and in the quick-actions tag on both versions) and `dialog.link` (gui `dialog:` + dialog `menu:`).

## Acceptance criteria
- [x] registry dialog visible on 1.21.11 + 26.3 (self-test)
- [x] core/gui API additions minimal and documented
- [x] `gradlew.bat check smokeTest` green (database MySQL suite excluded, see Result)

## Verify
gradlew.bat check smokeTest

## Result
Ran `gradlew.bat check smokeTest -x :synthetixlib-database:test`: green (smoke 84/84 on 1.21.11 and 26.3, new unit tests in dialog + gui).
**Not run:** `:synthetixlib-database:test`. Its MySQL suite needs the owner's local MySQL (gradle.properties `mysqlUrl`), which was not running (connection refused); the SQLite part and everything else is unchanged by this task. Please re-run `gradlew.bat check` with MySQL up.

- **Registry dialog:** `ExampleBootstrap` registers `dialogs/help.yml` as `synthetixexample:help` and adds it to the quick-actions tag. Self-test `dialog.registry` (both versions) finds it in `RegistryKey.DIALOG`, in the `QUICK_ACTIONS` tag, and routes `synthetixexample:help/ping` to the code handler (`DialogService#click`, same path as the custom click event).
- **Click key:** `ns:path/<button>` (the registry key's path, not the spec's `ns:action/<name>`): two registry dialogs may use the same action name. Every button except a lone `url:`/`copy:` is a custom click, including `close`, so the library ends the view the same way as for other dialogs.
- **Not tested here:** a real `PlayerCustomClickEvent` from a client (the self-test enters one step after the event), `/dialog show`, the pause menu itself, pause-screen additions. Registry dialogs are replayable by a modified client with nothing open; documented in `DialogService#registered`, handlers must check state.
- **Static checks:** placeholders (incl. `<player>`) and `slot:`/`placeholder:` items are logged as errors at bootstrap and shown as text / barrier, never thrown. Registry text uses standard MiniMessage tags only (the library is not running at bootstrap).
- **Links:** `core.ScreenOpener`; `GuiService#link/#opener`; `DialogService#link("menu", …)/#opener`; gui `ClickAction.Linked`. Behaviour change: in a menu file `name:argument` with an unknown name is no longer a parse error but a link that is inert (with a warning) until something is linked. `dialog.link` covers: linked item works, unlinked item inert, both openers, dialog `menu:` calls the opener and does not close afterwards. Not covered: a real menu opening from a dialog button (needs a real inventory).
- **Debug:** `DialogSettings(callbackLifetime, debug)` (old one-argument constructor kept). The example's `settings.yml` has `dialog.debug: true`, but self-test mode returns before reading it, so the thread line shows only in a normal run.
- Also changed: `gui.md` §11, module AGENTS.md files, `selftest-menu.yml` (`teleport:spawn` → `teleport`, so it stays an unknown action).

## Review (planner, 2026-10-24)
Approved. Smoke 84/84 on 1.21.11 + 26.3 (dialog.registry, dialog.link); `check` ran with `-x :synthetixlib-database:test`
(MySQL off) → owner re-runs full `check` with MySQL on before commit. No static state (ScreenOpener is an interface; bootstrap
object handed over via `dialogs.bootstrapped`). Accepted: click key `ns:<path>/<button>`, gui `name:arg` with an unknown
prefix parses as a link and is reported at load (inert), switch instead of static set, `debug` flag.
Gap → P2-33: registry buttons have no one-use guard (server cannot know a pause-menu dialog is open), so a modified
client can spam a registry button; add a per-player guard.
