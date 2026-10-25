package com.example.myplugin;

import com.github.dewarepk.synthetixlib.config.annotation.Comment;

/** The plugin-wide settings, {@code config.yml}: a typed record, never string paths. */
@Comment("MyPlugin settings")
public record Settings(@Comment("Greet a player who joins") boolean welcome,
                       @Comment("Ticks to wait after the join before the greeting (20 ticks = 1 second)") int welcomeDelayTicks) {

    public static Settings defaults() {
        return new Settings(true, 20);
    }
}
