package com.example.myplugin;

import com.github.dewarepk.synthetixlib.command.CommandSpec;
import com.github.dewarepk.synthetixlib.command.SubSpec;
import com.github.dewarepk.synthetixlib.config.ConfigHandle;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** {@code /myplugin menu|greet|reload}: native Brigadier through the command module. */
public final class MyPluginCommand {

    private MyPluginCommand() {
    }

    /** @return the spec to hand to {@code CommandService.register} */
    public static CommandSpec build(final ConfigHandle<Settings> settings, final LocaleHandle<Messages> lang,
                                    final MainMenu menu, final GreetDialog greet) {
        return CommandSpec.literal("myplugin")
                .aliases("mp")
                .description("MyPlugin")
                .sub(SubSpec.literal("menu").description("Open the menu").playerOnly()
                        .executes(ctx -> menu.open(ctx.player())))
                .sub(SubSpec.literal("greet").description("Open the greeting form").playerOnly()
                        .executes(ctx -> greet.open(ctx.player())))
                .sub(SubSpec.literal("reload").permission("myplugin.admin").allowConsole()
                        .description("Reload config and messages")
                        .executes(ctx -> {
                            final List<String> errors = new ArrayList<>(settings.reload().errors());
                            lang.reload().files().forEach((file, result) -> errors.addAll(result.errors()));
                            if (errors.isEmpty()) {
                                ctx.reply(lang, Messages::reloaded);
                            } else {
                                ctx.reply(lang, Messages::reloadFailed,
                                        Placeholder.unparsed("errors", String.join("; ", errors)));
                            }
                        }))
                .helpSub()
                .build();
    }
}
