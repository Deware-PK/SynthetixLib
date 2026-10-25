package com.example.myplugin;

import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import com.github.dewarepk.synthetixlib.dialog.DialogService;
import com.github.dewarepk.synthetixlib.dialog.DialogType;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/**
 * The form {@code dialogs/greet.yml}. The file says what it looks like; this class only registers the code it
 * refers to by name ({@code action:greet}).
 */
public final class GreetDialog {

    /** The answers of the form; public so the config codecs can fill it. The component ids match {@code inputs:}. */
    public record GreetForm(String name) {
    }

    private final DialogType<Void> dialog;

    private GreetDialog(final DialogType<Void> dialog) {
        this.dialog = dialog;
    }

    /** The file is copied from the jar on the first start and never overwritten. */
    public static GreetDialog create(final DialogService dialogs, final LocaleHandle<Messages> lang) {
        return new GreetDialog(dialogs.dialog("greet")
                .submit("greet", GreetForm.class, (form, click) ->
                        lang.send(click.player(), Messages::greeted, Placeholder.unparsed("name", form.name())))
                .build());
    }

    /** Opens the form; any thread. */
    public void open(final Player player) {
        dialog.open(player);
    }
}
