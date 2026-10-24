# synthetixlib-dialog

Package: `com.github.dewarepk.synthetixlib.dialog` (only this module may use it). Design: docs/design/dialog.md.
Depends on core + config (dialog files are read with the config codecs; no dependency on gui, see design §12.4).

Status: **complete, 0.6.0** (P2-29..P2-33). Open for the owner: Geyser table + callback thread note in docs/design/dialog.md.

Types
- `DialogService` - one per `SynthetixLib`; `dialog(id)` -> `DialogBuilder<Void>`; `reload()` re-reads every dialog file.
  Registers the `click:` codec (`ClickActions`) in `configs.codecs()`.
- `DialogBuilder<C>` - stores `placeholder`, `action`, `submit`, `submitAsync`, `input`, `onClose` (names a file may refer
  to) and `build()`s a `DialogType<C>`. `withContext()` must come first (it changes `C`).
- `DialogType<C>` - immutable apart from the swapped file contents; `file()` (parsed `DialogFile`), `isInert(buttonPath)`,
  `open(player[, ctx])` (any thread). `preview` / `attach` / `click` are `@ApiStatus.Internal` hooks for self-tests.
- `DialogView<C>` - `player() context() reopen() close() next(type, ctx)`; `DialogClick<C>` - `view() player() response()` (`DialogResponse`,
  limit-checked answers). Also on the service: `messages(lang, keys)` (`<lang:key>`), `settings(DialogSettings)` (callback lifetime,
  default 5m), `fallback(cannotShow, handler)`, `@Internal executor(...)` (self-tests with fake players).
- `DialogBuilder` also has `item(name, fn)`: the item behind `item: { placeholder: name }` (the design lacked a way to register it).
- `dialog.file` (INTERNAL, public only so the config codecs can map them): `DialogFile`, `DialogKind`, `DialogBody`
  (+ raw `BodyEntry`, `ItemSpec`), `DialogInput` (sealed: text / bool / option / number), `DialogButtons`, `DialogButton`,
  `ClickAction` (sealed, `parse`/`format`), `ClickActions`.
- Package-private: `DialogStore` (dialogs/<id>.yml, bundled copy from the jar once, never overwritten), `DialogParser`
  (raw YAML -> `DialogFile`), `DialogChecker` + `FormCheck` (file vs registrations -> inert buttons), `DialogLoader`,
  `DialogRegistry`, `ClickActionsCodec`, `Registrations`.
- Views (P2-30), package-private: `ViewManager` (one per service: opens/reshows/closes, back stacks in `ViewRegistry`, quit
  listener, thread hops, fallback; implements `ViewHost` for `DialogType`), `DialogViewImpl` (one viewer's copy: context,
  answers of the last click, the callbacks of the current showing, pending `reopen/close/next`), `ViewState` (generation +
  consumed flag, THE one-use rule), `ClickFlow` (runs the built-in steps in order, pure), `DialogRenderer` (file + view ->
  Paper `Dialog`; the only class that builds Paper dialog objects; text/`<lang>`/placeholders copied from the gui renderer).

Rules specific to dialog
- A dialog file never throws at load: every mistake is an error with its path (`buttons.yes.click[1]`) and something safe
  is used instead: body entry / input / button dropped (confirmation and notice fall back to default buttons labelled
  `<lang:gui.yes>` / `gui.no` / `gui.ok`), value out of range clamped (widths 1..1024, item width/height 1..256,
  text max-length >= 1, number initial into min..max, columns >= 1), step <= 0 ignored, `min >= max` drops the input.
  Only text that is not YAML gives no dialog: first load = empty notice, reload = the last good dialog stays.
- `buttons:` keys by type: confirmation `yes`/`no`; notice `ok`; multi-action = named buttons in file order + `exit` +
  `columns` (so `exit` and `columns` are reserved names); list = `dialogs: [ids]` + `exit` + `columns` + `button-width`;
  links = `exit` + `columns` + `button-width`. YAML reads an unquoted `yes`/`no` key as a boolean: the parser maps
  `true`/`false` back to `yes`/`no`.
- Input keys are `[a-z0-9_-]{1,64}` (they are the kebab-case names of the submit record components).
- Warnings (checker): unregistered `action:` -> that button is inert; `submit` record that does not fit the inputs -> the
  buttons using it are inert; unregistered placeholder -> text shown as is; `input(key)` for a key the file lacks.
- `FormCheck` finds a mismatch without reflection by decoding sample answers into the record with the config codecs (the
  real answers go through the same path in P2-31). Extra inputs the record does not read are fine.
- Showing: every `open`/`reopen` makes a NEW Paper dialog with fresh callbacks (`ClickCallback.Options` uses 1, lifetime from
  `DialogSettings`); nothing is cached. A button is a callback unless its click list is exactly one `url:`/`copy:` (static
  click event; the checker makes a button inert if they are mixed with other steps). `after-action`: no callbacks -> CLOSE;
  callbacks only -> WAIT_FOR_RESPONSE; callbacks and static buttons -> NONE (WAIT would hang on a static button).
- One use (design 4): `ViewState.begin()` per showing; the first callback of the current generation consumes the whole view
  (other buttons, replays, earlier showings, anything after `end()` do nothing). A callback is also ignored if the clicker is
  not the viewer, the view is not the viewer's current one (replaced, closed, quit), or the file changed since it was shown
  (`type.resolved()` no longer equals the snapshot). The Paper callback thread only checks, takes the use and reads the
  answers; steps and handlers run on the viewer's entity scheduler. After the steps the library always ends: `next` /
  `reopen` / `close` (default), unless a step already replaced the dialog (`open:` / `back` / `menu:`).
- A button with no usable behaviour (inert, empty `click`) still closes the dialog when clicked: with WAIT_FOR_RESPONSE a
  button that answers nothing would leave the client waiting.
- List dialogs show the dialogs of `buttons.dialogs` rendered for the same viewer (unknown ids and itself are left out with a
  warning); they share the one-use state of the list. `links` passes no exit button to Paper when the file has none.
- Escape closes notices/confirmations without telling the server (design 9): the view stays registered until the next
  `open`, quit or `close()`, and `onClose` only fires for exit buttons (`buttons.exit`, or a confirmation `no` that only closes).
- No static state; the model records must stay `public` (the config module maps them by reflection, this module has none).
- Unit tests need no server: `DialogFixtures.codecs()` uses `Material.matchMaterial`. Anything with real Bukkit codecs is
  covered by the `dialog.*` self-tests in `synthetixlib-example`.
- Submit (P2-31): `FormValidator` (file limits, pure) then `FormSubmit` (config codecs -> record; failures -> `errors.<key>` lines
  shown above the body of the next showing, `DialogViewImpl.failures`). Input keys go to the client with `-` as `_`
  (`InputKeys.wire`; Paper refuses `-`), answers are stored by file key. `submitAsync`: view `defer()`s, `finishAsync()` closes it
  unless the handler asked for something. `ResponseImpl` = `click.response()`.
- Registry dialogs (P2-32): `SynthetixDialogs.bootstrap(ctx)` (public; made in a `PluginBootstrap`, kept by the plugin through
  `createPlugin`, handed to `DialogService#bootstrapped`) reads `dialogs/<id>.yml` with its own `Codecs` (Material by
  `matchMaterial`; the Bukkit codecs need a server), `register(fileId, "ns:key")` in `RegistryEvents.DIALOG.compose()`,
  `quickActions/pauseScreen` in the postFlatten tags. Rendered by `DialogRenderer` in static mode (`StaticKeys`): a button
  that is not a lone `url:`/`copy:` is `customClick(Key ns:path/<button>)`, lists point at registry keys. Static problems
  (placeholders incl. `<player>`, `slot:`/`placeholder:` items) are logged as errors and shown as text / a barrier.
  Service side: `DialogService#registered(key)` -> `DialogBuilder` (registrar `registerRegistry`), `ViewManager` routes
  `PlayerCustomClickEvent` by `ns:path/button` (`routeClick`; `DialogService#click` is the self-test hook), builds a fresh
  `DialogViewImpl`, reads answers, then runs the normal `click()` path (submit validation, re-show dynamic). The client can send
  these keys any time: handlers must check their state. Configuration-phase connections are ignored.
- Registry click guard (P2-33): `ClickGuard` per player in `ViewManager.registryGuards` (one handler at a time incl. `submitAsync`, via
  `asyncDone`, plus `DialogSettings.registryClickInterval`, default 250 ms); cleared on quit/close, never static.
- Links (P2-32): `DialogService#link("menu", opener)` (only `menu`), `#opener()` (also finds registry keys); `menu:` ends the dialog view
  when the opener opened something. `DialogSettings.debug` -> `ViewManager#debug` logs at INFO instead of FINE.
