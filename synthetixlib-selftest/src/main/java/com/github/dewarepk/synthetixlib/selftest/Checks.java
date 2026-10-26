package com.github.dewarepk.synthetixlib.selftest;

import java.util.Objects;

/**
 * Small assertions for self-tests; they throw {@link AssertionError} with a readable message (no JUnit at runtime).
 *
 * <pre>{@code
 * Checks.equal(64, stack.getAmount(), "amount after merge");
 * Checks.isTrue(menu.isOpen(), "menu open");
 * Checks.throwsType(IllegalArgumentException.class, () -> config.parse("x: ["));
 * }</pre>
 */
public final class Checks {

    private Checks() {
    }

    /**
     * @param expected the wanted value
     * @param actual   the real value
     * @param what     what is compared, shown in the message
     */
    public static void equal(final Object expected, final Object actual, final String what) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    /**
     * @param condition must be true
     * @param what      what is checked, shown in the message
     */
    public static void isTrue(final boolean condition, final String what) {
        if (!condition) {
            throw new AssertionError(what + ": expected true but was false");
        }
    }

    /**
     * Passes when {@code check} throws {@code type} (or a subclass).
     *
     * @param type  the expected exception type
     * @param check the code that must throw
     */
    public static void throwsType(final Class<? extends Throwable> type, final SelfTest.Check check) {
        try {
            check.run();
        } catch (final Throwable thrown) {
            if (type.isInstance(thrown)) {
                return;
            }
            final AssertionError error = new AssertionError("expected " + type.getSimpleName() + " but "
                    + thrown.getClass().getSimpleName() + " was thrown: " + thrown.getMessage());
            error.initCause(thrown);
            throw error;
        }
        throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
    }
}
