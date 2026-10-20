package com.github.dewarepk.synthetixlib.archtest.fixture;

import com.zaxxer.hikari.HikariConfig;

/** Deliberate violation: the HikariCP rule must reject this class (it is outside the database module). */
public final class UsesHikari {

    static final HikariConfig CONFIG = null;

    private UsesHikari() {
    }
}
