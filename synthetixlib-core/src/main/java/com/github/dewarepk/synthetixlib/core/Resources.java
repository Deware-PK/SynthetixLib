package com.github.dewarepk.synthetixlib.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Resources attached to one {@link SynthetixLib}; closed in reverse registration order. */
final class Resources {

    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private final BiConsumer<AutoCloseable, Throwable> onError;
    private boolean closed;

    Resources(final BiConsumer<AutoCloseable, Throwable> onError) {
        this.onError = onError;
    }

    synchronized void attach(final AutoCloseable resource) {
        Objects.requireNonNull(resource, "resource");
        if (closed) {
            throw new IllegalStateException("SynthetixLib is closed");
        }
        resources.push(resource);
    }

    /** Closes everything attached (last attached first). A failing resource does not stop the others. */
    void close() {
        final Deque<AutoCloseable> toClose;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            toClose = new ArrayDeque<>(resources);
            resources.clear();
        }
        for (final AutoCloseable resource : toClose) {
            try {
                resource.close();
            } catch (final Exception t) {
                onError.accept(resource, t);
            }
        }
    }
}
