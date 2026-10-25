package com.github.dewarepk.synthetixlib.config;

import com.github.dewarepk.synthetixlib.config.annotation.Comment;

/**
 * The unit words {@link TimeFormat} writes after the numbers. Put it in your lang record so translators can change it.
 *
 * <pre>{@code
 * public record Messages(@Comment("Words for days, hours, minutes, seconds") TimeUnits units) {}
 * }</pre>
 *
 * @param d the word for days, e.g. {@code d}
 * @param h the word for hours
 * @param m the word for minutes
 * @param s the word for seconds
 */
public record TimeUnits(
        @Comment("Day") String d,
        @Comment("Hour") String h,
        @Comment("Minute") String m,
        @Comment("Second") String s) {

    /** @return the English words {@code d h m s} */
    public static TimeUnits defaults() {
        return new TimeUnits("d", "h", "m", "s");
    }
}
