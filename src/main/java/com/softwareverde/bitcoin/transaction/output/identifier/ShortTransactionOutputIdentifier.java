package com.softwareverde.bitcoin.transaction.output.identifier;

import com.softwareverde.constable.Const;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;

import java.util.Comparator;

public class ShortTransactionOutputIdentifier implements Const, Comparable<ShortTransactionOutputIdentifier> {
    public static final Comparator<ShortTransactionOutputIdentifier> COMPARATOR = new Comparator<>() {
        @Override
        public int compare(final ShortTransactionOutputIdentifier transactionOutputIdentifier0, final ShortTransactionOutputIdentifier transactionOutputIdentifier1) {
            return transactionOutputIdentifier0.compareTo(transactionOutputIdentifier1);
        }
    };

    public static final ShortTransactionOutputIdentifier COINBASE = new ShortTransactionOutputIdentifier(0L, -1);

    protected final Long _transactionId;
    protected final Integer _outputIndex;

    public ShortTransactionOutputIdentifier(final Long transactionId, final Integer outputIndex) {
        _transactionId = transactionId;
        _outputIndex = outputIndex;
    }

    public Long getTransactionId() {
        return _transactionId;
    }

    public Integer getOutputIndex() {
        return _outputIndex;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == this) { return true; }

        if (! (object instanceof ShortTransactionOutputIdentifier)) { return false; }
        final ShortTransactionOutputIdentifier transactionOutputIdentifier = (ShortTransactionOutputIdentifier) object;

        if (! Util.areEqual(_transactionId, transactionOutputIdentifier._transactionId)) { return false; }
        if (! Util.areEqual(_outputIndex, transactionOutputIdentifier._outputIndex)) { return false; }

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
    public int compareTo(final ShortTransactionOutputIdentifier transactionOutputIdentifier) {
        final int transactionIdComparison = +_transactionId.compareTo(transactionOutputIdentifier.getTransactionId());
        if (transactionIdComparison != 0) {
            return transactionIdComparison;
        }

        return _outputIndex.compareTo(transactionOutputIdentifier.getOutputIndex());
    }
}
