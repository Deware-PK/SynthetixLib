package com.github.dewarepk.synthetixexample.demo.recipes;

import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.database.Database;
import com.github.dewarepk.synthetixlib.database.Migrations;
import com.github.dewarepk.synthetixlib.dialog.DialogClick;
import com.github.dewarepk.synthetixlib.dialog.DialogService;
import com.github.dewarepk.synthetixlib.dialog.DialogType;
import com.github.dewarepk.synthetixlib.gui.GuiService;
import com.github.dewarepk.synthetixlib.gui.MenuClick;
import com.github.dewarepk.synthetixlib.gui.MenuType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Recipe 1: a shop menu, a buy dialog and a SQL table. The files ({@code menus/recipe-shop.yml},
 * {@code dialogs/recipe-buy.yml}) say what everything looks like; this class registers the code they refer to by name.
 */
public final class ShopRecipe {

    /** One product of the shop. */
    public record Product(String id, Material material, long price) {
    }

    /** The answers of {@code recipe-buy.yml}; public so the config codecs can fill it. */
    public record BuyForm(int amount) {
    }

    private final SynthetixLib lib;
    private final Database db;
    private final LocaleHandle<RecipeMessages> lang;
    private final MenuType<Void> shop;
    private final DialogType<Product> buy;

    private ShopRecipe(final SynthetixLib lib, final Database db, final LocaleHandle<RecipeMessages> lang,
                       final MenuType<Void> shop, final DialogType<Product> buy) {
        this.lib = lib;
        this.db = db;
        this.lang = lang;
        this.shop = shop;
        this.buy = buy;
    }

    /** @return the migrations of this recipe; run them (and wait) before {@link #create} */
    public static Migrations migrations() {
        return Migrations.fromResources(ShopRecipe.class.getClassLoader(), "db/recipes");
    }

    // @snippet:recipe-shop-create
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
    // @end

    // @snippet:recipe-shop-purchase
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
    // @end

    /** @return the shop menu */
    public MenuType<Void> shop() {
        return shop;
    }

    /** @return the buy dialog */
    public DialogType<Product> buyDialog() {
        return buy;
    }

    /** Opens the shop; any thread. */
    public void open(final Player player) {
        shop.open(player);
    }
}
