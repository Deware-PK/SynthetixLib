package com.github.dewarepk.synthetixexample.demo;

import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.dialog.DialogService;
import com.github.dewarepk.synthetixlib.dialog.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The dialogs of the demo ({@code dialogs/sell.yml}, {@code confirm.yml} and {@code menu.yml}). The files say what
 * everything looks like; this class only registers the code they refer to by name.
 */
public final class DemoDialogs {

    // @snippet:dialog-form-record
    /** The answers of {@code sell.yml}; public so the config codecs can fill it. The price is checked against the file. */
    public record SellForm(double price, String duration, String note, boolean anonymous) {
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
    public static DemoDialogs create(final DialogService dialogs, final LocaleHandle<Messages> lang) {
        // @snippet:dialog-submit
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
        // @end
        final DialogType<Void> confirm = dialogs.dialog("confirm")
                .action("confirmed", click -> lang.send(click.player(), Messages::dialogConfirmed))
                .build();
        final DialogType<Void> menu = dialogs.dialog("menu").build();
        return new DemoDialogs(sell, confirm, menu);
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
