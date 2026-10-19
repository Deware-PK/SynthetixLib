package com.github.dewarepk.synthetixlib.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.command.CommandFixtures.TestHost;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The COMMANDS-event rules: register before it, rebuild the same tree on every event. */
class CommandRegistryTest {

    private static final CommandHandler NOOP = ctx -> {
    };

    private static CommandSpec spec(final String name) {
        return CommandSpec.literal(name).executes(NOOP).sub(SubSpec.literal("sub").executes(NOOP)).build();
    }

    @Test
    void registerAfterTheEventThrowsAndSaysWhatToDo() {
        final CommandRegistry registry = new CommandRegistry();
        registry.register(spec("one"));
        registry.start();
        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> registry.register(spec("two")));
        assertTrue(e.getMessage().contains("onEnable"), e.getMessage());
    }

    @Test
    void theHandlerMayRunTwiceAndBuildsTheSameTree() {
        final CommandRegistry registry = new CommandRegistry();
        registry.register(spec("one"), spec("two"));
        final TestHost host = new TestHost();

        final List<String> first = registry.start().stream().map(s -> CommandFixtures.shape(s.compile(host))).toList();
        final List<String> second = registry.start().stream().map(s -> CommandFixtures.shape(s.compile(host))).toList();
        assertEquals(List.of("one*(sub*)", "two*(sub*)"), first);
        assertEquals(first, second);
    }

    @Test
    void specsCanBeRegisteredInSeveralCalls() {
        final CommandRegistry registry = new CommandRegistry();
        registry.register(spec("one"));
        registry.register(spec("two"));
        assertEquals(List.of("one", "two"), registry.start().stream().map(CommandSpec::name).toList());
    }
}
