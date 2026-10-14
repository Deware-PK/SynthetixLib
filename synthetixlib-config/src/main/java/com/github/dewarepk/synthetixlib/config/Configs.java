package com.github.dewarepk.synthetixlib.config;

import com.github.dewarepk.synthetixlib.core.SynthetixLib;
import java.util.Objects;

/**
 * Entry point of the config module; one instance per {@link SynthetixLib}, closed with it.
 *
 * <pre>{@code
 * Configs configs = Configs.create(lib);
 * }</pre>
 */
public final class Configs implements AutoCloseable {

    private final SynthetixLib lib;
    private volatile boolean closed;

    private Configs(final SynthetixLib lib) {
        this.lib = lib;
    }

    /**
     * Creates the config service and attaches it to {@code lib}, so {@code lib.close()} closes it.
     *
     * @param lib the owning library instance
     * @return a new service bound to {@code lib}
     * @throws IllegalStateException if {@code lib} is already closed
     */
    public static Configs create(final SynthetixLib lib) {
        Objects.requireNonNull(lib, "lib");
        final Configs configs = new Configs(lib);
        lib.attach(configs);
        return configs;
    }

    /** @return the library instance this service belongs to */
    public SynthetixLib lib() {
        return lib;
    }

    /** @return {@code true} after {@link #close()} */
    public boolean isClosed() {
        return closed;
    }

    /** Releases the service. Idempotent; called by {@code lib.close()}. */
    @Override
    public void close() {
        closed = true;
    }
}
