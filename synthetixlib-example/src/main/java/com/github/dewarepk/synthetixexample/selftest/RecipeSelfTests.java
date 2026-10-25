package com.github.dewarepk.synthetixexample.selftest;

import static com.github.dewarepk.synthetixexample.selftest.SelfTest.require;

import com.github.dewarepk.synthetixexample.demo.recipes.CooldownRecipe;
import com.github.dewarepk.synthetixexample.demo.recipes.RecipeMessages;
import com.github.dewarepk.synthetixexample.demo.recipes.ReloadRecipe;
import com.github.dewarepk.synthetixexample.demo.recipes.ShopRecipe;
import com.github.dewarepk.synthetixlib.config.Configs;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import com.github.dewarepk.synthetixlib.database.Database;
import com.github.dewarepk.synthetixlib.database.DatabaseSettings;
import com.github.dewarepk.synthetixlib.dialog.DialogService;
import com.github.dewarepk.synthetixlib.gui.GuiService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Self-test of the recipes the skill documents: their files are copied from the jar and match the code that
 * registers the names they use, the SQL runs on SQLite, and the command spec builds.
 */
final class RecipeSelfTests {

    private RecipeSelfTests() {
    }

    static List<SelfTest> all(final JavaPlugin plugin, final SynthetixLib lib) {
        return List.of(SelfTest.async("recipes.build", () -> build(plugin, lib)));
    }

    private static CompletableFuture<?> build(final JavaPlugin plugin, final SynthetixLib lib) {
        try {
            final Path data = plugin.getDataFolder().toPath();
            for (final String file : List.of("menus/recipe-shop.yml", "menus/recipe-info.yml",
                    "dialogs/recipe-buy.yml", "dialogs/recipe-notice.yml", "recipes.yml", "selftest/recipes.db")) {
                Files.deleteIfExists(data.resolve(file));
            }
            final Configs configs = Configs.create(lib);
            final LocaleHandle<RecipeMessages> lang = configs.locale("recipes-lang", RecipeMessages.class,
                    RecipeMessages::defaults).defaultLocale(Locale.US).load();
            final GuiService gui = GuiService.create(lib, configs).messages(lang, RecipeMessages::gui);
            final DialogService dialogs = DialogService.create(lib, configs).messages(lang, RecipeMessages::dialog);
            gui.link("dialog", dialogs.opener());

            require("daily".equals(CooldownRecipe.daily(lang, Material.BREAD).name()), "the daily command builds");
            final ReloadRecipe reload = ReloadRecipe.create(lib, configs, lang, gui, dialogs);
            require(reload.settings().dailyAmount() == 1, "default settings");

            final Database db = Database.create(lib, DatabaseSettings.sqlite("selftest/recipes.db").withTablePrefix("rt_"));
            final ShopRecipe.Product bread = new ShopRecipe.Product("bread", Material.BREAD, 5);
            final UUID player = UUID.randomUUID();
            return db.migrate(ShopRecipe.migrations()).thenCompose(applied -> {
                final ShopRecipe shop = ShopRecipe.create(lib, gui, dialogs, db, lang, List.of(bread));
                require(shop.shop().file().items().size() >= 4, "shop menu file read");
                require(shop.buyDialog().id().equals("recipe-buy"), "buy dialog built");
                final List<String> problems = reload.reload();
                require(problems.isEmpty(), "files and registrations match: " + problems);
                return shop.record(player, bread, 3).thenCompose(rows -> shop.purchases(player));
            }).thenAccept(count -> require(count == 1L, "one purchase stored, got " + count));
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
