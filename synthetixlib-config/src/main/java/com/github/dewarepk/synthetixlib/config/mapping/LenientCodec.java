package com.github.dewarepk.synthetixlib.config.mapping;

import com.github.dewarepk.synthetixlib.config.codec.DecodeContext;

/**
 * Decode mode for config files: a codec that can fall back to a default per field instead of failing as a whole.
 * Implemented by the codecs of records and of {@code Map<String, T>}.
 */
interface LenientCodec {

    /**
     * Decodes {@code raw}, using the matching part of {@code defaults} for every missing or invalid piece and
     * recording it in {@code report}. Never throws {@code DecodeException}.
     *
     * @param defaults a non-null value of the decoded type
     * @return the decoded value, never {@code null}
     */
    Object decodeLenient(Object raw, Object defaults, DecodeContext ctx, LenientReport report);
}
