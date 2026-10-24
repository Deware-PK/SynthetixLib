package com.github.dewarepk.synthetixlib.dialog;

/**
 * Input keys on the wire. The file may use {@code -} (kebab-case record components); the vanilla client only accepts
 * {@code [a-zA-Z0-9_]+}, so Paper gets the key with {@code -} turned into {@code _} and the answers are read by that key.
 */
final class InputKeys {

    private InputKeys() {
    }

    static String wire(final String fileKey) {
        return fileKey.replace('-', '_');
    }
}
