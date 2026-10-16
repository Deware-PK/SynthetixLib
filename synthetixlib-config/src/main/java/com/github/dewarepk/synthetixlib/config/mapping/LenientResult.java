package com.github.dewarepk.synthetixlib.config.mapping;

import com.github.dewarepk.synthetixlib.config.codec.Problem;
import java.util.List;

/**
 * Outcome of decoding a config file's data with per-field fallback to defaults. Internal: used by the YAML layer.
 *
 * @param value    the decoded value; fields that were missing or invalid hold the default
 * @param errors   invalid values, as {@code path: message -> using <default>}
 * @param warnings things that did not stop decoding, e.g. unknown keys
 * @param missing  keys absent from the data that have a default worth writing to the file
 * @param <T>      the decoded type
 */
@org.jetbrains.annotations.ApiStatus.Internal
public record LenientResult<T>(T value, List<Problem> errors, List<Problem> warnings, List<Missing> missing) {

    /**
     * @param path  data keys from the root to the missing key, e.g. {@code [anti-snipe, extend-seconds]}
     * @param value the encoded default for that key (data tree node)
     */
    public record Missing(List<String> path, Object value) {
    }
}
