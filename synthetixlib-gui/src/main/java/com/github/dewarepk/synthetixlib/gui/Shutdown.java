package com.github.dewarepk.synthetixlib.gui;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Closing every open view when the service closes. The order is the point: while a menu is still open the listener must still
 * be cancelling clicks on it, so the listener stops last, and a view that cannot be closed is made harmless instead of
 * stopping the others.
 */
final class Shutdown {

    private Shutdown() {
    }

    /**
     * Closes every view, then stops listening, whatever happened to the views.
     *
     * @param views         the open views
     * @param closeOne      closes one view (or schedules it); may throw
     * @param whenItFails   gets a view whose {@code closeOne} threw, and the exception: make it harmless
     * @param stopListening unregisters the listener; runs after the last view, even if one of them threw
     */
    static <V> void run(final Collection<V> views, final Consumer<V> closeOne,
                        final BiConsumer<V, RuntimeException> whenItFails, final Runnable stopListening) {
        try {
            for (final V view : views) {
                try {
                    closeOne.accept(view);
                } catch (final RuntimeException e) {
                    whenItFails.accept(view, e);
                }
            }
        } finally {
            stopListening.run();
        }
    }
}
