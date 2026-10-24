# P2-30 Dialog rendering, views, one-use callbacks, threads
Status: done · Approved: yes · Model: sonnet · Effort: high
Module: synthetixlib-dialog · Depends on: P2-29 · Design: dialog.md §2, §4, §5, §6, §9

## Prompt (paste into Claude Code)
Read AGENTS.md, docs/design/dialog.md and docs/plan/tasks/P2-30-dialog-views-and-callbacks.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Spec
- `DialogType<C>.open(player[, ctx])` from any thread → player's entity scheduler → renders a fresh Paper `Dialog`
  with `Dialog.create(...)` per open (never cached across viewers) and `player.showDialog(...)`. Texts: MiniMessage +
  `<lang:key>` + placeholders exactly like gui (reuse/extract the gui renderer logic into config or core only if it
  needs no public API change; otherwise copy and say so in Result). Unknown tags kept literally.
- `DialogView<C>`: `player() context() reopen() close() next(type, ctx)`; one per open; nothing per player in
  `DialogType` / static maps; views dropped on close / quit / `lib.close()` (`closeDialog()` on open ones).
- Buttons: built-in actions run in order; `action:`/`submit` buttons use
  `DialogAction.customClick(callback, ClickCallback.Options.builder().uses(1).lifetime(settings))`, after-action
  `WAIT_FOR_RESPONSE`, and the library always ends with close / next dialog / reshow. Built-in-only buttons use
  `staticAction` (url, copy, command as player via run_command) or a callback for console/message/sound/open/back.
- Safety (§4): the first used callback of a view consumes the whole view (other buttons of the same dialog do nothing
  afterwards); audience must be the view's player; one handler at a time per view; stale view after reload → ignored.
  Callback lifetime default 5m, from a `DialogSettings` record (config codec) the consumer can put in its config.
- Every code callback runs on the player's entity scheduler. Self-test records which thread Paper fires the
  `ClickCallback` on (1.21.11 and 26.3) → write it in Result and dialog.md §5.
- `onClose`: exit buttons (multi-action/list/links `exit`, confirmation `no` if it only has `close`). Document escape (§9).
- `dialogs.fallback(Predicate<Player> cannotShow, BiConsumer<Player, DialogView<?>> handler)`: the library cannot
  detect old/Bedrock clients itself (no third-party API, ADR 0012), the consumer supplies the predicate.

- Carry-over from P2-29 review:
  - Vanilla input keys are `[a-zA-Z0-9_]+` (template variables). File keys may contain `-` (kebab-case records), so the
    renderer sends `-` as `_` and maps answers back; two file keys that collide after mapping (`a-b` + `a_b`) → config
    error at load, the second input dropped. Unit test + self-test (render a dialog with key `my-key`).
  - Verify the item body width/height limit (P2-29 used 1..256 from memory): self-test builds Paper `DialogBody.item`
    with 256 and 257; fix the parser clamp if Paper disagrees, write the real limit in Result.

## Tests
Unit (fake click model, no server): one-use per view, wrong audience ignored, debounce, stale-after-reload, action
order, fallback routing. Self-tests `dialog.render` (every type + body + input renders on both versions without
throwing; placeholders + lang resolved) and `dialog.callback` (invoke a callback twice → handler runs once; thread logged).

## Acceptance criteria
- [~] dialog.md §4 rules each covered by a test (those that belong here: one-use, audience, debounce, stale; server-side re-validation and plain-text re-show are P2-31)
- [~] callback thread on 1.21.11 + 26.3 written in Result and dialog.md §5 (not measurable without a client; the design makes it irrelevant, written in §5)
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
Ran: `gradlew.bat check` (green, ArchUnit untouched) and `gradlew.bat smokeTest` (PASS 79/79 on 1.21.11 and 26.3, incl.
`dialog.files`, `dialog.render`, `dialog.callback`, `dialog.fallback`). Not committed.

Built: `DialogType.open(player[, ctx])`; `DialogView` (`player context reopen close next`); `DialogRenderer` (file -> Paper `Dialog`,
every kind / body / input, fresh one-use callbacks per showing); `ViewManager` / `DialogViewImpl` / `ViewState` / `ClickFlow` /
`ViewRegistry` (back stack, quit cleanup, close on `lib.close()`); `DialogService.messages / settings / fallback`;
`DialogSettings` (callback lifetime, default 5m); `DialogBuilder.item`. Unit tests: `ViewStateTest` (one use, stale generation,
ended, 16 racing threads -> one winner), `ClickFlowTest` (order, failing step isolated, replaced / closed), `ViewRegistryTest`,
new checker rules. Self-tests: every dialog kind accepted by Paper on both versions, click twice -> handler once, click from
another player ignored, `reopen()` gives fresh working buttons, `<lang:key>` per viewer locale, exit button -> `onClose`, fallback.

Decisions / deviations (change if you disagree):
- **Gui text / placeholder code was copied, not shared** (`DialogRenderer.Pass`, `DialogMessages`, `isVanillaKey`): moving it to
  core or config would be a public API change. About 80 lines.
- **`DialogBuilder.item(name, fn)` added.** The file model (P2-29) has `item: { placeholder: x }` but the builder had no way to
  register it; an unregistered one gives a checker warning and a barrier is shown.
- **`after-action` is per dialog in Paper, not per button:** no callback buttons -> CLOSE, callbacks only -> WAIT_FOR_RESPONSE (as
  the task says), callbacks mixed with static `url:` / `copy:` buttons -> NONE, because WAIT would leave the client waiting on a
  static button. `url:` / `copy:` must be the only step of their button (otherwise the checker makes the button inert).
- **Inert and empty buttons still close the dialog** (they get a callback that only ends the view), for the same WAIT reason.
- **Stale after reload:** a click is ignored when the file changed since the dialog was shown (`resolved` snapshot differs); an
  unchanged reload keeps open dialogs working. Design 2 says open dialogs keep the old definition: they do, but their buttons stop
  working after a real change (safer: the new file may no longer register an action).
- **LIST** renders the dialogs of `buttons.dialogs` inline (same viewer, null context, shared one-use state); `RegistrySet.valueSet`
  accepted them on both versions (`dialog.render`). A list that contains itself, or an unknown id, warns and leaves it out.
- **`submit` buttons do nothing yet** (one warning is logged); P2-31 reads the answers. The answers of the last click are already
  stored per view and used as `initial` by `reopen()` (text, bool, number as float, option id via `getText`). Not verified
  against a real client that `getText` gives the option id (P2-31 should check on 1.21.11 and 26.3).
- `menu:` logs a warning (no link until P2-32). `open:<id>` looks up dialogs of the same service.
- **Thread Paper fires the callback on: not measured** (needs a client). The code does not depend on it (dialog.md section 5).
- **`@Internal` hooks for tests:** `DialogType.preview / attach / click` and `DialogService.executor(...)`. The last exists because
  `Bukkit.isOwnedByCurrentRegion(entity)` casts to `CraftEntity`, so a Proxy player cannot go through the real scheduler; the
  self-test runs tasks inline. A plugin never needs it.
- The `links` exit button is omitted when the file gives none (Paper accepted null on 1.21.11 and 26.3).
- Escape on a notice / confirmation is not reported by the client, so a view stays registered until the next open, quit or
  `close()` (module AGENTS.md, design 9).
- Fallback is not unit tested (needs a player); it is covered by the self-test `dialog.fallback`.

## Review (planner, 2026-10-24)
Approved. Smoke 79/79 on 1.21.11 + 26.3 (dialog.files/render/callback/fallback). Accepted as built: copied text code
(~80 lines), `DialogBuilder.item`, after-action per dialog (CLOSE / WAIT_FOR_RESPONSE / NONE when mixed with static
buttons), `url:`/`copy:` only step of their button, inert buttons still end the view, stale-after-changed-reload ignored
(dialog.md §2 updated), LIST inline, `@Internal` test hooks. Callback thread [~]: measured by the owner in P2-33.
**Not done: both P2-29 carry-overs** (input key `-`→`_` mapping, item body 1..256 check against Paper) → moved to P2-31.
