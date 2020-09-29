package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.server.module.node.database.indexer.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.TransactionId;

public class IndexedInput {
    public final TransactionId transactionId;
    public final Integer inputIndex;
    public final TransactionOutputId transactionOutputId;

    public IndexedInput(final TransactionId transactionId, final Integer inputIndex, final TransactionOutputId transactionOutputId) {
        this.transactionId = transactionId;
        this.inputIndex = inputIndex;
        this.transactionOutputId = transactionOutputId;
    }
}
