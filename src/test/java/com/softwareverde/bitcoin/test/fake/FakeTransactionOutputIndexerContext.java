package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.context.AtomicTransactionOutputIndexerContext;
import com.softwareverde.bitcoin.context.TransactionOutputIndexerContext;
import com.softwareverde.bitcoin.transaction.TransactionId;

public class FakeTransactionOutputIndexerContext implements TransactionOutputIndexerContext {
    protected final FakeAtomicTransactionOutputIndexerContext _context;

    public FakeTransactionOutputIndexerContext() {
        _context = new FakeAtomicTransactionOutputIndexerContext();
    }

    @Override
    public AtomicTransactionOutputIndexerContext newTransactionOutputIndexerContext() {
        return _context;
    }

    @Override
    public void commitLastProcessedTransactionId(final TransactionId transactionId) { }

    public FakeAtomicTransactionOutputIndexerContext getContext() {
        return _context;
    }
}
