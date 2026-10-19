package com.github.dewarepk.synthetixlib.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * How one argument is parsed by the client (a native Brigadier/Paper type, so input is validated and coloured
 * there) and turned into a Java value. The extension point behind {@link Args}.
 *
 * @param <T> the Java value
 */
public interface ArgType<T> {

    /** @return the Brigadier type the client sees */
    ArgumentType<?> nativeType();

    /**
     * Reads the value of an argument that was typed. Runs on the thread of the command.
     *
     * @param context the Brigadier context of this execution
     * @param name    the argument name
     * @return the value, never {@code null}
     * @throws CommandSyntaxException if the typed text cannot be converted; the sender sees its message
     */
    T read(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException;

    /**
     * @return suggestions for this type, or {@code null} to keep what the native type offers; filtered by the typed
     *         prefix; may run on Paper's suggestion thread, so read only thread-safe data
     */
    default @Nullable Function<CommandSourceStack, ? extends Collection<String>> suggestions() {
        return null;
    }
}
