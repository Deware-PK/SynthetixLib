package com.github.dewarepk.synthetixexample.demo.recipes;

import com.github.dewarepk.synthetixlib.command.CommandMessages;
import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import java.util.Map;

/** The messages of the three recipes; one file per language in {@code recipes-lang/}. */
public record RecipeMessages(@Comment("Messages of the command module itself") CommandMessages commands,
                             @Comment("Placeholders: <amount>, <product>") String bought,
                             String buyFailed,
                             @Comment("Placeholders: <item>") String daily,
                             String inventoryFull,
                             String reloaded,
                             @Comment("Placeholders: <errors>") String reloadFailed,
                             @Comment("Texts of the menus (menus/recipe-*.yml), used there as <lang:key>")
                             Map<String, String> gui,
                             @Comment("Texts of the dialogs (dialogs/recipe-*.yml), used there as <lang:key>")
                             Map<String, String> dialog) {

    public static RecipeMessages defaults() {
        return new RecipeMessages(
                CommandMessages.defaults(),
                "<green>Bought <amount> x <product>.",
                "<red>The purchase failed, see the console.",
                "<green>Here is your daily <item>.",
                "<red>Your inventory is full.",
                "<green>Reloaded.",
                "<red>Reload found errors: <errors>",
                Map.of("shop-title", "<dark_gray>Shop",
                        "shop-previous", "<yellow>Previous page",
                        "shop-next", "<yellow>Next page",
                        "shop-close", "<red>Close",
                        "info-title", "<dark_gray>Info",
                        "info-daily", "<gold>Daily reward:",
                        "info-notice", "<yellow>Read the notice"),
                Map.of("buy-title", "<gold>Buy <product>",
                        "buy-info", "<gray>Price per piece: <white><price></white> coins",
                        "buy-amount", "Amount",
                        "buy-yes", "<green>Buy",
                        "buy-no", "<red>Cancel",
                        "buy-error-amount", "<red>Choose between <min> and <max>",
                        "notice-title", "<gold>Notice",
                        "notice-text", "<gray>The daily reward is <white><daily></white> item(s).",
                        "notice-ok", "<green>OK"));
    }
}
