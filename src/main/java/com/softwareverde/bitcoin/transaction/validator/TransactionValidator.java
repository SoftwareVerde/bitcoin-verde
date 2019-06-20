package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.transaction.Transaction;

public interface TransactionValidator {
    Long COINBASE_MATURITY = 100L; // Number of Blocks before a coinbase transaction may be spent.

    Boolean validateTransaction(BlockchainSegmentId blockchainSegmentId, Long blockHeight, Transaction transaction, Boolean validateForMemoryPool);
    void setLoggingEnabled(Boolean shouldLogInvalidTransactions);
}
