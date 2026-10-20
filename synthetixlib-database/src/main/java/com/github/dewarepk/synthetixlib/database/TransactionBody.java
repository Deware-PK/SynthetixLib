package com.github.dewarepk.synthetixlib.database;

/**
 * The statements of one transaction. Returning normally commits, any exception rolls back.
 *
 * @param <T> the result of the transaction
 */
@FunctionalInterface
public interface TransactionBody<T> {

    /**
     * @param tx the transaction; valid only during this call
     * @return the result of the transaction's future
     * @throws Exception any exception rolls the transaction back and completes the future with it
     */
    T run(Transaction tx) throws Exception;
}
