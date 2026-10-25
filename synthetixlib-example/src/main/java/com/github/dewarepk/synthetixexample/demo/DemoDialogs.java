package com.github.dewarepk.synthetixexample.demo;

import com.github.dewarepk.synthetixlib.config.ConfigHandle;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.dialog.DialogService;
import com.github.dewarepk.synthetixlib.dialog.DialogType;
import com.github.dewarepk.synthetixlib.dialog.FieldError;
import com.github.dewarepk.synthetixlib.dialog.Range;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The dialogs of the demo ({@code dialogs/sell.yml}, {@code confirm.yml} and {@code menu.yml}). The files say what
 * everything looks like; this class only registers the code they refer to by name.
 */
public final class DemoDialogs {

    private static final long BASE_VALUE = 100;

    // @snippet:dialog-form-record
    /** The answers of {@code sell.yml}; public so the config codecs can fill it. The price is a typed whole number. */
    public record SellForm(long price, String duration, String note, boolean anonymous) {
    }
    // @end

    private final DialogType<ItemStack> sell;
    private final DialogType<Void> confirm;
    private final DialogType<Void> menu;

    private DemoDialogs(final DialogType<ItemStack> sell, final DialogType<Void> confirm, final DialogType<Void> menu) {
        this.sell = sell;
        this.confirm = confirm;
        this.menu = menu;
    }

    /** Builds the three dialogs; their files are copied from the jar on the first start and never overwritten. */
    public static DemoDialogs create(final DialogService dialogs, final LocaleHandle<Messages> lang,
                                     final ConfigHandle<Settings> settings) {
        // @snippet:dialog-submit
        final DialogType<ItemStack> sell = dialogs.dialog("sell").<ItemStack>withContext()
                .placeholder("item-name", view -> itemName(view.context()))
                // limits from settings.yml, read for every showing and again when the answer is checked
                .inputRange("price", view -> Range.of(settings.get().sellMinPrice(), settings.get().sellMaxPrice()))
                .submit("sell", SellForm.class, (form, click) -> {
                    final Player player = click.player();
                    lang.send(player, Messages::dialogSold,
                            Placeholder.component("item", itemName(click.view().context())),
                            Placeholder.unparsed("price", String.valueOf(form.price())),
                            Placeholder.unparsed("duration", form.duration()),
                            Placeholder.unparsed("note", form.note().isBlank() ? "-" : form.note()),
                            Placeholder.unparsed("who", form.anonymous() ? "anonymous" : player.getName()));
                })
                // a check that needs code: not more than 10 x the base value of the stack (a demo rule)
                .validate("sell", SellForm.class, (form, click) -> {
                    final long limit = maxPrice(click.view().context());
                    return form.price() > limit
                            ? Optional.of(FieldError.of("price", "<lang:sell-error-too-high>",
                                    Placeholder.unparsed("limit", String.valueOf(limit))))
                            : Optional.empty();
                })
                .build();
        // @end
        final DialogType<Void> confirm = dialogs.dialog("confirm")
                .action("confirmed", click -> lang.send(click.player(), Messages::dialogConfirmed))
                .build();
        final DialogType<Void> menu = dialogs.dialog("menu").build();
        return new DemoDialogs(sell, confirm, menu);
    }

    /** The most a stack may be sold for: 10 times its base value, which the demo sets to 100 coins per item. */
    private static long maxPrice(final ItemStack item) {
        return item == null || item.isEmpty() ? 0 : 10L * BASE_VALUE * item.getAmount();
    }

    private static Component itemName(final ItemStack item) {
        return item == null || item.isEmpty() ? Component.text("nothing") : item.effectiveName();
    }

    /** Opens the sell form with the item in the player's hand as its context; any thread. */
    public void openSell(final Player player) {
        sell.open(player, player.getInventory().getItemInMainHand().clone());
    }

    /** Opens the yes/no question; any thread. */
    public void openConfirm(final Player player) {
        confirm.open(player);
    }

    /** Opens the dialog with the buttons for the other dialogs and the gui menu; any thread. */
    public void openMenu(final Player player) {
        menu.open(player);
    }
}
