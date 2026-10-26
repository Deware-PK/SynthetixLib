# Recipes

Four end-to-end recipes, each built from code that compiles and runs in the example plugin's `recipes.build`
self-test (`synthetixlib-example/.../demo/recipes`). Copy the shape, not the names. Every recipe uses the same wiring:
one `Configs`, one `LocaleHandle<RecipeMessages>` (all text in `recipes-lang/<tag>.yml`), a `GuiService` and a
`DialogService` that link to each other.

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

## Recipe 1: shop menu + buy dialog + SQLite

Flow: `/shop` opens a paginated menu of products; clicking a product opens a dialog asking for the amount; submitting writes a
row to SQLite on the database worker and gives the item back on the player's own thread.

Files the buyer can edit (`menus/recipe-shop.yml`, `dialogs/recipe-buy.yml`):

```yaml snippet=recipe-shop-yml
title: '<lang:shop-title>'
type: chest
rows: 3
layout:
  - "#########"
  - "#LLLLLLL#"
  - "<###X###>"
items:
  '#':
    material: gray_stained_glass_pane
    hide-tooltip: true
  'L':
    list: products
    empty: { material: air }
  '<':
    material: arrow
    name: '<lang:shop-previous>'
    hide-when: first-page
    click: [page-previous]
  '>':
    material: arrow
    name: '<lang:shop-next>'
    hide-when: last-page
    click: [page-next]
  'X':
    material: barrier
    name: '<lang:shop-close>'
    click: [close]
```

```yaml snippet=recipe-buy-yml
config-version: 1
title: '<lang:buy-title>'
type: confirmation
body:
  - message: '<lang:buy-info>'
    width: 250
inputs:
  amount:
    type: number
    label: '<lang:buy-amount>'
    min: 1
    max: 64
    step: 1
    initial: 1
buttons:
  yes: { label: '<lang:buy-yes>', click: ['action:buy'] }
  no: { label: '<lang:buy-no>', click: [close] }
errors:
  amount: '<lang:buy-error-amount>'
```

The code registers the names they use (the list `products`, the action `buy`, the placeholders):

```java snippet=recipe-shop-create
public static ShopRecipe create(final SynthetixLib lib, final GuiService gui, final DialogService dialogs,
                                final Database db, final LocaleHandle<RecipeMessages> lang,
                                final List<Product> products) {
    final ShopRecipe[] self = new ShopRecipe[1];

    final DialogType<Product> buy = dialogs.dialog("recipe-buy").<Product>withContext()
            .placeholder("product", view -> Component.text(view.context().id()))
            .placeholder("price", view -> Component.text(view.context().price()))
            .submitAsync("buy", BuyForm.class, (form, click) -> self[0].purchase(form, click))
            .build();

    final MenuType<Void> shop = gui.menu("recipe-shop")
            .list("products", view -> products, (product, view) -> lib.items().of(product.material())
                    .name("<white>" + lib.text().escape(product.id()))
                    .lore("<gray>Price: <white><price>", Placeholder.unparsed("price", String.valueOf(product.price())))
                    .build())
            .onListClick("products", (Product product, MenuClick<Void> click) -> buy.open(click.player(), product))
            .build();

    self[0] = new ShopRecipe(lib, db, lang, shop, buy);
    return self[0];
}
```

The handler runs on an async thread (`submitAsync`), writes with the database, then hops to the player:

```java snippet=recipe-shop-purchase
/** Runs on an async thread (submitAsync); the answer goes back to the player's own thread. */
private void purchase(final BuyForm form, final DialogClick<Product> click) {
    final Player player = click.player();
    final Product product = click.view().context();
    record(player.getUniqueId(), product, form.amount()).whenComplete((rows, failure) ->
            lib.scheduler().entity(player).run(() -> {
                if (failure != null) {
                    lib.plugin().getLogger().warning("purchase failed: " + failure);
                    lang.send(player, RecipeMessages::buyFailed);
                    return;
                }
                player.getInventory().addItem(new ItemStack(product.material(), form.amount()));
                lang.send(player, RecipeMessages::bought,
                        Placeholder.unparsed("amount", String.valueOf(form.amount())),
                        Placeholder.unparsed("product", product.id()));
            }));
}

/** Writes one purchase; the table name gets the configured prefix through {@code {prefix}}. */
public CompletableFuture<Integer> record(final UUID player, final Product product, final int amount) {
    return db.update("INSERT INTO {prefix}purchases (player, product, amount, paid, bought_at) VALUES (?, ?, ?, ?, ?)",
            player, product.id(), amount, product.price() * amount, Instant.now());
}

/** @return how many purchases this player made */
public CompletableFuture<Long> purchases(final UUID player) {
    return db.queryOne("SELECT COUNT(*) AS n FROM {prefix}purchases WHERE player = ?",
                    row -> row.longValue("n"), player)
            .thenApply(count -> count.orElse(0L));
}
```

Details that matter:

- `submitAsync` is for slow work; the form record (`BuyForm`) is public and its component `amount` matches `inputs.amount`. The
  `number` input has `min: 1`, `max: 64` in the file; the server re-checks them and re-shows the dialog with
  `errors.amount` if a modified client sends something else.
- The context of the buy dialog is the clicked `Product`, passed from `onListClick`. `view.context()` is a snapshot: if the
  purchase must take money or an item, re-check that state in the handler.
- The migration `db/recipes/V1__purchases.sql` (plus a `.mysql.sql` override for `AUTO_INCREMENT`) creates `{prefix}purchases`:

```sql snippet=recipe-migration
CREATE TABLE {prefix}purchases (
    id        INTEGER PRIMARY KEY,
    player    CHAR(36)    NOT NULL,
    product   VARCHAR(64) NOT NULL,
    amount    INTEGER     NOT NULL,
    paid      BIGINT      NOT NULL,
    bought_at BIGINT      NOT NULL
);
```

  Run `db.migrate(ShopRecipe.migrations())` in `onEnable` (blocking is fine there) before `ShopRecipe.create(...)`.
- Open the shop from a command with `ctx.player()`; `open` is thread-safe.

## Recipe 2: per-player cooldown command with locale

`/daily` gives an item once per 24 hours per player; moderators with a permission skip the cooldown; every message comes
from the language file; a full inventory fails without using the cooldown.

```java snippet=recipe-cooldown
public static CommandSpec daily(final LocaleHandle<RecipeMessages> lang, final Material reward) {
    return CommandSpec.literal("daily")
            .aliases("dailyreward")
            .description("Claim the daily reward")
            .playerOnly()
            .cooldown(Duration.ofHours(24), "recipes.bypass")
            .executes(ctx -> {
                if (ctx.player().getInventory().firstEmpty() == -1) {
                    throw ctx.fail(lang, RecipeMessages::inventoryFull);     // a failure does not start the cooldown
                }
                ctx.player().getInventory().addItem(new ItemStack(reward));
                ctx.reply(lang, RecipeMessages::daily, Placeholder.unparsed("item", reward.key().value()));
            })
            .build();
}
```

- `cooldown(Duration, bypassPermission)` is per player and per executable node, in memory (a restart clears it).
- `throw ctx.fail(lang, ...)` sends the message, stops the handler and does **not** start the cooldown; a normal return does.
- The message the player gets while waiting is `CommandMessages.cooldown` (`<time>`), nested in `RecipeMessages` and passed
  with `.messages(lang, RecipeMessages::commands)` on the `CommandService`.
- Register in `onEnable`: `commandService.register(CooldownRecipe.daily(lang, Material.BREAD))`.

## Recipe 3: reloadable config + menu + dialog

One `/recipes reload` re-reads the config, the language files, the menu files and the dialog files and reports every
problem. Menus and dialogs read the config snapshot through placeholders, so a reload shows at once.

```java snippet=recipe-reload-create
public static ReloadRecipe create(final SynthetixLib lib, final Configs configs,
                                  final LocaleHandle<RecipeMessages> lang, final GuiService gui,
                                  final DialogService dialogs) {
    final ConfigHandle<RecipeSettings> settings =
            configs.file("recipes.yml", RecipeSettings.class, RecipeSettings::defaults).load();
    settings.onReload(next -> lib.plugin().getLogger().info("daily amount is now " + next.dailyAmount()));

    // The placeholders read the snapshot each time a menu or dialog is drawn, so a reload shows at once.
    final MenuType<Void> info = gui.menu("recipe-info")
            .placeholder("daily", view -> Component.text(settings.get().dailyAmount()))
            .build();
    dialogs.dialog("recipe-notice")
            .placeholder("daily", view -> Component.text(settings.get().dailyAmount()))
            .build();
    return new ReloadRecipe(settings, lang, gui, dialogs, info);
}
```

```java snippet=recipe-reload
/** @return every problem found while re-reading; empty if all went well */
public List<String> reload() {
    final List<String> errors = new ArrayList<>(settings.reload().errors());
    errors.addAll(lang.reload().errors());
    errors.addAll(gui.reload().errors());
    errors.addAll(dialogs.reload().errors());
    return errors;
}
```

- `ConfigHandle#reload` never rewrites the file; invalid values keep their text and fall back to the default plus an error.
- The collected `errors()` are strings that start with the path of the bad key; send them with
  `lang.send(sender, RecipeMessages::reloadFailed, Placeholder.unparsed("errors", String.join("; ", errors)))`.
- Open views redraw on the next refresh; a dialog that was opened before a reload which changed its file stops working (an
  unchanged reload keeps it working).
- Commands are not re-registered by a reload (Paper has no runtime registration).

## Recipe 4: unit-test your store

A store that takes a `Database` is tested in plain JUnit with `Database.standalone`: a temp folder, real SQLite, the real
migrations from your resources, no server. (`ItemStack` columns need a server: test those in a self-test.)

```kotlin
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.zaxxer:HikariCP:7.1.0")
    testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")
}
tasks.test { useJUnitPlatform() }
```

```java snippet=recipe-store-test
@TempDir
Path dir;

@Test
void claimsAreCounted() {
    try (Database db = Database.standalone("store-test", dir, Logger.getLogger("test"),
            DatabaseSettings.sqlite("test.db").withTablePrefix("t_"))) {
        final ClaimStore store = ClaimStore.open(db);   // runs the migrations from the resources

        store.claim("daily", UUID.randomUUID()).join();
        store.claim("daily", UUID.randomUUID()).join();

        assertEquals(2, store.stats("daily", 10).join().count());
    }
}
```

- Close the database (try-with-resources) so its worker thread stops; `close` waits for accepted writes.
- Call `join()` freely in tests; the server-thread warning does not exist here.
