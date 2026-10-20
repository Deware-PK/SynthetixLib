package com.github.dewarepk.synthetixlib.database;

import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import com.github.dewarepk.synthetixlib.config.annotation.NotEmpty;
import com.github.dewarepk.synthetixlib.config.annotation.Range;
import com.github.dewarepk.synthetixlib.config.codec.Codec;
import com.github.dewarepk.synthetixlib.config.codec.Codecs;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The database part of a product's config, the same keys and comments in every product. Put it in your own settings
 * record and give it to {@link Database#create}.
 *
 * <pre>{@code
 * record Settings(@Comment("Database for auctions") DatabaseSettings database) {
 *     static Settings defaults() { return new Settings(DatabaseSettings.sqlite("auction.db").withTablePrefix("ia_")); }
 * }
 * // before loading: DatabaseSettings.register(configs.codecs());   (writes "sqlite" instead of "SQLITE")
 * }</pre>
 *
 * @param type            which of {@code sqlite} / {@code mysql} is used; the other section is ignored
 * @param tablePrefix     replaces {@code {prefix}} in every statement; letters, digits and {@code _}, at most 32
 * @param sqlite          the SQLite settings
 * @param mysql           the MySQL settings
 * @param pool            the connection pool
 * @param shutdownTimeout how long {@link Database#close()} waits for queued statements
 */
public record DatabaseSettings(
        @Comment("Which database to use: sqlite | mysql") Dialect type,
        @Comment("Put in front of every table name ({prefix} in the SQL); letters, digits and _") String tablePrefix,
        @Comment("Used when type is sqlite") Sqlite sqlite,
        @Comment("Used when type is mysql") Mysql mysql,
        @Comment("Connection pool") Pool pool,
        @Comment("How long the server waits for queued writes when it stops") Duration shutdownTimeout) {

    /**
     * @param file database file, relative to the plugin folder
     */
    public record Sqlite(@Comment("Relative to the plugin folder") @NotEmpty String file) {
    }

    /**
     * @param host       server name or address
     * @param port       TCP port
     * @param database   schema name; it must exist
     * @param username   account
     * @param password   the account's password; hidden by {@link #toString()}
     * @param properties extra JDBC properties, empty by default (the driver's {@code sslMode=PREFERRED} connects to a
     *                   fresh MySQL 8 / MariaDB); they win over the library's own
     */
    public record Mysql(
            @NotEmpty String host,
            @Range(min = 1, max = 65535) int port,
            @NotEmpty String database,
            String username,
            String password,
            @Comment({"Extra JDBC URL parameters. Empty works with a normal MySQL 8 / MariaDB (TLS is used when offered).",
                    "A server without TLS needs: { useSSL: 'false', allowPublicKeyRetrieval: 'true' }"})
            Map<String, String> properties) {

        @Override
        public String toString() {
            return "Mysql[host=" + host + ", port=" + port + ", database=" + database + ", username=" + username
                    + ", password=***, properties=" + properties.keySet() + "]";
        }
    }

    /**
     * @param size              connections and worker threads; always 1 for SQLite
     * @param connectionTimeout how long to wait for a free connection
     * @param maxLifetime       when a connection is replaced
     */
    public record Pool(
            @Comment("Ignored for sqlite (always 1)") @Range(min = 1, max = 100) int size,
            Duration connectionTimeout,
            Duration maxLifetime) {
    }

    /**
     * SQLite in {@code file} (relative to the plugin folder), no table prefix, the other sections at their defaults.
     *
     * @param file e.g. {@code auction.db}
     */
    public static DatabaseSettings sqlite(final String file) {
        return new DatabaseSettings(Dialect.SQLITE, "", new Sqlite(Objects.requireNonNull(file, "file")),
                defaultMysql("localhost", 3306, "minecraft", "root", ""), defaultPool(), Duration.ofSeconds(10));
    }

    /**
     * MySQL on the given server, no table prefix, the other sections at their defaults.
     *
     * @param password the account's password; {@code ""} for none
     */
    public static DatabaseSettings mysql(final String host, final int port, final String database,
                                         final String username, final String password) {
        return new DatabaseSettings(Dialect.MYSQL, "", new Sqlite("database.db"),
                defaultMysql(Objects.requireNonNull(host, "host"), port, Objects.requireNonNull(database, "database"),
                        Objects.requireNonNull(username, "username"), Objects.requireNonNull(password, "password")),
                defaultPool(), Duration.ofSeconds(10));
    }

    /** @return a copy with another {@code {prefix}} */
    public DatabaseSettings withTablePrefix(final String prefix) {
        return new DatabaseSettings(type, Objects.requireNonNull(prefix, "prefix"), sqlite, mysql, pool,
                shutdownTimeout);
    }

    /**
     * Makes the config write the type as {@code sqlite} / {@code mysql} (it reads either case). Call before loading
     * the file that contains these settings; without it the value is written as {@code SQLITE} / {@code MYSQL}.
     *
     * @param codecs {@code configs.codecs()}
     */
    public static void register(final Codecs codecs) {
        Objects.requireNonNull(codecs, "codecs").register(Dialect.class,
                Codec.string(text -> Dialect.valueOf(text.strip().toUpperCase(Locale.ROOT)),
                        dialect -> dialect.name().toLowerCase(Locale.ROOT)));
    }

    private static Mysql defaultMysql(final String host, final int port, final String database,
                                      final String username, final String password) {
        return new Mysql(host, port, database, username, password, Map.of());
    }

    private static Pool defaultPool() {
        return new Pool(10, Duration.ofSeconds(10), Duration.ofMinutes(30));
    }
}
