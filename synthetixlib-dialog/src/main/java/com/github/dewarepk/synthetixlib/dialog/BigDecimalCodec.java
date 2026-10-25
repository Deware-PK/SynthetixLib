package com.github.dewarepk.synthetixlib.dialog;

import com.github.dewarepk.synthetixlib.config.codec.Codec;
import com.github.dewarepk.synthetixlib.config.codec.DecodeContext;
import java.math.BigDecimal;

/**
 * {@code BigDecimal} for the record of a {@code submit}: a {@code number-field} with {@code format: decimal} gives the
 * exact number the player typed. Only numbers are read (never text), so a component of another kind cannot be mistaken
 * for one by the form check.
 */
final class BigDecimalCodec implements Codec<BigDecimal> {

    @Override
    public BigDecimal decode(final Object raw, final DecodeContext ctx) {
        if (raw instanceof BigDecimal big) {
            return big;
        }
        if (raw instanceof Long || raw instanceof Integer || raw instanceof Short || raw instanceof Byte) {
            return BigDecimal.valueOf(((Number) raw).longValue());
        }
        if (raw instanceof Number number && Double.isFinite(number.doubleValue())) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        throw ctx.expected("a number", raw);
    }

    @Override
    public Object encode(final BigDecimal value) {
        return value.toPlainString();
    }
}
