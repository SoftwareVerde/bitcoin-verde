package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.context.MedianBlockTimeContext;
import com.softwareverde.bitcoin.context.NetworkTimeContext;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.transaction.Transaction;

public interface TransactionValidator {
    interface Context extends MedianBlockTimeContext, NetworkTimeContext, UnspentTransactionOutputContext, TransactionInflaters { }

    Long COINBASE_MATURITY = 100L; // Number of Blocks before a coinbase transaction may be spent.
    Integer MAX_SIGNATURE_OPERATIONS = 3000; // Number of Signature operations allowed per Transaction.

    /**
     * Returns true iff the transaction would be valid for the provided blockHeight.
     *  For acceptance into the mempool, blockHeight should be 1 greater than the current blockchain's head blockHeight.
     */
    TransactionValidationResult validateTransaction(Long blockHeight, Transaction transaction);
}
