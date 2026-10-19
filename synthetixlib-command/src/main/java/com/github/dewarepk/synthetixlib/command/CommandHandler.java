package com.github.dewarepk.synthetixlib.command;

/**
 * What a command node runs. Throw {@code ctx.fail(...)} to stop with a message; any other exception is logged and
 * the sender gets {@link CommandMessages#error()}.
 *
 * <pre>{@code
 * SubSpec.literal("reload").executes(ctx -> {
 *     config.reload();
 *     ctx.reply(lang, Messages::reloaded);
 * });
 * }</pre>
 */
@FunctionalInterface
public interface CommandHandler {

    /**
     * @param call the context of this execution; never shared with another execution
     */
    void handle(CommandCall call);
}
