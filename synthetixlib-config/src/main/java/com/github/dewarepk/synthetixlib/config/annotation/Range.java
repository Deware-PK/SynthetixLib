package com.github.dewarepk.synthetixlib.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inclusive numeric bounds, checked when a value is decoded. Valid on {@code int}, {@code long},
 * {@code float}, {@code double} (and boxed / {@code Optional} of them). Bounds are compared as doubles.
 *
 * <pre>{@code
 * public record Zone(@Range(min = 1, max = 256) int radius) {}
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Range {

    /** @return smallest allowed value (default: unbounded) */
    double min() default Double.NEGATIVE_INFINITY;

    /** @return largest allowed value (default: unbounded) */
    double max() default Double.POSITIVE_INFINITY;
}
