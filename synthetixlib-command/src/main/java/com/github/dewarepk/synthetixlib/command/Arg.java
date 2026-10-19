package com.github.dewarepk.synthetixlib.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A typed argument handle: declare it once, add it to a node with {@link SpecBuilder#argument}, read it back with
 * {@link CommandCall#get(Arg)}. Immutable.
 *
 * <pre>{@code
 * private final Arg<String> lot = Arg.of("lot", Args.word()).suggests(src -> auctions.openLotIds());
 * }</pre>
 *
 * @param <T> the Java value
 */
public final class Arg<T> {

    private final String name;
    private final ArgType<T> type;
    private final @Nullable Function<CommandSourceStack, ? extends Collection<String>> suggestions;

    private Arg(final String name, final ArgType<T> type,
                final @Nullable Function<CommandSourceStack, ? extends Collection<String>> suggestions) {
        this.name = name;
        this.type = type;
        this.suggestions = suggestions;
    }

    /**
     * @param name the Brigadier node name, {@code [a-z0-9_-]+}; unique within one command node
     * @param type how it is parsed, e.g. {@link Args#word()}
     * @param <T>  the Java value
     * @return a new handle
     * @throws IllegalArgumentException if {@code name} is not valid
     */
    public static <T> Arg<T> of(final String name, final ArgType<T> type) {
        return new Arg<>(Names.check(name, "argument name"), Objects.requireNonNull(type, "type"), null);
    }

    /**
     * Replaces the suggestions of this argument. Filtered by the typed prefix, case-insensitive. The function runs
     * on Paper's suggestion thread (may be async): read only thread-safe data, e.g. a snapshot of ids.
     *
     * @param suggestions the candidates for the sender of the tab-complete
     * @return a copy of this handle with the suggestions
     */
    public Arg<T> suggests(final Function<CommandSourceStack, ? extends Collection<String>> suggestions) {
        return new Arg<>(name, type, Objects.requireNonNull(suggestions, "suggestions"));
    }

    /** @return the Brigadier node name */
    public String name() {
        return name;
    }

    /** @return how the argument is parsed */
    public ArgType<T> type() {
        return type;
    }

    /** @return the suggestions set with {@link #suggests}, else the type's own, else {@code null} */
    @Nullable Function<CommandSourceStack, ? extends Collection<String>> effectiveSuggestions() {
        return suggestions != null ? suggestions : type.suggestions();
    }

    @Override
    public String toString() {
        return "Arg<" + name + ">";
    }
}
