# synthetixlib-dialog — API design

Status: **Implemented (0.6.0)** — approved 2026-10-24 (all §12 defaults accepted; Geyser test on Bedrock for Windows, §10) · Task: P1-05 · Implementation: P2-29..P2-33 · Date: 2026-10-24
Package root: `com.github.dewarepk.synthetixlib.dialog` · Depends on: core, config (codecs, locale), paper-api only

Wrapper over the Paper Dialog API (`io.papermc.paper.dialog`, `io.papermc.paper.registry.data.dialog`, client 1.21.6+;
the whole API is `@ApiStatus.Experimental` in paper-api 1.21.11). Same split as the gui module: **everything you see
comes from a YAML file, everything that happens comes from code** the file refers to by name. Main use: forms with
typed input (price, amount, name, search, yes/no) — the thing the gui module cannot do (no anvil, gui.md §8).

Paper API surface used (checked against paper-api-1.21.11-R0.1-SNAPSHOT.jar):
`Dialog.create(builder)`, `Audience#showDialog / closeDialog`, `DialogBase` (title, external title, can-close-with-escape,
pause, after-action, body, inputs), `DialogType` (notice, confirmation, multiAction, dialogList, serverLinks),
`ActionButton`, `DialogAction` (customClick(callback, ClickCallback.Options) | customClick(Key, payload) | staticAction |
commandTemplate), `DialogInput` (text, bool, singleOption, numberRange), `DialogBody` (plainMessage, item),
`DialogResponseView` (getText / getBoolean / getFloat), `PlayerCustomClickEvent`, `RegistryEvents.DIALOG` (bootstrap),
`AsyncPlayerConnectionConfigureEvent` (configuration phase).

---

## 1. A dialog file (what the buyer edits)

```yaml
# plugins/Auction/dialogs/sell.yml — copied from the jar on first start, then never overwritten
config-version: 1
title: '<gold>Sell <item-name>'
external-title: '<gold>Sell'          # optional: shown on the button that opens it (lists, pause menu)
type: confirmation                    # notice | confirmation | multi-action | list | links
can-close-with-escape: true
pause: false                          # single-player only; ignored on servers, kept for completeness
body:
  - message: '<gray>Starting price for <white><item-name></white>. Fee: <fee>%'
    width: 250
  - item: { slot: hand }              # or a config item (material/name/item-model...) or `placeholder: lot-item`
    description: '<gray>The item you are selling'
inputs:
  price:
    type: number                      # text | bool | option | number
    label: 'Price'
    min: 1
    max: 1000000
    step: 1
    initial: 100
    format: '%s: %s coins'            # client label format (options.generic_value style)
  duration:
    type: option
    label: 'Duration'
    options:
      1h:  { display: '1 hour' }
      12h: { display: '12 hours', initial: true }
      24h: { display: '24 hours' }
  note:
    type: text
    label: 'Note (optional)'
    max-length: 64
    multiline: { max-lines: 3, height: 48 }
  anonymous:
    type: bool
    label: 'Hide my name'
buttons:                               # confirmation: exactly yes + no; multi-action: any list; notice: one
  yes: { label: '<green>List it', tooltip: '<gray>Puts the lot on sale', width: 150, click: [action:sell] }
  no:  { label: '<red>Cancel', click: [close] }
errors:                                # shown on top of the body when server-side validation fails (§4)
  price: '<red>Price must be between <min> and <max>'
```

Built-in click actions (same words as gui): `close`, `back`, `open:<dialog>`, `menu:<gui menu>` (only when the gui
module is linked, §8), `command:<cmd>` (as the player), `console:<cmd>` (file is owner-controlled), `message:<text>`,
`url:<https://…>`, `copy:<text>`, `sound:<key>`, `action:<name>` (code). Several run in order.
Unknown keys → config error, element kept; a dialog that cannot be decoded → opening it logs + sends nothing.

## 2. Code side (what the plugin author writes)

```java
DialogService dialogs = DialogService.create(lib, configs)       // attaches to lib
        .messages(lang, Messages::dialog);                       // optional <lang:key>

record SellForm(@Range(min = 1, max = 1_000_000) double price,   // typed result, decoded by config codecs (§3)
                String duration, String note, boolean anonymous) {}

DialogType<ItemStack> sell = dialogs.dialog("sell").<ItemStack>withContext()
        .placeholder("item-name", view -> view.context().effectiveName())
        .placeholder("fee", view -> Component.text(settings.fee()))
        .submit("sell", SellForm.class, (form, click) -> auctions.list(click.player(), click.view().context(), form))
        .build();

sell.open(player, player.getInventory().getItemInMainHand());     // any thread; hops to the player's scheduler
```

- `build()` fails fast (logged, dialog still usable with that button inert) if the file uses an `action:` /
  placeholder the code did not register, or a `submit` form whose record components do not match the file's inputs.
- Also: `.action(name, click -> …)` (no form), `.onClose(view -> …)` (exit button / escape where the client reports it, §9),
  `.input(key, view -> initialValue)` (initial value from code, e.g. last price).
- `dialogs.reload()` re-reads all dialog files; buttons of a dialog opened before a reload that changed its file do nothing (unchanged reload: they keep working).
- `DialogView`: `player()`, `context()`, `reopen()` (keeps typed values), `close()`, `next(DialogType, ctx)` (chain).
  One view per viewer, nothing per-player in `DialogType` or static maps (bug #4/#5 spirit).

## 3. Typed responses

- `DialogResponseView` gives `String` / `Boolean` / `Float` by key. The library maps the submitted map onto the form
  record with the **config codec layer** (key kebab-case ↔ component, `@Range`, `@NotEmpty`, enums for option ids,
  `int`/`long`/`double` from the float), so validation rules are written once, the same as config.
- Without a record: `click.response().number("price")`, `.text("note")`, `.bool("anonymous")`, `.option("duration")`.

## 4. Never trust the client (dupe / exploit safety)

- Client limits (`max-length`, number range, option list) are **re-checked on the server**; a modified client can send
  anything. Invalid → the dialog is re-shown with the `errors:` line and the values the player typed (as `initial`).
- Each shown dialog gets fresh one-use callbacks (`ClickCallback.Options` uses = 1, lifetime from settings, default 5m):
  a button clicked twice / replayed packet / stale dialog after reload does nothing (no double-sell, no double-pay).
- The clicking audience must be the view's player, else ignored + debug log.
- **Registry dialogs (§7) are guarded per player** (implemented P2-33): a button of a registry dialog has no one-use
  callback (the client can send its key any time), so `ClickGuard` lets one click per player through at a time: a click while
  the previous registry handler still runs (including `submitAsync`) is ignored, and so is one sooner than
  `DialogSettings.registryClickInterval` (default 250 ms) after the last that ran. State lives in the service, cleared on quit.
- One handler at a time per view (same debounce as gui); `submit` handlers that touch money/items should re-check state
  (item still in hand etc.) — documented, the `context` is a snapshot.
- Text input is plain text: never parsed as MiniMessage when shown again (no tag injection).

## 5. Threads (Folia-safe)

`open`, `reopen`, `close` from any thread → player's entity scheduler.
**Implemented (P2-30):** the Paper callback itself only checks who clicked, takes the view's one use and reads the answers
(thread-agnostic, no server state touched); the steps and the handlers then run on the player's entity scheduler (inline if
the calling thread already owns the player). The thread Paper really fires `ClickCallback` on (1.21.11 / 26.3) is **not
measured**: it needs a connected client and the smoke tests have none. Because of the hop it does not matter for correctness. Every code callback runs on the player's entity
scheduler, whatever thread Paper fires the click on. `submitAsync(...)` for slow handlers (DB) → `scheduler().async()`,
then back to the player for the next dialog / message.

## 6. Text and locale

Identical to gui.md §6: MiniMessage inline, `<lang:key>` per viewer locale, placeholders are components never re-parsed,
unknown tags kept literally (ItemsAdder / Nexo glyphs in titles and labels).

## 7. Registry dialogs (bootstrap, opt-in)

Static dialogs registered in `RegistryEvents.DIALOG` during `PluginBootstrap` — needed for the **pause-menu
"Quick actions" / custom pause entry** (dialog tags) and vanilla `/dialog show`. Limits: no per-player placeholders,
no lambdas (buttons use `customClick(Key)` routed through `PlayerCustomClickEvent`), fixed until restart.

```java
public final class AuctionBootstrap implements PluginBootstrap {               // paper-plugin.yml: bootstrapper: …
    @Override public void bootstrap(final BootstrapContext ctx) {
        SynthetixDialogs.bootstrap(ctx)                                         // reads dialogs/*.yml from ctx.getDataDirectory()
                .register("help", "auction:help")                               // file id → registry key
                .quickActions("help");                                          // add to the pause-menu quick actions tag
    }
}
// onEnable: dialogs.registered("auction:help").action("open-auction", click -> auction.open(click.player()));
```
Clicks on registry dialogs go through the per-player guard of §4 (one handler at a time, `registry-click-interval`).
No static state: the bootstrap object is handed to `DialogService` through the plugin instance (ADR 0004).

## 8. Links to other modules

- gui: `dialogs.linkMenus(gui)` enables `menu:<id>`; gui gets `dialog:<id>` the same way. No compile dependency between
  the two modules (a small `Opener` interface in core, or dialog depends on gui — §12.4).
- command: nothing special; `dialog.open(sender)` from a command handler.

## 9. Minecraft limits (documented, not hidden)

- No live update: changing anything = show again (inputs reset unless we pass them back as `initial`).
- Escape with `can-close-with-escape` closes **without telling the server** for notice/confirmation; `onClose` only
  fires on an exit button. Dialogs that must get an answer set `can-close-with-escape: false`.
- Widths 1–1024, text `max-length`, number range float only, multi-action `columns`; values checked at load.
- Bedrock (Geyser): see the table below (filled by the owner after the manual test, §10).
- Clients below 1.21.6 (ViaVersion) cannot see dialogs → `fallback` hook (§12.6).

**Bedrock / Geyser support** (owner fills in: works / partly / no, plus notes):

| Dialog type | Inputs | Result | Notes |
|---|---|---|---|
| notice | - | _pending_ | |
| confirmation | - | _pending_ | |
| multi-action | - | _pending_ | |
| list | - | _pending_ | |
| links | - | _pending_ | |
| any | text / number / option / bool | _pending_ | |

## 10. Testing

- Unit: YAML → definition (every type/input/body, errors), response → record mapping + validation, callback one-use.
- Smoke self-tests (1.21.11 + 26.3): build every dialog type with `Dialog.create`, registry dialog from the example
  bootstrap, `showDialog` to a fake/offline audience does not throw, reload keeps open views.
- E2E: go-mcbots if it can answer dialogs (check), else manual client test list in the task.
- Manual Geyser test (owner, Bedrock for Windows, Geyser `auth-type: offline`, connect via LAN IP if 127.0.0.1 fails):
  open each type, record what works → doc table in §9.

## 11. Not in this module

Anvil / sign input, chat prompts, resource-pack generation, Bedrock-native forms (Floodgate API = third-party, ADR 0012).

## 12. Decisions for owner (defaults chosen, change if you disagree)

1. **Dialogs in YAML `dialogs/<id>.yml`, behaviour by name** (same as gui) (chosen) vs code-only builders.
2. **Typed `submit(name, Record.class, …)` via config codecs** (chosen) vs only `response().number(...)`.
3. **Server-side re-validation + re-show with errors, one-use callbacks** (chosen; not optional).
4. **gui ↔ dialog link through a tiny opener interface, no module dependency** (chosen) vs dialog depends on gui.
5. **Registry/bootstrap dialogs opt-in in the same module** (chosen) vs dynamic only (skip §7 until a product needs it).
6. **Fallback for clients that cannot show dialogs**: `fallback(player -> …)` hook, no built-in chat prompt (chosen) vs
   built-in chat-input fallback.
7. **Configuration-phase dialogs** (rules / language before join via `AsyncPlayerConnectionConfigureEvent`): **later**,
   not in 0.6.0 (chosen) vs include now.
8. **Version:** module ships as 0.6.0; API marked experimental in Javadoc while Paper keeps it `@Experimental` (chosen).

## 13. Deviations from this design (as implemented, 0.6.0)

- `DialogBuilder#item(name, fn)` added (an `item: { placeholder: x }` needs code to supply the item).
- gui link is `GuiService#link("dialog", dialogs.opener())` / `DialogService#link("menu", gui.opener())` using core's
  `ScreenOpener`, not `dialogs.linkMenus(gui)`.
- Registry dialogs: `SynthetixDialogs.bootstrap(ctx)` is kept by the plugin through `createPlugin` and handed to
  `DialogService#bootstrapped`; registry dialogs are static (no placeholders, no `slot:`/`placeholder:` items).
- Input keys go to the client with `-` as `_` (Paper refuses `-`); the files still use the kebab names.
- Registry clicks are rate-limited per player (`DialogSettings.registryClickInterval`), not part of the original design.
- The callback thread of Paper is not measured by the smoke tests (no client); the owner notes it from the debug log:
  _pending_. Handlers always run on the player's entity scheduler anyway.
- Configuration-phase dialogs are not supported (decision 7).

## Open questions (planner to check before tasks)
- Thread Paper fires `ClickCallback` / `PlayerCustomClickEvent` on (main / region / netty) on 1.21.11 and 26.x.
- Geyser 2.x dialog support level.
- Whether `DialogResponseView` returns typed values for `option` (string id) and `number` (float) on 26.x unchanged.
