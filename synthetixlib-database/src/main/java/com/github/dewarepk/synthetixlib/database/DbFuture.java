package com.github.dewarepk.synthetixlib.database;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * The future of a database call. Behaves like any {@link CompletableFuture}; {@code join()} and {@code get()} first ask
 * the {@link ServerThreadGuard} (futures made from this one with {@code thenApply} and friends do the same) and refuse
 * to wait on a worker thread of the same database, where the wait could never end.
 *
 * @param <T> the result type
 */
final class DbFuture<T> extends CompletableFuture<T> {

    private static final String OWN_WORKER = "waiting on the database from its own worker would deadlock; "
            + "chain with thenCompose/thenAccept";

    private final ServerThreadGuard guard;
    private final BooleanSupplier onOwnWorker;

    /**
     * @param guard       warns when a server thread waits
     * @param onOwnWorker whether the current thread is a worker of the database that made this future
     */
    DbFuture(final ServerThreadGuard guard, final BooleanSupplier onOwnWorker) {
        this.guard = guard;
        this.onOwnWorker = onOwnWorker;
    }

    private void beforeBlocking() {
        if (onOwnWorker.getAsBoolean()) {
            throw new IllegalStateException(OWN_WORKER);
        }
        guard.beforeBlocking();
    }

    @Override
    public T join() {
        beforeBlocking();
        return super.join();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        beforeBlocking();
        return super.get();
    }

    @Override
    public T get(final long timeout, final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        beforeBlocking();
        return super.get(timeout, unit);
    }

    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() {
        return new DbFuture<>(guard, onOwnWorker);
    }
}
