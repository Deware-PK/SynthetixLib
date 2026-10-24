package com.github.dewarepk.synthetixexample.demo.command;

import com.github.dewarepk.synthetixexample.demo.DemoDialogs;
import com.github.dewarepk.synthetixlib.command.SubCommand;
import com.github.dewarepk.synthetixlib.command.SubSpec;

/** {@code /example dialog <sell|confirm|menu>}: a group of three leaves. */
final class DialogCommand implements SubCommand {

    private final DemoDialogs dialogs;

    DialogCommand(final DemoDialogs dialogs) {
        this.dialogs = dialogs;
    }

    @Override
    public SubSpec spec() {
        return SubSpec.literal("dialog")
                .description("Open a demo dialog")
                .sub(SubSpec.literal("sell")
                        .description("A form with typed inputs; the item in your hand is the context")
                        .playerOnly()
                        .executes(ctx -> dialogs.openSell(ctx.player())))
                .sub(SubSpec.literal("confirm")
                        .description("A yes/no question")
                        .playerOnly()
                        .executes(ctx -> dialogs.openConfirm(ctx.player())))
                .sub(SubSpec.literal("menu")
                        .description("Buttons that open other dialogs and the demo menu")
                        .playerOnly()
                        .executes(ctx -> dialogs.openMenu(ctx.player())));
    }
}
