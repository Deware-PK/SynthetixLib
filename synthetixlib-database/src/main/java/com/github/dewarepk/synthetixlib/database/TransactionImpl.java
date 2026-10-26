package com.github.dewarepk.synthetixlib.database;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** The {@link Transaction} given to a {@link TransactionBody}: valid until the body returns, on its own thread only. */
final class TransactionImpl implements Transaction {

    private final Statements statements;
    private final Dialect dialect;
    private final String prefix;
    private final Thread owner = Thread.currentThread();
    private volatile boolean finished;

    TransactionImpl(final Statements statements, final Dialect dialect, final String prefix) {
        this.statements = statements;
        this.dialect = dialect;
        this.prefix = prefix;
    }

    /** The body has returned or thrown: later use of this object is a bug in the caller. */
    void finish() {
        finished = true;
    }

    private Statements active() {
        if (finished) {
            throw new IllegalStateException("This transaction is over; use it only inside the transaction body");
        }
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("A transaction must be used on the thread that runs its body");
        }
        return statements;
    }

    @Override
    public <T> List<T> query(final String sql, final RowMapper<T> mapper, final Object... params) {
        Objects.requireNonNull(mapper, "mapper");
        return active().query(DatabaseImpl.resolve(prefix, sql), mapper, Params.convert(params));
    }

    @Override
    public <T> Optional<T> queryOne(final String sql, final RowMapper<T> mapper, final Object... params) {
        Objects.requireNonNull(mapper, "mapper");
        return active().queryOne(DatabaseImpl.resolve(prefix, sql), mapper, Params.convert(params));
    }

    @Override
    public int update(final String sql, final Object... params) {
        return active().update(DatabaseImpl.resolve(prefix, sql), Params.convert(params));
    }

    @Override
    public long insert(final String sql, final Object... params) {
        return active().insert(DatabaseImpl.resolve(prefix, sql), Params.convert(params));
    }

    @Override
    public int[] batch(final String sql, final List<Object[]> rows) {
        return active().batch(DatabaseImpl.resolve(prefix, sql), Params.convertRows(rows));
    }

    @Override
    public int upsert(final String table, final List<String> keyColumns, final Map<String, Object> values) {
        final Upsert upsert = Upsert.of(dialect, table, keyColumns, values);
        return active().update(DatabaseImpl.resolve(prefix, upsert.sql()), upsert.values());
    }
}
