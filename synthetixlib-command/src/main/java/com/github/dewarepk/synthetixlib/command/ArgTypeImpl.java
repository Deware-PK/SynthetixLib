package com.github.dewarepk.synthetixlib.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;

/**
 * The {@link ArgType} behind most of {@link Args}. Besides the public contract it knows the {@link CommandHost}, which
 * the public {@link ArgType} methods cannot be given: {@link TreeCompiler} asks for the native type with the host (so a
 * conversion error is worded from the sender's language files) and {@link CommandCallImpl} reads values with it.
 *
 * <p>Immutable and without state: one instance is shared by every command that uses it.
 *
 * @param <T> the Java value
 */
final class ArgTypeImpl<T> implements ArgType<T> {

    /** Turns what Brigadier parsed into the value; runs on the thread of the command. */
    @FunctionalInterface
    interface Reader<T> {

        T read(CommandContext<CommandSourceStack> context, String name, @Nullable CommandHost host)
                throws CommandSyntaxException;
    }

    private final Function<@Nullable CommandHost, ArgumentType<?>> nativeType;
    private final Reader<T> reader;
    private final @Nullable Function<CommandSourceStack, ? extends Collection<String>> suggestions;

    private ArgTypeImpl(final Function<@Nullable CommandHost, ArgumentType<?>> nativeType, final Reader<T> reader,
                        final @Nullable Function<CommandSourceStack, ? extends Collection<String>> suggestions) {
        this.nativeType = nativeType;
        this.reader = reader;
        this.suggestions = suggestions;
    }

    // --- factories ----------------------------------------------------------------------------------------------

    /** A native type created on demand (Paper's {@code ArgumentTypes} need a running server), read by {@code reader}. */
    static <T> ArgTypeImpl<T> lazy(final Supplier<? extends ArgumentType<?>> nativeType, final Reader<T> reader) {
        return new ArgTypeImpl<>(host -> nativeType.get(), reader, null);
    }

    /** A native type whose parsed value already is a {@code valueType}. */
    static <T> ArgTypeImpl<T> direct(final Supplier<? extends ArgumentType<?>> nativeType,
                                     final Class<T> valueType) {
        return lazy(nativeType, (context, name, host) -> context.getArgument(name, valueType));
    }

    /**
     * A native type of {@code N} turned into a {@code T} on the server while the command is parsed. A converter that
     * throws {@link IllegalArgumentException} rejects the input with {@link CommandMessages#invalidValue()}, its
     * message being the reason.
     */
    static <N, T> ArgTypeImpl<T> converted(final Supplier<? extends ArgumentType<N>> nativeType,
                                           final Function<N, T> converter,
                                           final @Nullable Function<CommandSourceStack, ? extends Collection<String>>
                                                   suggestions) {
        return new ArgTypeImpl<>(host -> new Parsed<>(nativeType.get(), converter, host),
                (context, name, host) -> {
                    @SuppressWarnings("unchecked")
                    final T value = (T) context.getArgument(name, Object.class);
                    return value;
                }, suggestions);
    }

    /** The same type with other suggestions. */
    ArgTypeImpl<T> withSuggestions(
            final @Nullable Function<CommandSourceStack, ? extends Collection<String>> suggestions) {
        return new ArgTypeImpl<>(nativeType, reader, suggestions);
    }

    // --- ArgType ------------------------------------------------------------------------------------------------

    @Override
    public ArgumentType<?> nativeType() {
        return nativeType.apply(null);
    }

    @Override
    public T read(final CommandContext<CommandSourceStack> context, final String name) throws CommandSyntaxException {
        return reader.read(context, name, null);
    }

    @Override
    public @Nullable Function<CommandSourceStack, ? extends Collection<String>> suggestions() {
        return suggestions;
    }

    // --- used by the compiler and the call ----------------------------------------------------------------------

    /** @return the native type for the Brigadier tree of {@code host}; conversion errors are worded through it */
    static ArgumentType<?> nativeOf(final ArgType<?> type, final CommandHost host) {
        return type instanceof ArgTypeImpl<?> impl ? impl.nativeType.apply(host) : type.nativeType();
    }

    /** @return the value of argument {@code name}; errors are worded through {@code host} */
    static <T> T readFrom(final ArgType<T> type, final CommandContext<CommandSourceStack> context, final String name,
                          final CommandHost host) throws CommandSyntaxException {
        return type instanceof ArgTypeImpl<T> impl ? impl.reader.read(context, name, host)
                : type.read(context, name);
    }

    /** Paper's way to convert on the server what a vanilla type parsed; the client only ever sees {@code N}. */
    private static final class Parsed<N, T> implements CustomArgumentType.Converted<T, N> {

        private final ArgumentType<N> nativeType;
        private final Function<N, T> converter;
        private final @Nullable CommandHost host;

        private Parsed(final ArgumentType<N> nativeType, final Function<N, T> converter,
                       final @Nullable CommandHost host) {
            this.nativeType = nativeType;
            this.converter = converter;
            this.host = host;
        }

        @Override
        public ArgumentType<N> getNativeType() {
            return nativeType;
        }

        @Override
        public T convert(final N value) throws CommandSyntaxException {
            return convert(value, (Object) null);
        }

        /** Paper passes the {@code CommandSourceStack}; plain Brigadier (unit tests) passes nothing. */
        @Override
        public <S> T convert(final N value, final S source) throws CommandSyntaxException {
            final CommandSender sender = source instanceof CommandSourceStack stack ? stack.getSender() : null;
            final T converted;
            try {
                converted = converter.apply(value);
            } catch (final IllegalArgumentException e) {
                throw ArgErrors.invalid(host, sender, String.valueOf(value),
                        e.getMessage() == null ? "" : e.getMessage());
            }
            if (converted == null) {
                throw ArgErrors.invalid(host, sender, String.valueOf(value), "");
            }
            return converted;
        }
    }
}
