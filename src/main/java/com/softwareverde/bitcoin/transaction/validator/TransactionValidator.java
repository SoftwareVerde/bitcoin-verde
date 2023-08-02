package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;

public interface TransactionValidator {
    Long COINBASE_MATURITY = 100L; // Number of Blocks before a coinbase transaction may be spent, inclusive.  The 100th block may spend the coinbase.

    SequenceNumber FINAL_SEQUENCE_NUMBER = SequenceNumber.MAX_SEQUENCE_NUMBER; // If all inputs are "FINAL" then ignore lock time

    /**
     * Returns true iff the transaction would be valid for the provided blockHeight.
     *  For acceptance into the mempool, blockHeight should be 1 greater than the current blockchain's head blockHeight.
     */
    TransactionValidationResult validateTransaction(Long blockHeight, Transaction transaction);
}
