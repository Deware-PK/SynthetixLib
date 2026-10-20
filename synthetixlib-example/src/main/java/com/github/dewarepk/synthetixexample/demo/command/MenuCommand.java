package com.github.dewarepk.synthetixexample.demo.command;

import com.github.dewarepk.synthetixexample.demo.DemoMenus;
import com.github.dewarepk.synthetixlib.command.CommandCall;
import com.github.dewarepk.synthetixlib.command.SubCommand;
import com.github.dewarepk.synthetixlib.command.SubSpec;

/** {@code /example menu}. */
final class MenuCommand implements SubCommand {

    private final DemoMenus menus;

    MenuCommand(final DemoMenus menus) {
        this.menus = menus;
    }

    @Override
    public SubSpec spec() {
        return SubSpec.literal("menu").description("Open the demo menu").playerOnly().executes(this::run);
    }

    private void run(final CommandCall ctx) {
        menus.open(ctx.player());
    }
}
