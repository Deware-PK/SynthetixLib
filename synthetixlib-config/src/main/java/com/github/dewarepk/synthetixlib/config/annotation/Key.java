package com.github.dewarepk.synthetixlib.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the data key of a record component. Without it the key is the component name in kebab-case
 * ({@code minBid} becomes {@code min-bid}).
 *
 * <pre>{@code
 * public record Config(@Key("anti-snipe") AntiSnipe antiSnipe) {}
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Key {

    /** @return the exact key to use; must not be blank */
    String value();
}
