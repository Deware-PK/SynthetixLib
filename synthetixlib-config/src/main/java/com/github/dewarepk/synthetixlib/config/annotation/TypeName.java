package com.github.dewarepk.synthetixlib.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Discriminator value of a record that implements a sealed interface. Without it the value is the kebab-case
 * simple name with the interface's simple name removed from the end ({@code ItemReward} implementing
 * {@code Reward} becomes {@code item}).
 *
 * <pre>{@code
 * @TypeName("cmd")
 * public record CommandReward(String command) implements Reward {}
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TypeName {

    /** @return the exact value of the {@code type} key; must not be blank */
    String value();
}
