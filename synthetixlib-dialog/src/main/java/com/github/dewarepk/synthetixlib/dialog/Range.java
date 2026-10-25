package com.github.dewarepk.synthetixlib.dialog;

import java.util.OptionalDouble;

/**
 * The limits of a number input for one showing, from code: see {@link DialogBuilder#inputRange}. Replaces {@code min},
 * {@code max} (and {@code step}) of the file while that showing is open.
 *
 * <pre>{@code
 * .inputRange("price", view -> Range.of(settings.minPrice(), settings.maxPrice()))
 * }</pre>
 *
 * @param min  smallest value
 * @param max  largest value, not less than {@code min}
 * @param step distance between the values of a slider ({@code number}); a {@code number-field} ignores it
 */
public record Range(double min, double max, OptionalDouble step) {

    public Range {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) {
            throw new IllegalArgumentException("a range needs finite numbers with min <= max (was " + min + " and "
                    + max + ")");
        }
        if (step.isPresent() && !(step.getAsDouble() > 0 && Double.isFinite(step.getAsDouble()))) {
            throw new IllegalArgumentException("the step of a range must be greater than 0 (was "
                    + step.getAsDouble() + ")");
        }
    }

    /**
     * @param min smallest value
     * @param max largest value
     * @return a range without a step
     */
    public static Range of(final double min, final double max) {
        return new Range(min, max, OptionalDouble.empty());
    }

    /**
     * @param min  smallest value
     * @param max  largest value
     * @param step distance between the values of a slider, greater than 0
     * @return a range with a step
     */
    public static Range of(final double min, final double max, final double step) {
        return new Range(min, max, OptionalDouble.of(step));
    }
}
