package com.github.dewarepk.synthetixlib.database;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Copies whole tables from one {@link Database} to another, for switching backends (SQLite to MySQL and back) and for
 * importing the data of an older version. The tables must already exist in the target with the same columns, for
 * example created by {@link Database#migrate}; the dialects may differ.
 *
 * <pre>{@code
 * try (Database old = Database.open(lib, legacySettings)) {
 *     Copy.Report report = Copy.tables(old, db, List.of("lots", "mail")).batchSize(500).run().join();
 *     lib.plugin().getLogger().info("Imported " + report.total() + " rows");
 * }
 * }</pre>
 *
 * <p>Each name is a table without its prefix: the {@code table-prefix} of each side is added ({@code "lots"} reads
 * {@code old_lots} and writes {@code ia_lots}); a name that starts with {@code {prefix}} is accepted as the same thing.
 * Tables are copied one after the other, in the order given (list a parent before its children when foreign keys
 * exist). A table is read in pages ordered by its primary key (by {@code rowid} for a SQLite table without one; a MySQL
 * table without a primary key is refused) and every page is written to the target in one transaction. Rows are
 * inserted, never replaced: a row that already exists makes its page fail. A failure stops the copy and fails the
 * future; the pages already written stay, so copy into empty tables and clear them to try again.
 *
 * <p>Waiting for the future follows the rules of {@link Database}: do not {@code join()} it on a server thread.
 */
public final class Copy {

    private static final Pattern NAME = Pattern.compile("(\\{prefix})?([A-Za-z_][A-Za-z0-9_]*)");
    private static final int DEFAULT_BATCH_SIZE = 500;

    private final DatabaseImpl from;
    private final DatabaseImpl to;
    private final Map<String, String> tables;
    private int batchSize = DEFAULT_BATCH_SIZE;

    private Copy(final DatabaseImpl from, final DatabaseImpl to, final Map<String, String> tables) {
        this.from = from;
        this.to = to;
        this.tables = tables;
    }

    /**
     * @param from   where the rows are read
     * @param to     where they are written; not the same database as {@code from}
     * @param tables the tables to copy, in order, without prefix (see the class description)
     * @throws IllegalArgumentException for the same database twice, a name that is not a plain table name, or a table
     *                                  listed twice
     */
    public static Copy tables(final Database from, final Database to, final List<String> tables) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(tables, "tables");
        if (from == to) {
            throw new IllegalArgumentException("from and to are the same database");
        }
        if (!(from instanceof DatabaseImpl source) || !(to instanceof DatabaseImpl target)) {
            throw new IllegalArgumentException("Copy works with databases made by Database.create or Database.open");
        }
        final Map<String, String> names = new LinkedHashMap<>();
        for (final String table : tables) {
            final var matcher = NAME.matcher(Objects.requireNonNull(table, "table"));
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Table \"" + table + "\" is not a plain name (optionally starting with {prefix})");
            }
            if (names.put(table, matcher.group(2)) != null) {
                throw new IllegalArgumentException("Table \"" + table + "\" is listed twice");
            }
        }
        if (new LinkedHashSet<>(names.values()).size() != names.size()) {
            throw new IllegalArgumentException("A table is listed twice (with and without {prefix})");
        }
        return new Copy(source, target, names);
    }

    /**
     * @param rows rows per page: read from the source, then written in one transaction (default 500)
     * @throws IllegalArgumentException if {@code rows} is less than 1
     */
    public Copy batchSize(final int rows) {
        if (rows < 1) {
            throw new IllegalArgumentException("batchSize must be at least 1, was " + rows);
        }
        this.batchSize = rows;
        return this;
    }

    /**
     * Starts the copy.
     *
     * @return the rows copied per table; fails with the {@link DatabaseException} (or {@link DatabaseClosedException})
     *         that stopped it
     */
    public CompletableFuture<Report> run() {
        final Map<String, Long> counts = new LinkedHashMap<>();
        final List<Map.Entry<String, String>> order = List.copyOf(tables.entrySet());
        return copyFrom(order, 0, counts).thenApply(ignored -> new Report(counts));
    }

    private CompletableFuture<Void> copyFrom(final List<Map.Entry<String, String>> order, final int index,
                                             final Map<String, Long> counts) {
        if (index == order.size()) {
            return CompletableFuture.completedFuture(null);
        }
        final Map.Entry<String, String> next = order.get(index);
        return copyTable(from.prefix() + next.getValue(), to.prefix() + next.getValue()).thenCompose(count -> {
            counts.put(next.getKey(), count);
            return copyFrom(order, index + 1, counts);
        });
    }

    private CompletableFuture<Long> copyTable(final String source, final String target) {
        return from.withConnection("SELECT * FROM " + source,
                        connection -> PagedTable.keysOf(connection, from.dialect(), source))
                .thenCompose(keys -> copyPages(source, target, keys, null, 0L));
    }

    /** Reads the page after {@code after}, writes it, and continues with the next until a page comes back short. */
    private CompletableFuture<Long> copyPages(final String source, final String target, final List<String> keys,
                                              final Object @Nullable [] after, final long done) {
        final int size = batchSize;
        return from.withConnection("SELECT * FROM " + source,
                connection -> PagedTable.read(connection, from.dialect(), source, keys, after, size)
        ).thenCompose(page -> {
            if (page.rows().isEmpty()) {
                return CompletableFuture.completedFuture(done);
            }
            return to.withTransaction("INSERT INTO " + target, connection -> {
                PagedTable.write(connection, to.dialect(), target, page);
                return null;
            }).thenCompose(written -> {
                final long total = done + page.rows().size();
                return page.rows().size() < size
                        ? CompletableFuture.completedFuture(total)
                        : copyPages(source, target, keys, page.lastKey(), total);
            });
        });
    }

    /**
     * What a copy did.
     *
     * @param rows rows copied per table, in the order the tables were given and under the names as given
     */
    public record Report(Map<String, Long> rows) {

        public Report {
            rows = Collections.unmodifiableMap(new LinkedHashMap<>(rows));
        }

        /** @return the rows copied from {@code table} (as named in the call), or 0 if it was not part of the copy */
        public long rows(final String table) {
            return rows.getOrDefault(table, 0L);
        }

        /** @return the rows copied in all tables */
        public long total() {
            return rows.values().stream().mapToLong(Long::longValue).sum();
        }
    }
}
