package com.softwareverde.bitcoin.server.module.node.database.indexer;

import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.Const;
import com.softwareverde.util.Util;

public class TransactionOutputId implements Const, Comparable<TransactionOutputId> {
    protected final TransactionId _transactionId;
    protected final Integer _outputIndex;

    public TransactionOutputId(final TransactionId transactionId, final Integer outputIndex) {
        _transactionId = transactionId;
        _outputIndex = outputIndex;
    }

    public TransactionId getTransactionId() {
        return _transactionId;
    }

    public Integer getOutputIndex() {
        return _outputIndex;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == this) { return true; }

        if (! (object instanceof TransactionOutputId)) { return false; }
        final TransactionOutputId transactionOutputId = (TransactionOutputId) object;

        final TransactionId transactionId = transactionOutputId.getTransactionId();
        if (! Util.areEqual(_transactionId, transactionId)) { return false; }

        final Integer outputIndex = transactionOutputId.getOutputIndex();
        if (! Util.areEqual(_outputIndex, outputIndex)) { return false; }

        return true;
    }

    @Override
    public int hashCode() {
        return (_transactionId.hashCode() + _outputIndex.hashCode());
    }

    @Override
    public String toString() {
        return (_transactionId + ":" + _outputIndex);
    }

    @Override
    public int compareTo(final TransactionOutputId transactionOutputId) {
        final TransactionId transactionId = transactionOutputId.getTransactionId();
        final int transactionIdComparison = _transactionId.compareTo(transactionId);
        if (transactionIdComparison != 0) {
            return transactionIdComparison;
        }

        final Integer outputIndex = transactionOutputId.getOutputIndex();
        return _outputIndex.compareTo(outputIndex);
    }
}
