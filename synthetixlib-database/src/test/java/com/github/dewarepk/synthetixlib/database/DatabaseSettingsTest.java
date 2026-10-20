package com.github.dewarepk.synthetixlib.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import com.github.dewarepk.synthetixlib.config.yaml.FileConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The settings as a product embeds them in its own config. */
class DatabaseSettingsTest {

    /** A product's settings record; public because the config module maps records by reflection. */
    public record ProductSettings(@Comment("Database for auctions") DatabaseSettings database, int other) {

        public static ProductSettings defaults() {
            return new ProductSettings(DatabaseSettings.sqlite("auction.db").withTablePrefix("ia_"), 5);
        }
    }

    @TempDir
    Path dir;

    private FileConfig<ProductSettings> config(final Codecs codecs) {
        return new FileConfig<>(dir.resolve("config.yml"), ProductSettings.class, ProductSettings::defaults, codecs, 1,
                List.of());
    }

    private String text() throws IOException {
        return Files.readString(dir.resolve("config.yml"), StandardCharsets.UTF_8);
    }

    @Test
    void sqliteDefaultsMatchTheDesign() {
        final DatabaseSettings settings = DatabaseSettings.sqlite("auction.db");

        assertEquals(Dialect.SQLITE, settings.type());
        assertEquals("", settings.tablePrefix());
        assertEquals("auction.db", settings.sqlite().file());
        assertEquals(new DatabaseSettings.Mysql("localhost", 3306, "minecraft", "root", "", Map.of()),
                settings.mysql());
        assertEquals(new DatabaseSettings.Pool(10, Duration.ofSeconds(10), Duration.ofMinutes(30)), settings.pool());
        assertEquals(Duration.ofSeconds(10), settings.shutdownTimeout());
    }

    @Test
    void mysqlFactoryKeepsTheOtherDefaults() {
        final DatabaseSettings settings = DatabaseSettings.mysql("db", 3307, "shop", "u", "p");

        assertEquals(Dialect.MYSQL, settings.type());
        assertEquals(new DatabaseSettings.Mysql("db", 3307, "shop", "u", "p", Map.of()),
                settings.mysql());
        assertEquals(DatabaseSettings.sqlite("database.db").pool(), settings.pool());
    }

    @Test
    void theYamlHasTheKeysAndCommentsOfTheDesign() throws IOException {
        final Codecs codecs = Codecs.create();
        DatabaseSettings.register(codecs);

        final FileConfig.Result<ProductSettings> result = config(codecs).load();

        assertEquals(ProductSettings.defaults(), result.value());
        assertEquals(List.of(), result.errors());
        assertEquals(List.of(), result.warnings());
        final String text = text();
        for (final String expected : new String[] {
                "# Database for auctions", "database:", "  # Which database to use: sqlite | mysql", "  type: sqlite",
                "  table-prefix: ia_", "  sqlite:", "    file: auction.db", "  mysql:", "    host: localhost",
                "    port: 3306", "    database: minecraft", "    username: root", "    password: ''",
                "    properties: {}", "  pool:", "    size: 10", "    connection-timeout: 10s",
                "# Extra JDBC URL parameters. Empty works with a normal MySQL 8 / MariaDB (TLS is used when offered).",
                "# A server without TLS needs: { useSSL: 'false', allowPublicKeyRetrieval: 'true' }",
                "    max-lifetime: 30m", "  shutdown-timeout: 10s"}) {
            assertTrue(text.contains(expected), "missing \"" + expected + "\" in\n" + text);
        }
    }

    @Test
    void editedValuesAreReadBackWithoutRewritingTheFile() throws IOException {
        final Codecs codecs = Codecs.create();
        DatabaseSettings.register(codecs);
        config(codecs).load();
        final String edited = text().replace("type: sqlite", "type: mysql").replace("port: 3306", "port: 3307")
                .replace("max-lifetime: 30m", "max-lifetime: 20m");
        Files.writeString(dir.resolve("config.yml"), edited, StandardCharsets.UTF_8);

        final FileConfig.Result<ProductSettings> result = config(codecs).load();

        assertEquals(List.of(), result.errors());
        final DatabaseSettings database = result.value().database();
        assertEquals(Dialect.MYSQL, database.type());
        assertEquals(3307, database.mysql().port());
        assertEquals(Duration.ofMinutes(20), database.pool().maxLifetime());
        assertEquals("ia_", database.tablePrefix());
        assertEquals(edited, text());
    }

    @Test
    void withoutRegisterTheTypeIsWrittenUpperCaseButEitherCaseIsRead() throws IOException {
        final Codecs codecs = Codecs.create();
        config(codecs).load();
        assertTrue(text().contains("type: SQLITE"), text());

        for (final String spelling : new String[] {"mysql", "MySQL", "MYSQL"}) {
            Files.writeString(dir.resolve("config.yml"), text().replaceAll("type: \\w+", "type: " + spelling),
                    StandardCharsets.UTF_8);
            assertEquals(Dialect.MYSQL, config(codecs).load().value().database().type(), spelling);
        }
    }

    @Test
    void outOfRangeValuesAreReportedAndReplacedByTheDefault() throws IOException {
        final Codecs codecs = Codecs.create();
        DatabaseSettings.register(codecs);
        config(codecs).load();
        Files.writeString(dir.resolve("config.yml"),
                text().replace("port: 3306", "port: 70000").replace("size: 10", "size: 0"), StandardCharsets.UTF_8);

        final FileConfig.Result<ProductSettings> result = config(codecs).load();

        assertEquals(2, result.errors().size(), result.errors().toString());
        assertEquals(3306, result.value().database().mysql().port());
        assertEquals(10, result.value().database().pool().size());
    }

    @Test
    void theCodecRoundTripsTheWholeRecord() {
        final Codecs codecs = Codecs.create();
        DatabaseSettings.register(codecs);
        final DatabaseSettings settings = DatabaseSettings.mysql("db", 3307, "shop", "u", "p").withTablePrefix("x_");

        assertEquals(settings, codecs.decode(codecs.encode(settings), DatabaseSettings.class));
    }
}
