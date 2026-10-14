package com.github.dewarepk.synthetixlib.core.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Creates and tracks {@link TaskHandle}s for one library instance. Needs only a {@link Logger}, so the
 * scheduling/close logic is testable without a server (Paper's schedulers are passed in as {@link Launch}).
 */
final class TaskTracker {

    private static final String PACKAGE_PREFIX = TaskTracker.class.getPackageName() + ".";

    /** Creates the Paper task for a handle. Returns {@code null} if the target (entity) is already gone. */
    @FunctionalInterface
    interface Launch {
        @Nullable ScheduledTask launch(TaskHandle handle);
    }

    private final TaskRegistry<TaskHandle> registry = new TaskRegistry<>();
    private final Logger logger;
    private final boolean debug;

    TaskTracker(final Logger logger, final boolean debug) {
        this.logger = logger;
        this.debug = debug;
    }

    /**
     * Registers a new handle, then lets {@code launch} create the Paper task.
     *
     * @throws IllegalStateException if closed; nothing was scheduled then
     */
    TaskHandle submit(final String kind, final boolean repeating, final Consumer<SynthetixTask> body,
                      final @Nullable Runnable retired, final Launch launch) {
        final TaskHandle handle = new TaskHandle(registry, logger, kind, origin(), repeating, body);
        if (!registry.register(handle)) {
            throw new IllegalStateException("SynthetixLib is closed, cannot schedule a " + kind + " task");
        }
        final ScheduledTask task;
        try {
            task = launch.launch(handle);
        } catch (final RuntimeException | Error e) {
            handle.cancel();
            throw e;
        }
        if (task == null) {
            handle.retire(retired);
        } else {
            handle.attach(task);
        }
        return handle;
    }

    /** Cancels every live task and rejects further {@link #submit} calls. Idempotent. */
    void close() {
        for (final TaskHandle handle : registry.close()) {
            handle.cancel();
        }
    }

    boolean isClosed() {
        return registry.isClosed();
    }

    int liveCount() {
        return registry.size();
    }

    /** First stack frame outside this package; only computed with the debug flag (stack walking is not free). */
    private @Nullable String origin() {
        if (!debug) {
            return null;
        }
        return StackWalker.getInstance().walk(frames -> frames
                .dropWhile(frame -> frame.getClassName().startsWith(PACKAGE_PREFIX))
                .findFirst()
                .map(frame -> frame.toStackTraceElement().toString())
                .orElse(null));
    }
}
