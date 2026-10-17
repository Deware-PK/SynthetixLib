package com.github.dewarepk.synthetixlib.config;

import java.util.Objects;

/**
 * What happened to one object of a {@link ConfigCollection}; passed to {@link ConfigCollection#onChange}.
 *
 * <pre>{@code
 * airdrops.onChange(change -> switch (change) {
 *     case Change.Added<Airdrop> add     -> spawner.start(add.id(), add.value());
 *     case Change.Updated<Airdrop> upd   -> spawner.restart(upd.id(), upd.value());
 *     case Change.Removed<Airdrop> rem   -> spawner.stop(rem.id());
 * });
 * }</pre>
 *
 * <p>A rename is a {@link Removed} of the old id followed by an {@link Added} of the new one.
 *
 * @param <T> the object record
 */
public sealed interface Change<T> {

    /** @return the id of the object, i.e. its file name without {@code .yml} */
    String id();

    /**
     * A new object: created by code, or a file that appeared and was found by a reload.
     *
     * @param id    the id
     * @param value the object
     * @param <T>   the object record
     */
    record Added<T>(String id, T value) implements Change<T> {

        public Added {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * An existing object that now has a different value.
     *
     * @param id       the id
     * @param value    the new value
     * @param previous the value before
     * @param <T>      the object record
     */
    record Updated<T>(String id, T value, T previous) implements Change<T> {

        public Updated {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(previous, "previous");
        }
    }

    /**
     * An object that is gone: deleted by code, or its file disappeared and a reload noticed.
     *
     * @param id    the id
     * @param value the value it had
     * @param <T>   the object record
     */
    record Removed<T>(String id, T value) implements Change<T> {

        public Removed {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(value, "value");
        }
    }
}
