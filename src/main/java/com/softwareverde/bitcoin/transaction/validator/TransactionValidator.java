package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.context.MedianBlockTimeContext;
import com.softwareverde.bitcoin.context.NetworkTimeContext;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.transaction.Transaction;

public interface TransactionValidator {
    interface Context extends MedianBlockTimeContext, NetworkTimeContext, UnspentTransactionOutputContext { }

    Long COINBASE_MATURITY = 100L; // Number of Blocks before a coinbase transaction may be spent.

    Boolean validateTransaction(Long blockHeight, Transaction transaction, Boolean validateForMemoryPool);
    void setLoggingEnabled(Boolean shouldLogInvalidTransactions);
}
