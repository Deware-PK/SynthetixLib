package com.github.dewarepk.synthetixlib.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value must not be empty, checked when a value is decoded. Valid on {@code String}, {@code List},
 * {@code Set} and {@code Map} components (and {@code Optional} of them: only a present value is checked).
 *
 * <pre>{@code
 * public record Config(@NotEmpty List<String> worlds) {}
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface NotEmpty {
}
