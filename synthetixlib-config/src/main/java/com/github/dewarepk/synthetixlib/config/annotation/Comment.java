package com.github.dewarepk.synthetixlib.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Comment written above a key (record component) or at the top of a file (record type) when a config is saved.
 * One array element per line.
 *
 * <pre>{@code
 * public record Config(@Comment("Minimum starting bid") int minBid) {}
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.TYPE})
public @interface Comment {

    /** @return the comment lines, without a leading {@code #} */
    String[] value();
}
