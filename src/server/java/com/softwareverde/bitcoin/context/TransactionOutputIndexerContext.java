package com.softwareverde.bitcoin.context;

public interface TransactionOutputIndexerContext {
    AtomicTransactionOutputIndexerContext newTransactionOutputIndexerContext() throws ContextException;
}
