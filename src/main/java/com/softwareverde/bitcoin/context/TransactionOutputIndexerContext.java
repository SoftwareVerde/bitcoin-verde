package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.transaction.TransactionId;

public interface TransactionOutputIndexerContext {
    AtomicTransactionOutputIndexerContext newTransactionOutputIndexerContext() throws ContextException;
    void commitLastProcessedTransactionId(TransactionId transactionId) throws ContextException;
}
