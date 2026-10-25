# dialog

`synthetixlib-dialog` (since 0.6.0). Wrapper over Paper's native Dialog API (client 1.21.6+): forms with typed input
(a price, an amount, a name, yes/no). Same split as the gui module: **the YAML file says what it looks like, code
registers what happens by name**. Depends on core and config. Design: `docs/design/dialog.md` (read its section 13,
"Deviations"). The Paper dialog API is `@Experimental` in 1.21.11, so the module is too.

## When to use / when not

- Use a dialog whenever the player must type text or a number, pick an option, tick a box or confirm. This replaces anvil
  GUIs and chat prompts.
- Use a **menu** (`gui.md`) to browse, page or drop items. A button in either opens the other.
- Not here: anvil / sign input, chat prompts, Bedrock-native forms, dialogs in the configuration phase (before join).

## Minimal example

The file (`dialogs/<id>.yml`, copied from the jar on first start, never overwritten):

```yaml snippet=dialog-sell-yml
config-version: 1
title: '<lang:sell-title>'
external-title: '<lang:sell-external>'
type: confirmation
body:
  - message: '<lang:sell-info>'
    width: 250
  - item: { slot: hand }
    description: '<lang:sell-item>'
inputs:
  price:
    type: number
    label: '<lang:sell-price>'
    min: 1
    max: 1000000
    step: 1
    initial: 100
  duration:
    type: option
    label: '<lang:sell-duration>'
    options:
      h1: { display: '<lang:sell-1h>' }
      h12: { display: '<lang:sell-12h>', initial: true }
      h24: { display: '<lang:sell-24h>' }
  note:
    type: text
    label: '<lang:sell-note>'
    max-length: 64
  anonymous:
    type: bool
    label: '<lang:sell-anonymous>'
buttons:
  yes: { label: '<lang:sell-yes>', tooltip: '<lang:sell-yes-tip>', click: ['action:sell', 'sound:ui.button.click'] }
  no: { label: '<lang:sell-no>', click: [close] }
errors:
  price: '<lang:sell-error-price>'
```

The code: a record for the answers (public; components are the input keys in camelCase, the file uses kebab-case) and a
registration per name the file uses.

```java snippet=dialog-form-record
/** The answers of {@code sell.yml}; public so the config codecs can fill it. The price is checked against the file. */
public record SellForm(double price, String duration, String note, boolean anonymous) {
}
```

```java snippet=dialog-submit
final DialogType<ItemStack> sell = dialogs.dialog("sell").<ItemStack>withContext()
        .placeholder("item-name", view -> itemName(view.context()))
        .submit("sell", SellForm.class, (form, click) -> {
            final Player player = click.player();
            lang.send(player, Messages::dialogSold,
                    Placeholder.component("item", itemName(click.view().context())),
                    Placeholder.unparsed("price", String.valueOf((long) form.price())),
                    Placeholder.unparsed("duration", form.duration()),
                    Placeholder.unparsed("note", form.note().isBlank() ? "-" : form.note()),
                    Placeholder.unparsed("who", form.anonymous() ? "anonymous" : player.getName()));
        })
        .build();
```

Wiring (once, in `onEnable`):

```java snippet=template-enable
lib = SynthetixLib.init(this);
final Configs configs = Configs.create(lib);
settings = configs.file("config.yml", Settings.class, Settings::defaults).load();
lang = configs.locale("lang", Messages.class, Messages::defaults).defaultLocale(Locale.US).load();

final DialogService dialogs = DialogService.create(lib, configs).messages(lang, Messages::dialog);
final GreetDialog greet = GreetDialog.create(dialogs, lang);
final GuiService gui = GuiService.create(lib, configs).messages(lang, Messages::gui)
        .link("dialog", dialogs.opener());
final MainMenu menu = MainMenu.create(gui);

CommandService.create(lib)
        .messages(lang, Messages::commands)
        .register(MyPluginCommand.build(settings, lang, menu, greet));

// Database module: Database.create(lib, <DatabaseSettings from config.yml>); closed together with lib.
getServer().getPluginManager().registerEvents(this, this);
```

`sell.open(player, item)` (the context is whatever you pass) works from any thread and hops to the player's scheduler.

## The dialog file

| Key | Meaning |
|---|---|
| `title`, `external-title` | MiniMessage; `<lang:key>` and your placeholders work |
| `type` | `notice` (button `ok`), `confirmation` (`yes` + `no`), `multi-action` (named buttons + `exit` + `columns`), `list` (`dialogs: [ids]`), `links` |
| `can-close-with-escape` | `false` for dialogs that must be answered |
| `body` | list of `message:` (+ `width`) or `item:` (`slot: hand`, a config item, or `placeholder: name` registered with `item(...)`) with `description` |
| `inputs.<key>` | `type: text` (`max-length`, `multiline`), `bool`, `option` (`options:` with `display`, `initial`), `number` (`min`, `max`, `step`, `initial`, `format`) |
| `buttons` | by type (see above); each has `label`, `tooltip`, `width`, `click` |
| `errors.<input-key>` | the line shown above the body when server-side validation of that input fails |

Click steps: `close`, `back`, `open:<dialog>`, `menu:<id>` (needs the link), `command:<cmd>`, `console:<cmd>`,
`message:<text>`, `url:<https://...>`, `copy:<text>`, `sound:<key>`, `action:<name>` (code). A button that is exactly one
`url:` or `copy:` is a static client action; mixed with other steps it is inert. A file never throws at load: every mistake is an
error with its path and a safe fallback (dropped input / button, default buttons, clamped values). YAML reads an unquoted
`yes` / `no` key as a boolean; the parser maps it back. Input keys: `[a-z0-9_-]{1,64}`.

## Typed answers and validation

`submit(name, Record.class, (form, click) -> ...)` decodes the answers with the config codecs: `@Range`, `@NotEmpty`, enums
for option ids, `int` / `long` / `double` from the client's float. Without a record use `click.response()`:
`number(key)` (`OptionalDouble`), `integer(key)`, `text(key)`, `bool(key)`, `option(key)`.

**The client is never trusted.** `max-length`, ranges and the option list are re-checked on the server. An invalid answer
re-shows the dialog with the `errors:` line and what the player typed as the initial values. Text is shown again as plain
text, never parsed as MiniMessage.

### Wide numbers and limits that come from config

- A `number` input is drawn as a **slider** (`min`, `max`, `step`). It suits small ranges (1-64 items), not a price from 1
  to 1,000,000,000: use a `text` input (set `max-length`) and parse the number yourself, as below.
- `min` / `max` / `@Range` live in the dialog file or the record, so a limit that comes from `config.yml` (min / max
  price, max per purchase) cannot be expressed there; check the config value in the handler. Do not keep the same limit
  in both places (slider `max: 64` and config `max-per-purchase` drift apart).
- On a bad value re-show the dialog with `view.next(dialog, context)`: the context carries what was typed and the error
  text; the file shows it with a `placeholder("error", ...)` in a `body` message, and `input("price", ...)` gives the
  typed value back. The dialog type is read when the click happens, so it can be a field of your class.

```java snippet=dialog-config-limits
priceDialog = dialogs.dialog("price").<PriceAsk>withContext()
        .placeholder("error", view -> Component.text(view.context().error()))   // "" or the message
        .input("price", view -> view.context().typed())                         // text input: any size
        .action("confirm", click -> {
            final String typed = click.response().text("price").orElse("").trim();
            long price = -1;
            try {
                price = Long.parseLong(typed);
            } catch (final NumberFormatException ignored) {
                // stays -1: the same error as a price out of range
            }
            if (price < 1 || price > maxPrice) {                                // limit from config.yml
                click.view().next(priceDialog, new PriceAsk(typed, "Price must be 1-" + maxPrice));
                return;
            }
            click.player().sendMessage("Price: " + price);
        })
        .build();
priceDialog.open(player, new PriceAsk("", ""));
```

Planned (task P2-34, not available yet): a `validate` hook on `submit`, limits from code and a typed number text input,
so this hand-written flow goes away. Today the pattern above is the supported way. In real code build the error text
from your `Messages` (`lang.get(player, Messages::x)`).

## More registrations, service options, async

```java snippet=dialog-more
final DialogType<String> search = dialogs.dialog("search").<String>withContext()
        .input("query", view -> view.context())                              // initial value from code
        .action("clear", click -> click.view().reopen())                     // a button without a form
        .submitAsync("search", SearchForm.class, (form, click) -> {          // slow: runs async
            final String found = form.query() + (form.exact() ? " (exact)" : "");
            click.view().player().sendMessage(found);
        })
        .onClose(view -> lib.plugin().getLogger().info(view.player().getName() + " closed it"))
        .build();
search.open(player, "last query");                                           // any thread
```

```java snippet=dialog-service-options
dialogs.settings(new DialogSettings(Duration.ofMinutes(2), false))           // how long the buttons work
        .fallback(player -> player.getProtocolVersion() < 771,               // clients that cannot show dialogs
                (player, view) -> player.sendMessage("Please update your game"));
```

- `action(name, handler)`: a button without a form. `input(key, view -> initial)`: initial value from code. `onClose`:
  only fires for an exit button (`buttons.exit`, or a `no` that only closes): Escape closes notices and confirmations
  **without telling the server**, so a dialog that must get an answer sets `can-close-with-escape: false`.
- `DialogView`: `player()`, `context()`, `reopen()` (keeps typed values), `close()`, `next(type, context)` (chain). After
  the steps the library always ends with `next` / `reopen` / `close` unless a step already replaced the dialog.
- `fallback(cannotShow, handler)`: the library cannot tell which clients lack dialogs (old client behind ViaVersion,
  Bedrock); you supply the predicate and open a menu or send a message instead.
- `DialogSettings(callbackLifetime, debug[, registryClickInterval])`: how long buttons work (default 5m, 1s..1d); `debug`
  logs the first click's thread at INFO; `registryClickInterval` (default 250 ms, 0..1m). Usually a component of your
  settings record: `DialogSettings.defaults()`.

## Registry dialogs (pause menu, `/dialog show`)

Only needed to put a dialog into the pause menu's quick actions or the vanilla registry. They are static (no placeholders,
no `slot:` / `placeholder:` items), fixed until restart, and registered while the server starts:

```java snippet=dialog-bootstrap
@Override
public void bootstrap(final BootstrapContext context) {
    registry = SynthetixDialogs.bootstrap(context)
            .register("help", HELP)
            .quickActions("help");
}
```

```yaml snippet=bootstrapper-yml
loader: com.github.dewarepk.synthetixexample.ExampleLoader
bootstrapper: com.github.dewarepk.synthetixexample.ExampleBootstrap
```

The plugin keeps the registry object (`createPlugin` in the bootstrap passes it to the plugin constructor) and hands it to
the service, then registers the code of each registry dialog:

```java snippet=dialog-service
final DialogService dialogs = DialogService.create(lib, configs)
        .settings(settings.get().dialog())
        .bootstrapped(dialogRegistry);
```

```java snippet=dialog-registered
dialogs.registered(ExampleBootstrap.HELP)
        .action("ping", click -> click.player().sendMessage(Component.text("Pong!")))
        .build();
```

```yaml snippet=dialog-registry-yml
config-version: 1
title: '<gold>SynthetixExample help'
external-title: '<gold>Example help'
type: multi-action
body:
  - message: '<gray>This dialog is in the dialog registry: open it from the pause menu or with <white>/dialog show synthetixexample:help</white>.'
    width: 250
buttons:
  ping: { label: '<green>Ping', tooltip: '<gray>Runs the action registered in code', click: ['action:ping', 'sound:ui.button.click'] }
  docs: { label: '<aqua>Source', click: ['url:https://github.com/Deware-PK/SynthetixLib'] }
  exit: { label: '<red>Close', click: [close] }
```

`SynthetixDialogs`: `bootstrap(context)`, `register(fileId, "ns:key")`, `quickActions(fileIds...)`, `pauseScreen(fileIds...)`.
`DialogService#bootstrapped(registry)` first, then `registered(key)` returns a `DialogBuilder<Void>`. The client can send
these keys at any time, so a handler must check its own state. Clicks are limited per player (one handler at a time, plus
`registryClickInterval`).

## Linking to menus

`dialogs.link("menu", gui.opener())` enables `menu:<id>` in dialog files (only the prefix `menu`), and
`gui.link("dialog", dialogs.opener())` the other way. See the `gui-links` snippet in `gui.md`.

## API table

| Type | For | Since |
|---|---|---|
| `DialogService` | `create(lib, configs)`, `messages`, `settings`, `fallback`, `bootstrapped`, `registered`, `link`, `opener`, `dialog(id)`, `reload`, `lib()`, `configs()`, `isClosed`, `close` | 0.6.0 |
| `DialogBuilder<C>` | `withContext()`, `placeholder`, `item`, `action`, `submit`, `submitAsync`, `input`, `onClose`, `build()` | 0.6.0 |
| `DialogType<C>` | `id()`, `file()`, `isInert(path)`, `open(player)`, `open(player, context)` | 0.6.0 |
| `DialogView<C>` | `player`, `context`, `reopen`, `close`, `next` | 0.6.0 |
| `DialogClick<C>` | `view()`, `player()`, `response()` | 0.6.0 |
| `DialogResponse` | `number`, `integer`, `text`, `bool`, `option` | 0.6.0 |
| `DialogSettings` | record; `defaults()` | 0.6.0 |
| `SynthetixDialogs` | bootstrap registry (see above) | 0.6.0 |

Left out on purpose: `dialog.file.*` (public so the config codecs can map them) and the `@ApiStatus.Internal` hooks
`DialogService#executor`, `#click`, `DialogType#preview`, `#attach`, `#click` (self-tests).

## Threads

`open`, `reopen`, `close` work from any thread. Every code callback (`action`, `submit`, `onClose`) runs on the player's
entity scheduler, whatever thread Paper fires the click on. `submitAsync` runs the handler on the async scheduler for slow
work (database); come back with `lib.scheduler().entity(player)` before touching the player. The library has no measure of
the thread Paper fires `ClickCallback` on; the hop makes that irrelevant.

## Pitfalls

- Wrong: trusting `min`/`max` of the file in your handler -> right: use `@Range` on the record and let the library
  re-validate; re-check game state too (the item is still in the hand) because the context is a snapshot (rule
  `dialog-validate-on-server`).
- Wrong: caching a `Dialog` or reusing buttons -> right: every `open` creates fresh one-use callbacks; a replayed or stale
  click does nothing (rule `dialog-one-use-callbacks`).
- Wrong: DB work in `submit` -> right: `submitAsync` (rule `threads-async-then-back`).
- Wrong: `can-close-with-escape: true` on a dialog you wait for -> right: `false`, or accept that no answer arrives.
- Wrong: putting a player-typed value into MiniMessage -> right: `Placeholder.unparsed` (rule `text-escape-player-input`).
- Wrong: using an anvil GUI for text input -> right: a dialog with a `text` input (rule `dialog-instead-of-anvil`).
- Wrong: input key `max-length` or `-`/`_` clashes: `my-key` and `my_key` are the same key on the wire; the parser rejects
  the second.

## Limits

- Needs a client 1.21.6+. No live update: changing anything means showing again (typed values come back as `initial`).
- Widths 1-1024; number inputs are floats; `multi-action` has `columns`. Values are clamped at load with an error.
- Bedrock (Geyser) support level is not established yet (the table in the design doc is pending).
- Configuration-phase dialogs and a built-in chat fallback do not exist.

Design: `docs/design/dialog.md`.
