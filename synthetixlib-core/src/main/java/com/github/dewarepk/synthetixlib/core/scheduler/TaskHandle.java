package com.github.dewarepk.synthetixlib.core.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Our {@link SynthetixTask}: a small state machine around Paper's {@link ScheduledTask}.
 *
 * <p>States: PENDING -> COMPLETED (one-shot ran) or CANCELLED (cancel / close / retired). Both
 * transitions are compare-and-set, so exactly one wins. The Paper task is attached after scheduling
 * ({@link #attach}); {@link #cancel} and {@link #attach} each write one volatile and read the other,
 * so a cancel that races with scheduling is always seen by one of them.
 */
final class TaskHandle implements SynthetixTask {

    private static final int PENDING = 0;
    private static final int COMPLETED = 1;
    private static final int CANCELLED = 2;

    private final AtomicInteger state = new AtomicInteger(PENDING);
    private final TaskRegistry<TaskHandle> registry;
    private final Logger logger;
    private final String kind;
    private final @Nullable String origin;
    private final boolean repeating;
    private final Consumer<SynthetixTask> body;
    private volatile @Nullable ScheduledTask scheduled;

    TaskHandle(final TaskRegistry<TaskHandle> registry, final Logger logger, final String kind,
               final @Nullable String origin, final boolean repeating, final Consumer<SynthetixTask> body) {
        this.registry = registry;
        this.logger = logger;
        this.kind = kind;
        this.origin = origin;
        this.repeating = repeating;
        this.body = body;
    }

    /** Called once after the Paper task was created. Cancels it at once if we were cancelled meanwhile. */
    void attach(final ScheduledTask task) {
        scheduled = task;
        if (state.get() == CANCELLED) {
            task.cancel();
        }
    }

    /** Entry point handed to Paper. */
    void execute(final ScheduledTask ignored) {
        if (state.get() != PENDING) {
            return;
        }
        try {
            body.accept(this);
        } catch (final VirtualMachineError fatal) {
            throw fatal;
        } catch (final Throwable t) {
            report("Exception in " + kind + " task", t);
        } finally {
            if (!repeating && state.compareAndSet(PENDING, COMPLETED)) {
                registry.unregister(this);
            }
        }
    }

    /**
     * The entity was removed before the task ran. Cancels the task and runs {@code retired}, unless
     * the task was already finished or cancelled by someone else.
     */
    void retire(final @Nullable Runnable retired) {
        if (!state.compareAndSet(PENDING, CANCELLED)) {
            return;
        }
        registry.unregister(this);
        final ScheduledTask task = scheduled;
        if (task != null) {
            task.cancel();
        }
        if (retired != null) {
            try {
                retired.run();
            } catch (final VirtualMachineError fatal) {
                throw fatal;
            } catch (final Throwable t) {
                report("Exception in retired callback of " + kind + " task", t);
            }
        }
    }

    @Override
    public void cancel() {
        if (state.compareAndSet(PENDING, CANCELLED)) {
            registry.unregister(this);
        }
        if (state.get() == CANCELLED) {
            final ScheduledTask task = scheduled;
            if (task != null) {
                task.cancel();
            }
        }
    }

    @Override
    public boolean isCancelled() {
        return state.get() == CANCELLED;
    }

    @Override
    public boolean isDone() {
        return state.get() != PENDING;
    }

    private void report(final String message, final Throwable t) {
        logger.log(Level.SEVERE, origin == null ? message : message + " (scheduled at " + origin + ")", t);
    }
}
