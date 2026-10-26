package com.github.dewarepk.synthetixexample.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.dewarepk.synthetixlib.database.Database;
import com.github.dewarepk.synthetixlib.database.DatabaseSettings;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Recipe 4: a store tested in plain JUnit, no server. */
class StoreTestExample {

    // @snippet:recipe-store-test
    @TempDir
    Path dir;

    @Test
    void claimsAreCounted() {
        try (Database db = Database.standalone("store-test", dir, Logger.getLogger("test"),
                DatabaseSettings.sqlite("test.db").withTablePrefix("t_"))) {
            final ClaimStore store = ClaimStore.open(db);   // runs the migrations from the resources

            store.claim("daily", UUID.randomUUID()).join();
            store.claim("daily", UUID.randomUUID()).join();

            assertEquals(2, store.stats("daily", 10).join().count());
        }
    }
    // @end
}
