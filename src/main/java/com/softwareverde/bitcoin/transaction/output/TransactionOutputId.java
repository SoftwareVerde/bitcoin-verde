package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.identifier.Identifier;

public class TransactionOutputId extends Identifier {
    public static TransactionOutputId wrap(final Long transactionId, final Integer outputIndex) {
        if (transactionId == null) { return new TransactionOutputId(0L, -1); }
        return new TransactionOutputId(transactionId, outputIndex);
    }

    public static TransactionOutputId wrap(final TransactionId transactionId, final Integer outputIndex) {
        if (transactionId == null) { return new TransactionOutputId(0L, -1); }
        return new TransactionOutputId(transactionId.longValue(), outputIndex);
    }

    protected final Integer _outputIndex;

    protected TransactionOutputId(final Long transactionId, final Integer outputIndex) {
        super(transactionId);
        _outputIndex = outputIndex;
    }

    public TransactionId getTransactionId() {
        if (_value == 0L) { return null; }
        return TransactionId.wrap(_value);
    }

    public Integer getOutputIndex() {
        return _outputIndex;
    }

    @Override
    public int hashCode() {
        if (_value == null) { return _outputIndex.hashCode(); }
        return (_value.hashCode() + _outputIndex.hashCode());
    }

    @Override
    public boolean equals(final Object object) {
        if (! super.equals(object)) { return false; }

        if (! (object instanceof TransactionOutputId)) { return false; }

        final TransactionOutputId transactionOutputId = (TransactionOutputId) object;
        return Util.areEqual(_outputIndex, transactionOutputId._outputIndex);
    }

    @Override
    public String toString() {
        return (_value + "," + _outputIndex);
    }

    @Override
    public int compareTo(final Identifier value) {
        final int superCompare = super.compareTo(value);
        if (superCompare != 0) { return superCompare; }

        if (! (value instanceof TransactionOutputId)) {
            return superCompare;
        }

        final TransactionOutputId transactionOutputId = (TransactionOutputId) value;
        return _outputIndex.compareTo(transactionOutputId._outputIndex);
    }
}
