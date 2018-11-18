package com.softwareverde.bitcoin.server.module.node.sync.transaction.pending;

import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.bytearray.ByteArray;

public class PendingTransaction {
    protected final Sha256Hash _transactionHash;
    protected final ByteArray _data;

    public PendingTransaction(final Sha256Hash transactionHash) {
        _transactionHash = transactionHash;
        _data = null;
    }

    public PendingTransaction(final Sha256Hash transactionHash, final ByteArray transactionData) {
        _transactionHash = transactionHash;
        _data = transactionData;
    }

    public Sha256Hash getTransactionHash() { return _transactionHash; }

    public ByteArray getData() { return _data; }
}
