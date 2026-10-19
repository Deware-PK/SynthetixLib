package com.github.dewarepk.synthetixlib.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The specs of one service, and the rule that none may be added once Paper fired the COMMANDS event. */
final class CommandRegistry {

    private final List<CommandSpec> specs = new ArrayList<>();
    private boolean started;

    synchronized void register(final CommandSpec... added) {
        if (started) {
            throw new IllegalStateException("Commands can only be registered before the server registers them: "
                    + "register in onEnable, not later");
        }
        for (final CommandSpec spec : added) {
            specs.add(Objects.requireNonNull(spec, "spec"));
        }
    }

    /** Called by the COMMANDS handler; from now on {@link #register} throws. Safe to call on every event. */
    synchronized List<CommandSpec> start() {
        started = true;
        return List.copyOf(specs);
    }
}
