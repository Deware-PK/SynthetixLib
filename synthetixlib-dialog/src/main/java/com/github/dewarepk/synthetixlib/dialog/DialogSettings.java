package com.github.dewarepk.synthetixlib.dialog;

import java.time.Duration;
import java.util.Objects;

/**
 * Settings a plugin can put in its own config and hand to {@link DialogService#settings}.
 *
 * <pre>{@code
 * record Settings(DialogSettings dialog) {}      // config.yml:  dialog: { callback-lifetime: 5m }
 * dialogs.settings(config.get().dialog());
 * }</pre>
 *
 * @param callbackLifetime how long the buttons of a shown dialog work; a dialog that stays open longer than this does
 *                         nothing when clicked. Between 1 second and 1 day.
 */
public record DialogSettings(Duration callbackLifetime) {

    public DialogSettings {
        Objects.requireNonNull(callbackLifetime, "callbackLifetime");
        if (callbackLifetime.compareTo(Duration.ofSeconds(1)) < 0
                || callbackLifetime.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("callback-lifetime must be between 1s and 1d (was " + callbackLifetime
                    + ")");
        }
    }

    /** @return 5 minutes */
    public static DialogSettings defaults() {
        return new DialogSettings(Duration.ofMinutes(5));
    }
}
