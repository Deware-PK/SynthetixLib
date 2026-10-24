package com.github.dewarepk.synthetixlib.dialog;

import java.time.Duration;
import java.util.Objects;

/**
 * Settings a plugin can put in its own config and hand to {@link DialogService#settings}.
 *
 * <pre>{@code
 * record Settings(DialogSettings dialog) {}      // config.yml:  dialog: { callback-lifetime: 5m, debug: false, registry-click-interval: 250ms }
 * dialogs.settings(config.get().dialog());
 * }</pre>
 *
 * @param callbackLifetime how long the buttons of a shown dialog work; a dialog that stays open longer than this does
 *                         nothing when clicked. Between 1 second and 1 day.
 * @param debug            {@code true} to log the first callback thread and the clicks that are ignored at INFO (FINE otherwise)
 * @param registryClickInterval the least time between two clicks of a player on registry dialogs; a click while the
 *                         handler of the previous one still runs (including {@code submitAsync}) is ignored whatever the
 *                         time. Between 0 and 1 minute.
 */
public record DialogSettings(Duration callbackLifetime, boolean debug, Duration registryClickInterval) {

    private static final Duration DEFAULT_CLICK_INTERVAL = Duration.ofMillis(250);

    public DialogSettings {
        Objects.requireNonNull(callbackLifetime, "callbackLifetime");
        Objects.requireNonNull(registryClickInterval, "registryClickInterval");
        if (callbackLifetime.compareTo(Duration.ofSeconds(1)) < 0
                || callbackLifetime.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("callback-lifetime must be between 1s and 1d (was " + callbackLifetime
                    + ")");
        }
        if (registryClickInterval.isNegative() || registryClickInterval.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("registry-click-interval must be between 0 and 1m (was "
                    + registryClickInterval + ")");
        }
    }

    /**
     * @param callbackLifetime how long the buttons of a shown dialog work
     * @param debug            see above; the registry click interval is 250 ms
     */
    public DialogSettings(final Duration callbackLifetime, final boolean debug) {
        this(callbackLifetime, debug, DEFAULT_CLICK_INTERVAL);
    }

    /** @param callbackLifetime how long the buttons of a shown dialog work; debug logging off */
    public DialogSettings(final Duration callbackLifetime) {
        this(callbackLifetime, false, DEFAULT_CLICK_INTERVAL);
    }

    /** @return 5 minutes, debug logging off, 250 ms between registry clicks */
    public static DialogSettings defaults() {
        return new DialogSettings(Duration.ofMinutes(5), false, DEFAULT_CLICK_INTERVAL);
    }
}
