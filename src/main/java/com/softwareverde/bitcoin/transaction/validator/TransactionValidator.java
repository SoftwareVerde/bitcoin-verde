package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.context.MedianBlockTimeContext;
import com.softwareverde.bitcoin.context.NetworkTimeContext;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.transaction.Transaction;

public interface TransactionValidator {
    interface Context extends MedianBlockTimeContext, NetworkTimeContext, UnspentTransactionOutputContext { }

    Long COINBASE_MATURITY = 100L; // Number of Blocks before a coinbase transaction may be spent.

    /**
     * Returns true iff the transaction would be valid for the provided blockHeight.
     *  For acceptance into the mempool, blockHeight should be 1 greater than the current blockchain's head blockHeight.
     */
    Boolean validateTransaction(Long blockHeight, Transaction transaction);
    void setLoggingEnabled(Boolean shouldLogInvalidTransactions);
}
